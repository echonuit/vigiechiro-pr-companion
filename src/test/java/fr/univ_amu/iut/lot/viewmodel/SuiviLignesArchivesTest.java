package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.ArchivePlanifiee;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de [SuiviLignesArchives] (#820) : cycle de vie des lignes de la table de dépôt (en attente → en
/// cours → terminée / échec), ciblage par numéro, événements dans le désordre, numéro inconnu ignoré.
/// Purement observable (propriétés JavaFX) : aucun toolkit graphique requis.
class SuiviLignesArchivesTest {

    @Test
    @DisplayName("planifier() pré-remplit une ligne « en attente » par archive, dans l'ordre des numéros")
    void planifier_pre_remplit_les_lignes_en_attente() {
        SuiviLignesArchives suivi = new SuiviLignesArchives();
        suivi.planifier(
                List.of(new ArchivePlanifiee(1, 210, 600_000_000L), new ArchivePlanifiee(2, 208, 590_000_000L)));

        assertThat(suivi.lignes()).extracting(LigneArchive::numero).containsExactly(1, 2);
        assertThat(suivi.lignes()).extracting(LigneArchive::nombreFichiers).containsExactly(210, 208);
        assertThat(suivi.lignes()).allSatisfy(l -> {
            assertThat(l.etatProperty().get()).isEqualTo(EtatArchive.EN_ATTENTE);
            assertThat(l.fractionProperty().get()).isEqualTo(0.0);
            assertThat(l.tailleEstimee()).isTrue();
        });
        assertThat(suivi.lignes().get(0).tailleOctetsProperty().get()).isEqualTo(600_000_000L);
    }

    @Test
    @DisplayName("le cycle démarrer → progresser → terminer fait passer la ligne en cours puis terminée")
    void cycle_de_vie_d_une_ligne() {
        SuiviLignesArchives suivi = new SuiviLignesArchives();
        suivi.planifier(List.of(new ArchivePlanifiee(1, 4, 500_000L)));

        suivi.demarrer(1);
        assertThat(ligne(suivi, 1).etatProperty().get()).isEqualTo(EtatArchive.EN_COURS);

        suivi.progresser(1, 2, 4);
        assertThat(ligne(suivi, 1).fractionProperty().get()).isEqualTo(0.5);

        suivi.terminer(new ArchiveDepot(Path.of("Pass-1.zip"), 1, 480_000L, 4));
        LigneArchive l = ligne(suivi, 1);
        assertThat(l.etatProperty().get()).isEqualTo(EtatArchive.TERMINEE);
        assertThat(l.fractionProperty().get()).isEqualTo(1.0);
        assertThat(l.tailleOctetsProperty().get()).isEqualTo(480_000L); // estimation remplacée par le réel
        assertThat(l.tailleEstimee()).isFalse();
    }

    @Test
    @DisplayName("les événements ciblent la bonne ligne par numéro, même arrivés dans le désordre")
    void evenements_desordonnes_ciblent_par_numero() {
        SuiviLignesArchives suivi = new SuiviLignesArchives();
        suivi.planifier(List.of(
                new ArchivePlanifiee(1, 3, 300_000L),
                new ArchivePlanifiee(2, 3, 300_000L),
                new ArchivePlanifiee(3, 2, 200_000L)));

        // Archive 3 démarre et finit avant l'archive 1 (compression parallèle).
        suivi.demarrer(3);
        suivi.terminer(new ArchiveDepot(Path.of("Pass-3.zip"), 3, 190_000L, 2));
        suivi.demarrer(1);
        suivi.progresser(1, 1, 3);

        assertThat(ligne(suivi, 3).etatProperty().get()).isEqualTo(EtatArchive.TERMINEE);
        assertThat(ligne(suivi, 1).etatProperty().get()).isEqualTo(EtatArchive.EN_COURS);
        assertThat(ligne(suivi, 2).etatProperty().get()).isEqualTo(EtatArchive.EN_ATTENTE); // pas encore touchée
    }

    @Test
    @DisplayName("echouer() passe la ligne « échec » et retient la raison ; un numéro inconnu est ignoré")
    void echec_et_numero_inconnu() {
        SuiviLignesArchives suivi = new SuiviLignesArchives();
        suivi.planifier(List.of(new ArchivePlanifiee(1, 3, 300_000L)));

        suivi.echouer(1, "disque plein");
        assertThat(ligne(suivi, 1).etatProperty().get()).isEqualTo(EtatArchive.ECHEC);
        assertThat(ligne(suivi, 1).raisonEchecProperty().get()).isEqualTo("disque plein");

        // Numéro inexistant : aucun effet, aucune exception.
        suivi.demarrer(99);
        suivi.progresser(99, 1, 2);
        suivi.echouer(99, "x");
        assertThat(suivi.lignes()).hasSize(1);
    }

    @Test
    @DisplayName("reinitialiser() vide la table")
    void reinitialiser_vide_la_table() {
        SuiviLignesArchives suivi = new SuiviLignesArchives();
        suivi.planifier(List.of(new ArchivePlanifiee(1, 3, 300_000L)));
        suivi.reinitialiser();
        assertThat(suivi.lignes()).isEmpty();
    }

    private static LigneArchive ligne(SuiviLignesArchives suivi, int numero) {
        return suivi.lignes().stream()
                .filter(l -> l.numero() == numero)
                .findFirst()
                .orElseThrow();
    }
}

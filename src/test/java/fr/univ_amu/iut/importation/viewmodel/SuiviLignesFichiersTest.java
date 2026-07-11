package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.EtatUnite;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de [SuiviLignesFichiers] (#947) : cycle de vie des lignes de la table de suivi de l'import
/// (en attente → copie → transformation → terminé / rejeté), fraction par étapes (0 → 0,5 → 1),
/// replanification multi-nuits. Purement observable (propriétés JavaFX) : aucun toolkit graphique
/// requis.
class SuiviLignesFichiersTest {

    @Test
    @DisplayName("planifier() pré-remplit une ligne « en attente » par fichier, dans l'ordre du plan")
    void planifier_pre_remplit_les_lignes() {
        SuiviLignesFichiers suivi = new SuiviLignesFichiers();
        suivi.planifier(List.of("PaRec_203922.wav", "PaRec_204326.wav"));

        assertThat(suivi.lignes()).extracting(LigneFichierImport::numero).containsExactly(1, 2);
        assertThat(suivi.lignes())
                .extracting(LigneFichierImport::nomFichier)
                .containsExactly("PaRec_203922.wav", "PaRec_204326.wav");
        assertThat(suivi.lignes()).allSatisfy(l -> {
            assertThat(l.etatProperty().get()).isEqualTo(EtatUnite.EN_ATTENTE);
            assertThat(l.fractionProperty().get()).isEqualTo(0.0);
            assertThat(l.etapeProperty().get()).isEmpty();
        });
    }

    @Test
    @DisplayName("mode conservation : copie (barre 0 → 0,5) puis transformation (étape) puis terminé (1, étape vide)")
    void cycle_conservation_copie_puis_transformation() {
        SuiviLignesFichiers suivi = new SuiviLignesFichiers();
        suivi.planifier(List.of("a.wav"));

        suivi.copieDemarree(1);
        assertThat(ligne(suivi, 1).etatProperty().get()).isEqualTo(EtatUnite.EN_COURS);
        assertThat(ligne(suivi, 1).etapeProperty().get()).isEqualTo("Copie");

        suivi.copieTerminee(1);
        assertThat(ligne(suivi, 1).fractionProperty().get()).isEqualTo(0.5);

        suivi.transformationDemarree(1);
        assertThat(ligne(suivi, 1).etapeProperty().get()).isEqualTo("Transformation");
        assertThat(ligne(suivi, 1).fractionProperty().get())
                .as("la barre ne recule pas quand la transformation prend le relais")
                .isEqualTo(0.5);

        suivi.terminer(1);
        assertThat(ligne(suivi, 1).etatProperty().get()).isEqualTo(EtatUnite.TERMINEE);
        assertThat(ligne(suivi, 1).fractionProperty().get()).isEqualTo(1.0);
        assertThat(ligne(suivi, 1).etapeProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("mode sans copie : la transformation démarre la ligne directement (barre à 0)")
    void cycle_sans_copie_transformation_directe() {
        SuiviLignesFichiers suivi = new SuiviLignesFichiers();
        suivi.planifier(List.of("a.wav"));

        suivi.transformationDemarree(1);
        assertThat(ligne(suivi, 1).etatProperty().get()).isEqualTo(EtatUnite.EN_COURS);
        assertThat(ligne(suivi, 1).etapeProperty().get()).isEqualTo("Transformation");
        assertThat(ligne(suivi, 1).fractionProperty().get()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("#155 : un fichier rejeté passe « échec » avec sa raison, l'étape s'efface")
    void rejet_passe_la_ligne_en_echec() {
        SuiviLignesFichiers suivi = new SuiviLignesFichiers();
        suivi.planifier(List.of("a.wav", "b.wav"));
        suivi.transformationDemarree(2);

        suivi.echouer(2, "pas un WAV");

        assertThat(ligne(suivi, 2).etatProperty().get()).isEqualTo(EtatUnite.ECHEC);
        assertThat(ligne(suivi, 2).raisonEchecProperty().get()).isEqualTo("pas un WAV");
        assertThat(ligne(suivi, 2).etapeProperty().get()).isEmpty();
        assertThat(ligne(suivi, 1).etatProperty().get()).isEqualTo(EtatUnite.EN_ATTENTE); // pas touchée
    }

    @Test
    @DisplayName("multi-nuits : replanifier remplace les lignes par celles de la nuit en cours")
    void replanifier_remplace_les_lignes() {
        SuiviLignesFichiers suivi = new SuiviLignesFichiers();
        suivi.planifier(List.of("nuit1-a.wav", "nuit1-b.wav"));
        suivi.terminer(1);

        suivi.planifier(List.of("nuit2-a.wav"));

        assertThat(suivi.lignes()).hasSize(1);
        assertThat(suivi.lignes().getFirst().nomFichier()).isEqualTo("nuit2-a.wav");
        assertThat(suivi.lignes().getFirst().etatProperty().get()).isEqualTo(EtatUnite.EN_ATTENTE);
    }

    private static LigneFichierImport ligne(SuiviLignesFichiers suivi, int numero) {
        return suivi.lignes().stream()
                .filter(l -> l.numero() == numero)
                .findFirst()
                .orElseThrow();
    }
}

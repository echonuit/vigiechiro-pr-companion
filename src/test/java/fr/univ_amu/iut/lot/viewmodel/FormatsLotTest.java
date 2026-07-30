package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.EtatLot;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Formatage textuel de l'écran M-Lot (extrait de [LotViewModel]) : récapitulatif et message d'état.
class FormatsLotTest {

    @Test
    @DisplayName("recapLisible : « N séquences · volume », ou « volume inconnu » si le volume manque")
    void recap_lisible() {
        assertThat(FormatsLot.recapLisible(new EtatLot(StatutWorkflow.VERIFIE, "/ws", 5, 8192L, List.of(), null)))
                .contains("5 séquences");
        assertThat(FormatsLot.recapLisible(new EtatLot(StatutWorkflow.VERIFIE, "/ws", 5, null, List.of(), null)))
                .contains("volume inconnu");
    }

    @Test
    @DisplayName("messageEtat : déposé / vide selon le statut, un ÉTAT, jamais un compte rendu (#1890)")
    void message_etat() {
        assertThat(FormatsLot.messageEtat(new EtatLot(StatutWorkflow.DEPOSE, "/ws", 5, 8192L, List.of(), "2026-06-22")))
                .contains("déposé");
        assertThat(FormatsLot.messageEtat(new EtatLot(StatutWorkflow.VERIFIE, "/ws", 5, 8192L, List.of(), null)))
                .isEmpty();
        // « Prêt à déposer » ne dit plus « Dépôt préparé » : ce texte rendait compte de l'étape ①, donc il
        // appartient au retour d'opération de `preparer()`. Déduit du statut, il s'affichait aussi à la
        // simple ouverture d'un passage préparé la veille, annonçant une action qui n'avait pas eu lieu.
        assertThat(FormatsLot.messageEtat(new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws", 5, 8192L, List.of(), null)))
                .isEmpty();
    }

    @Test
    @DisplayName("#823 : bilanArchives résume nombre + volume, vide sans archive")
    void bilan_archives() {
        SuiviLignesArchives suivi = new SuiviLignesArchives();
        assertThat(FormatsLot.bilanArchives(suivi.lignes())).isEmpty();

        suivi.afficherTerminees(List.of(
                new fr.univ_amu.iut.lot.model.ArchiveDepot(java.nio.file.Path.of("/d/Car-1.zip"), 1, 2048L, 2),
                new fr.univ_amu.iut.lot.model.ArchiveDepot(java.nio.file.Path.of("/d/Car-2.zip"), 2, 4096L, 3)));

        assertThat(FormatsLot.bilanArchives(suivi.lignes()))
                .startsWith("2 archive(s) · ")
                .endsWith(" dans depot/");
    }

    @Test
    @DisplayName("#980 : messageEtat signale un dépôt entamé mais incomplet (reprise possible)")
    void message_etat_depot_en_cours() {
        assertThat(FormatsLot.messageEtat(new EtatLot(StatutWorkflow.DEPOT_EN_COURS, "/ws", 5, 8192L, List.of(), null)))
                .contains("Dépôt Vigie-Chiro entamé")
                .contains("reprise");
    }

    @Test
    @DisplayName("#984 : libelleDepotEnCours, compteur honnête déposées / en cours / échecs, masqués si nuls")
    void libelle_depot_en_cours_honnete() {
        assertThat(FormatsLot.libelleDepotEnCours(0, 0, 0, 0)).isEqualTo("Dépôt en préparation…");
        // En cours et échecs nuls : pas de bruit, seulement le déposé/total.
        assertThat(FormatsLot.libelleDepotEnCours(3, 0, 0, 21)).isEqualTo("Dépôt : 3/21 déposé(s)");
        assertThat(FormatsLot.libelleDepotEnCours(3, 5, 1, 21))
                .isEqualTo("Dépôt : 3/21 déposé(s) · 5 en cours · 1 échec(s)");
    }
}

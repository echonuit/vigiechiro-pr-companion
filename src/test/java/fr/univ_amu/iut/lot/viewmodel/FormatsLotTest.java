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
    @DisplayName("messageEtat : déposé / prêt à déposer / vide selon le statut")
    void message_etat() {
        assertThat(FormatsLot.messageEtat(new EtatLot(StatutWorkflow.DEPOSE, "/ws", 5, 8192L, List.of(), "2026-06-22")))
                .contains("déposé");
        assertThat(FormatsLot.messageEtat(new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws", 5, 8192L, List.of(), null)))
                .contains("Lot préparé");
        assertThat(FormatsLot.messageEtat(new EtatLot(StatutWorkflow.VERIFIE, "/ws", 5, 8192L, List.of(), null)))
                .isEmpty();
    }

    @Test
    @DisplayName("#980 : messageEtat signale un dépôt entamé mais incomplet (reprise possible)")
    void message_etat_depot_en_cours() {
        assertThat(FormatsLot.messageEtat(new EtatLot(StatutWorkflow.DEPOT_EN_COURS, "/ws", 5, 8192L, List.of(), null)))
                .contains("Dépôt VigieChiro entamé")
                .contains("reprise");
    }
}

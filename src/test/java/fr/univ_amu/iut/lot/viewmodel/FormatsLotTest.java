package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.EtatLot;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Formatage textuel de l'écran M-Lot (extrait de [LotViewModel]) : lignes d'archive, récapitulatif,
/// message d'état.
class FormatsLotTest {

    @Test
    @DisplayName("archiveLisible : « nom · N fichiers · taille »")
    void archive_lisible() {
        assertThat(FormatsLot.archiveLisible(new ArchiveDepot(Path.of("/ws/depot/Car-1.zip"), 1, 2048L, 3)))
                .contains("Car-1.zip")
                .contains("3 fichiers");
    }

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
}

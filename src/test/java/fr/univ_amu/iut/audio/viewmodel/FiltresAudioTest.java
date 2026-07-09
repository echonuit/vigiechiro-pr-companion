package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Filtres composables de la table audio (#470) : conjonction (ET) de prédicats nommés, retrait,
/// filtre de statut, et **notification** de l'appelant (recalcul des compteurs) à chaque changement.
class FiltresAudioTest {

    @Test
    @DisplayName("Deux filtres se cumulent en ET ; retirer l'un garde l'autre ; chaque changement notifie")
    void deux_filtres_se_cumulent() {
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligne(1, "Pippip", StatutObservation.VALIDEE),
                ligne(2, "Nyclei", StatutObservation.NON_TOUCHEE),
                ligne(3, "Pippip", StatutObservation.NON_TOUCHEE));
        FilteredList<LigneObservationAudio> affichees = new FilteredList<>(source);
        int[] notifications = {0};
        Filtres<LigneObservationAudio> filtres = new Filtres<>(affichees, () -> notifications[0]++);

        // Filtre A : taxon Tadarida = Pippip → lignes 1 et 3.
        filtres.definir("taxon", ligne -> "Pippip".equals(ligne.taxonTadarida()));
        assertThat(affichees).extracting(LigneObservationAudio::idObservation).containsExactly(1L, 3L);

        // Filtre B cumulé (ET) : statut À revoir → intersection = ligne 3.
        filtres.definir("aRevoir", ligne -> ligne.statut() == StatutObservation.NON_TOUCHEE);
        assertThat(affichees).extracting(LigneObservationAudio::idObservation).containsExactly(3L);

        // Retrait du filtre taxon : le filtre statut reste actif (lignes 2 et 3).
        filtres.definir("taxon", null);
        assertThat(affichees).extracting(LigneObservationAudio::idObservation).containsExactly(2L, 3L);

        assertThat(notifications[0]).isEqualTo(3); // une notification par changement de filtre
    }

    @Test
    @DisplayName("reinitialiser retire tous les filtres actifs")
    void reinitialiser_retire_tout() {
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligne(1, "Pippip", StatutObservation.VALIDEE), ligne(2, "Nyclei", StatutObservation.NON_TOUCHEE));
        FilteredList<LigneObservationAudio> affichees = new FilteredList<>(source);
        Filtres<LigneObservationAudio> filtres = new Filtres<>(affichees, () -> {});

        filtres.definir("taxon", ligne -> "Pippip".equals(ligne.taxonTadarida()));
        assertThat(affichees).hasSize(1);

        filtres.reinitialiser();
        assertThat(affichees).hasSize(2);
    }

    private static LigneObservationAudio ligne(long id, String taxonTadarida, StatutObservation statut) {
        return new LigneObservationAudio(
                id,
                10 + id,
                7L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Site",
                taxonTadarida,
                0.9,
                null,
                null,
                statut,
                false,
                null,
                45,
                null,
                taxonTadarida,
                "Chiroptères",
                "f" + id + ".wav",
                0.2,
                0.4,
                null,
                false);
    }
}

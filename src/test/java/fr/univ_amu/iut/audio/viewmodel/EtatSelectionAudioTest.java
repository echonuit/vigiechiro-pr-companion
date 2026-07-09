package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// État de sélection extrait du `AudioViewModel` : les cinq drapeaux dérivés d'une ligne (présence,
/// observation, proposition Tadarida, référence, douteux) qui pilotent les boutons de la barre d'actions.
class EtatSelectionAudioTest {

    @Test
    @DisplayName("Sans sélection (null) : tous les drapeaux sont faux")
    void aucune_selection_tout_faux() {
        EtatSelectionAudio etat = new EtatSelectionAudio();
        etat.maj(null);
        assertThat(etat.presenteProperty().get()).isFalse();
        assertThat(etat.avecObservationProperty().get()).isFalse();
        assertThat(etat.avecTadaridaProperty().get()).isFalse();
        assertThat(etat.referenceProperty().get()).isFalse();
        assertThat(etat.douteuxProperty().get()).isFalse();
    }

    @Test
    @DisplayName("Observation Tadarida douteuse : présente + avecObservation + avecTadarida + douteux")
    void observation_tadarida_douteuse() {
        EtatSelectionAudio etat = new EtatSelectionAudio();
        etat.maj(ligne(100L, "pippip", false, true));
        assertThat(etat.presenteProperty().get()).isTrue();
        assertThat(etat.avecObservationProperty().get()).isTrue();
        assertThat(etat.avecTadaridaProperty().get()).isTrue();
        assertThat(etat.referenceProperty().get()).isFalse();
        assertThat(etat.douteuxProperty().get())
                .as("le drapeau douteux est repris")
                .isTrue();
    }

    @Test
    @DisplayName("Séquence non identifiée (idObservation nul) : présente mais ni observation ni Tadarida")
    void sequence_non_identifiee() {
        EtatSelectionAudio etat = new EtatSelectionAudio();
        etat.maj(ligne(null, null, false, false));
        assertThat(etat.presenteProperty().get()).isTrue();
        assertThat(etat.avecObservationProperty().get()).as("pas d'observation").isFalse();
        assertThat(etat.avecTadaridaProperty().get()).isFalse();
        assertThat(etat.douteuxProperty().get()).isFalse();
    }

    private static LigneObservationAudio ligne(
            Long idObservation, String taxonTadarida, boolean reference, boolean douteux) {
        return new LigneObservationAudio(
                idObservation,
                10L,
                7L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Site",
                taxonTadarida,
                taxonTadarida == null ? null : 0.9,
                null,
                null,
                StatutObservation.NON_TOUCHEE,
                reference,
                null,
                45,
                null,
                taxonTadarida,
                "Chiroptères",
                "f.wav",
                0.2,
                0.4,
                null,
                douteux);
    }
}

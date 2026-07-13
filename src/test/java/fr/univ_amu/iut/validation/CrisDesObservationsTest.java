package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.passage.model.CriAttendu;
import fr.univ_amu.iut.validation.model.CrisDesObservations;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Projection des observations en **cris attendus** (#1302) : la matière de la vérification
/// acoustique (#1309). La fréquence médiane est persistée en kHz et sort en Hz réels ; une
/// observation sans instants ni fréquence ne décrit aucun cri localisable et est écartée.
@ExtendWith(MockitoExtension.class)
class CrisDesObservationsTest {

    @Mock
    ObservationDao observationDao;

    @Test
    @DisplayName("Les observations localisables deviennent des cris attendus, en Hz réels")
    void observations_projetees_en_hz() {
        when(observationDao.findBySequence(10L)).thenReturn(List.of(observation(0.20, 0.32, 45)));

        List<CriAttendu> cris = new CrisDesObservations(observationDao).pour(10L);

        assertThat(cris).singleElement().satisfies(cri -> {
            assertThat(cri.debutSecondes()).isEqualTo(0.20);
            assertThat(cri.finSecondes()).isEqualTo(0.32);
            assertThat(cri.frequenceMedianeHz())
                    .as("45 kHz persistés -> 45 000 Hz réels")
                    .isEqualTo(45_000.0);
        });
    }

    @Test
    @DisplayName("Une observation sans instants ou sans fréquence médiane est écartée (rien à sonder)")
    void observation_incomplete_ecartee() {
        when(observationDao.findBySequence(10L))
                .thenReturn(List.of(
                        observation(null, 0.32, 45), observation(0.20, null, 45), observation(0.20, 0.32, null)));

        assertThat(new CrisDesObservations(observationDao).pour(10L)).isEmpty();
    }

    @Test
    @DisplayName("Séquence sans identifiant : aucune interrogation, liste vide")
    void sequence_sans_identifiant() {
        assertThat(new CrisDesObservations(observationDao).pour(null)).isEmpty();
    }

    private static Observation observation(Double debut, Double fin, Integer frequenceKHz) {
        return new Observation(
                1L,
                10L,
                debut,
                fin,
                frequenceKHz,
                "Pippip",
                0.9,
                null,
                null,
                null,
                null,
                false,
                ModeValidation.MANUEL,
                100L,
                false,
                null,
                null,
                null);
    }
}

package fr.univ_amu.iut.bibliotheque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.bibliotheque.model.EntreeBiblio;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Complément Mockito : isole la **logique de décision** du [ServiceBibliotheque]
/// indépendamment de la base (cf. SERVICE-CONVENTIONS §3.2) :
///
/// - le filtre `is_reference` ne charge la séquence que des observations retenues (les
///   non-références ne déclenchent aucun appel au [SequenceDao]) ;
/// - le taxon retenu est l'observateur s'il est saisi, sinon Tadarida ;
/// - une séquence absente lève une [RegleMetierException].
@ExtendWith(MockitoExtension.class)
class ServiceBibliothequeMockTest {

    @Mock
    private ObservationDao observationDao;

    @Mock
    private SequenceDao sequenceDao;

    private ServiceBibliotheque service() {
        return new ServiceBibliotheque(observationDao, sequenceDao);
    }

    @Test
    @DisplayName("Le taxon retenu est l'observateur si saisi, sinon Tadarida")
    void taxon_retenu_privilegie_l_observateur() {
        Observation corrigee = obs(10L, 100L, "Pippip", "Rhihip"); // référence, observateur ≠ Tadarida
        Observation brute = obs(11L, 101L, "Nyclei", null); // référence, pas d'observateur
        when(observationDao.findAll()).thenReturn(List.of(corrigee, brute));
        when(sequenceDao.findById(100L)).thenReturn(Optional.of(seq(100L, "x_000.wav", "/ws/x_000.wav")));
        when(sequenceDao.findById(101L)).thenReturn(Optional.of(seq(101L, "y_000.wav", "/ws/y_000.wav")));

        List<EntreeBiblio> entrees = service().exporterBibliotheque().entrees();

        assertThat(entrees).extracting(EntreeBiblio::taxon).containsExactly("Nyclei", "Rhihip");
    }

    @Test
    @DisplayName("Les observations non-référence ne déclenchent aucune lecture de séquence")
    void non_references_ne_chargent_aucune_sequence() {
        Observation nonRef = obs(false, 12L, 102L, "Tadten", null);
        when(observationDao.findAll()).thenReturn(List.of(nonRef));

        assertThat(service().exporterBibliotheque().nombre()).isZero();
        verify(sequenceDao, never()).findById(102L);
    }

    @Test
    @DisplayName("Une séquence introuvable lève une RegleMetierException")
    void sequence_introuvable_leve_regle_metier() {
        when(observationDao.findAll()).thenReturn(List.of(obs(13L, 999L, "Pippip", "Pippip")));
        when(sequenceDao.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().exporterBibliotheque())
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Séquence d'écoute introuvable");
    }

    private static Observation obs(long id, long idSequence, String tadarida, String observateur) {
        return obs(true, id, idSequence, tadarida, observateur);
    }

    private static Observation obs(boolean reference, long id, long idSequence, String tadarida, String observateur) {
        return new Observation(
                id,
                idSequence,
                null,
                null,
                40000,
                tadarida,
                null,
                null,
                observateur,
                observateur == null ? null : 0.9,
                null,
                reference,
                ModeValidation.MANUEL,
                1L,
                false);
    }

    private static SequenceDEcoute seq(long id, String nom, String chemin) {
        return new SequenceDEcoute(id, nom, 1L, 0, 0.0, 5.0, chemin, false, 1L);
    }
}

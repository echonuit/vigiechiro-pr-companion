package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.lot.model.CompacteurDepot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.VerificationCoherence;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Complément Mockito (cf. SERVICE-CONVENTIONS §3.2) : isole la règle dure R14 indépendamment
/// de la base. On vérifie que le verdict « À jeter » court-circuite tout le reste : ni la
/// vérification de cohérence ni la moindre écriture ne sont déclenchées avant le refus.
@ExtendWith(MockitoExtension.class)
class ServiceLotMockTest {

    @Mock
    private PassageDao passageDao;

    @Mock
    private SessionDao sessionDao;

    @Mock
    private SequenceDao sequenceDao;

    @Mock
    private VerificationCoherence verification;

    private static Passage passageAJeter(Long id) {
        return new Passage(
                id,
                1,
                2026,
                "2026-06-20",
                "21:30:00",
                "05:15:00",
                null,
                StatutWorkflow.VERIFIE,
                Verdict.A_JETER,
                null,
                null,
                null,
                10L,
                "1925492");
    }

    @Test
    @DisplayName("R14 : preparerLot refuse « À jeter » sans vérifier la cohérence ni écrire")
    void preparer_lot_a_jeter_court_circuite() {
        ServiceLot service = new ServiceLot(
                passageDao,
                sessionDao,
                sequenceDao,
                verification,
                new MoteurWorkflowPassage(),
                new HorlogeFigee(LocalDate.of(2026, 5, 31)),
                new CompacteurDepot(),
                mock(DepotUniteDao.class));
        when(passageDao.findById(1L)).thenReturn(Optional.of(passageAJeter(1L)));

        assertThatThrownBy(() -> service.preparerLot(1L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("À jeter");

        verify(verification, never()).verifier(any());
        verify(passageDao, never()).update(any());
    }
}

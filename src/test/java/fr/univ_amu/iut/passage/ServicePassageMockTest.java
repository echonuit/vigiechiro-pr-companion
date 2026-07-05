package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ReprefixeurSession;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.RattachementDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Variante du test de [ServicePassage] **isolant la règle dure R5 avec Mockito** : le DAO est un
/// mock, on ne touche aucune base. On vérifie que la décision (refuser / déléguer) court- circuite
/// ou non l'écriture, indépendamment de SQLite. Le test « réel » `@TempDir` reste le mode par
/// défaut ([ServicePassageTest]) ; ce complément cible la logique de décision.
@ExtendWith(MockitoExtension.class)
class ServicePassageMockTest {

    @Mock
    private PassageDao passageDao;

    @Mock
    private SessionDao sessionDao;

    @Mock
    private SequenceDao sequenceDao;

    @Mock
    private UniteDeTravail uniteDeTravail;

    @Mock
    private RattachementDao rattachementDao;

    @Mock
    private MaterielMicroDao materielDao;

    private ServicePassage service() {
        return new ServicePassage(
                passageDao,
                new MoteurWorkflowPassage(),
                new HorlogeFigee(LocalDate.of(2026, 6, 20)),
                sessionDao,
                sequenceDao,
                new ReprefixeurSession(),
                uniteDeTravail,
                rattachementDao,
                materielDao);
    }

    @Test
    @DisplayName("R5 : si le quadruplet existe déjà, le service refuse sans tenter d'insérer")
    void r5_quadruplet_existant_refuse_sans_insertion() {
        Passage existant = new Passage(
                1L,
                1,
                2026,
                "2026-06-20",
                null,
                null,
                null,
                StatutWorkflow.IMPORTE,
                null,
                null,
                null,
                null,
                1L,
                "1925492");
        when(passageDao.trouverParPointAnneePassage(1L, 2026, 1)).thenReturn(Optional.of(existant));

        assertThatThrownBy(() -> service()
                        .creerPassage(1L, "1925492", 1, LocalDate.of(2026, 6, 20), null, null, null, null, null))
                .isInstanceOf(RegleMetierException.class);

        verify(passageDao, never()).insert(any());
    }

    @Test
    @DisplayName("Quadruplet libre : le service délègue l'insertion au DAO")
    void quadruplet_libre_delegue_insertion() {
        Passage attendu = new Passage(
                7L,
                1,
                2026,
                "2026-06-20",
                null,
                null,
                null,
                StatutWorkflow.IMPORTE,
                null,
                null,
                null,
                null,
                1L,
                "1925492");
        when(passageDao.trouverParPointAnneePassage(1L, 2026, 1)).thenReturn(Optional.empty());
        when(passageDao.insert(any())).thenReturn(attendu);

        Passage cree =
                service().creerPassage(1L, "1925492", 1, LocalDate.of(2026, 6, 20), null, null, null, null, null);

        assertThat(cree).isEqualTo(attendu);
        verify(passageDao).insert(any());
    }
}

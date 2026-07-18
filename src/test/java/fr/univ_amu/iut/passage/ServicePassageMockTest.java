package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.CoordonneesPoint;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.PositionGeo;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.passage.model.CouvertureNuageuse;
import fr.univ_amu.iut.passage.model.FenetreObserveeNuit;
import fr.univ_amu.iut.passage.model.FournisseurMeteo;
import fr.univ_amu.iut.passage.model.MeteoReleve;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ServiceConditionsPassage;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.Vent;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private CoordonneesPoint coordonnees;

    @Mock
    private FournisseurMeteo fournisseurMeteo;

    @Mock
    private MaterielMicroDao materielDao;

    /// Passage minimal pour exercer une règle de décision : seules ses heures nous intéressent ici.
    private static Passage passageDeReference() {
        return new Passage(
                1L,
                1,
                2026,
                "2026-07-04",
                "15:00",
                "16:00",
                null,
                StatutWorkflow.IMPORTE,
                null,
                null,
                null,
                null,
                7L,
                "1997632");
    }

    private ServicePassage service() {
        return new ServicePassage(
                passageDao,
                new MoteurWorkflowPassage(),
                new HorlogeFigee(LocalDate.of(2026, 6, 20)),
                sessionDao,
                sequenceDao,
                mock(ServiceDisponibiliteAudio.class));
    }

    /// Conditions de la nuit (météo), extraites de ServicePassage (#1192).
    private ServiceConditionsPassage conditions() {
        return new ServiceConditionsPassage(
                passageDao,
                materielDao,
                mock(fr.univ_amu.iut.passage.model.dao.EnregistreurDao.class),
                coordonnees,
                fournisseurMeteo,
                mock(fr.univ_amu.iut.passage.model.FenetreObserveeNuit.class));
    }

    @Test
    @DisplayName("#1892 : une nuit que ses enregistrements ATTESTENT refuse la saisie, et le dit")
    void heures_prouvees_refusent_la_saisie() {
        FenetreObserveeNuit preuves = mock(FenetreObserveeNuit.class);
        when(preuves.pour(1L))
                .thenReturn(Optional.of(new FenetreObserveeNuit.Bornes(
                        LocalDateTime.of(2026, 7, 4, 21, 0), LocalDateTime.of(2026, 7, 5, 6, 0))));
        when(passageDao.findById(1L)).thenReturn(Optional.of(passageDeReference()));
        ServiceConditionsPassage service = new ServiceConditionsPassage(
                passageDao,
                materielDao,
                mock(fr.univ_amu.iut.passage.model.dao.EnregistreurDao.class),
                coordonnees,
                fournisseurMeteo,
                preuves);

        // Accepter la saisie serait la trahir : le premier envoi la remplacerait par les preuves (#1878).
        // Mieux vaut refuser en le disant que faire semblant d'obéir.
        assertThatThrownBy(() -> service.definirHoraires(1L, "15:00", "16:00"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("établies par ses enregistrements");
        verify(passageDao, never()).update(any());
        assertThat(service.heuresProuvees(1L)).isTrue();
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

    @Test
    @DisplayName("#547 : recupererMeteo interroge le fournisseur au GPS du point et aux heures du passage")
    void recuperer_meteo_delegue_avec_gps_et_heures() {
        when(passageDao.findById(7L)).thenReturn(Optional.of(passageNuit(7L)));
        when(coordonnees.pour(1L)).thenReturn(Optional.of(new PositionGeo(43.4, -1.5)));
        MeteoReleve attendu = new MeteoReleve(8.5, 4.0, Vent.FAIBLE, CouvertureNuageuse.DE_25_A_50);
        when(fournisseurMeteo.pour(43.4, -1.5, LocalDate.of(2026, 6, 20), LocalTime.of(21, 30), LocalTime.of(5, 15)))
                .thenReturn(Optional.of(attendu));

        assertThat(conditions().recupererMeteo(7L)).contains(attendu);
    }

    @Test
    @DisplayName("#547 : sans GPS sur le point, recupererMeteo est vide et n appelle pas le fournisseur")
    void recuperer_meteo_sans_gps_est_empty() {
        when(passageDao.findById(7L)).thenReturn(Optional.of(passageNuit(7L)));
        when(coordonnees.pour(1L)).thenReturn(Optional.empty());

        assertThat(conditions().recupererMeteo(7L)).isEmpty();
        verifyNoInteractions(fournisseurMeteo);
    }

    private static Passage passageNuit(long id) {
        return new Passage(
                id,
                1,
                2026,
                "2026-06-20",
                "21:30:00",
                "05:15:00",
                null,
                StatutWorkflow.IMPORTE,
                null,
                null,
                null,
                null,
                1L,
                "1925492");
    }
}

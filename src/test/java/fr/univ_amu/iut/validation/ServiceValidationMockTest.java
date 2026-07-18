package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.ObservationVigieChiro;
import fr.univ_amu.iut.commun.model.CoordonneesPoint;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.commun.model.PositionGeo;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ExportVuCsv;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ParserCsvTadarida;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.dao.MessageObservationDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Complément ciblé (Mockito) : isole la **logique de décision** du
/// [ServiceValidation] indépendamment de la base (cf. `ServiceSitesMockTest`). On mocke les DAO
/// pour vérifier le statut dérivé (R15/R16/R17), le comportement de
/// [ServiceValidation#valider(Long)] (R24) et le refus dur de
/// [ServiceValidation#corriger(Long, String, Double)] sur taxon inconnu.
@ExtendWith(MockitoExtension.class)
class ServiceValidationMockTest {

    @Mock
    ResultatsIdentificationDao resultatsDao;

    @Mock
    ObservationDao observationDao;

    @Mock
    TaxonDao taxonDao;

    @Mock
    SessionDao sessionDao;

    @Mock
    SequenceDao sequenceDao;

    @Mock
    UniteDeTravail uniteDeTravail;

    /// Fil de discussion (#1417) : bouchonné comme les autres DAO. L'import CSV n'y touche jamais
    /// (seul l'import VigieChiro a des fils), mais le service l'exige à la construction.
    @Mock
    MessageObservationDao messageDao;

    @Mock
    PassageDao passageDao;

    /// Liens plateforme : le rattachement de la nuit entre dans le calcul de
    /// [ServiceValidation#publicationImpossible] (#1838).
    @Mock
    LienVigieChiroDao liens;

    @Mock
    CoordonneesPoint coordonnees;

    @Captor
    ArgumentCaptor<List<Observation>> observationsCaptor;

    private ServiceValidation service() {
        return new ServiceValidation(
                resultatsDao,
                observationDao,
                taxonDao,
                sessionDao,
                sequenceDao,
                new ParserCsvTadarida(),
                new ExportVuCsv(),
                uniteDeTravail,
                new HorlogeFigee(LocalDate.of(2026, 5, 31)),
                messageDao,
                liens);
    }

    /// Calcul de plage nuit (#549), sorti de ServiceValidation avec les projections audio (#1193).
    private PlageNuitPassage plageNuit() {
        return new PlageNuitPassage(passageDao, coordonnees);
    }

    private static Passage passageAix() {
        // Nuit du 20 juin 2026, point d'Aix (idPoint 5) : coucher ~21:23, lever ~05:58 (heure locale).
        return new Passage(
                1L,
                1,
                2026,
                "2026-06-20",
                "22:00:00",
                "05:00:00",
                null,
                StatutWorkflow.TRANSFORME,
                null,
                null,
                null,
                null,
                5L,
                "1925492");
    }

    private static Observation observation(String taxonTadarida, String observateur, Double probObs) {
        return new Observation(
                1L,
                10L,
                0.0,
                5.0,
                45,
                taxonTadarida,
                0.8,
                null,
                observateur,
                probObs,
                null,
                false,
                ModeValidation.NON_VALIDE,
                100L,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("statut : pas de taxon observateur → NON_TOUCHEE (R17)")
    void statut_non_touchee() {
        assertThat(service().statut(observation("Pippip", null, null))).isEqualTo(StatutObservation.NON_TOUCHEE);
    }

    @Test
    @DisplayName("statut : taxon observateur = Tadarida et prob renseignée → VALIDEE (R15)")
    void statut_validee() {
        assertThat(service().statut(observation("Pippip", "Pippip", 0.9))).isEqualTo(StatutObservation.VALIDEE);
    }

    @Test
    @DisplayName("statut : taxon observateur = Tadarida SANS probabilité numérique → VALIDEE (confiance _Vu)")
    void statut_validee_sans_probabilite() {
        // Un _Vu réel peut porter une confiance textuelle (« SUR ») lue comme prob inconnue : la décision
        // tient à la présence du taxon observateur, pas à sa probabilité (sinon une validation réelle
        // ressortirait « non revue »).
        assertThat(service().statut(observation("Pippip", "Pippip", null))).isEqualTo(StatutObservation.VALIDEE);
    }

    @Test
    @DisplayName("statut : taxon observateur différent → CORRIGEE (R16)")
    void statut_corrigee() {
        assertThat(service().statut(observation("noise", "Pippip", 0.9))).isEqualTo(StatutObservation.CORRIGEE);
    }

    @Test
    @DisplayName("valider : recopie le taxon Tadarida, mode manuel (R24), une seule écriture")
    void valider_recopie_le_taxon_tadarida() {
        when(observationDao.findById(1L)).thenReturn(Optional.of(observation("Pippip", null, null)));

        Observation validee = service().valider(1L);

        assertThat(validee.taxonObservateur()).isEqualTo("Pippip");
        assertThat(validee.probObservateur()).isEqualTo(0.8); // reprend la prob Tadarida
        assertThat(validee.modeValidation()).isEqualTo(ModeValidation.MANUEL);
        verify(observationDao).update(any(Observation.class));
    }

    @Test
    @DisplayName("corriger : un taxon observateur inconnu est refusé sans écriture")
    void corriger_taxon_inconnu_court_circuite_l_ecriture() {
        when(observationDao.findById(1L)).thenReturn(Optional.of(observation("noise", null, null)));
        when(taxonDao.findById("ZZZZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().corriger(1L, "ZZZZZZ", 0.5)).isInstanceOf(RegleMetierException.class);
        verify(observationDao, never()).update(any(Observation.class));
    }

    @Test
    @DisplayName("corriger : un taxon connu et différent met à jour l'observateur")
    void corriger_taxon_connu() {
        when(observationDao.findById(1L)).thenReturn(Optional.of(observation("noise", null, null)));
        when(taxonDao.findById("Pippip"))
                .thenReturn(Optional.of(new Taxon("Pippip", "Pipistrellus pipistrellus", null, 1L)));

        Observation corrigee = service().corriger(1L, "Pippip", 0.99);

        assertThat(corrigee.taxonObservateur()).isEqualTo("Pippip");
        assertThat(service().statut(corrigee)).isEqualTo(StatutObservation.CORRIGEE);
        verify(observationDao).update(any(Observation.class));
    }

    @Test
    @DisplayName("marquerReference : pose PUIS retire is_reference, sans toucher au taxon ni au mode")
    void marquer_reference() {
        when(observationDao.findById(1L)).thenReturn(Optional.of(observation("Pippip", "Nyclei", 0.9)));
        ArgumentCaptor<Observation> ecrites = ArgumentCaptor.forClass(Observation.class);

        Observation marquee = service().marquerReference(1L, true);
        assertThat(marquee.reference()).isTrue();
        assertThat(marquee.taxonObservateur()).isEqualTo("Nyclei"); // taxon observateur inchangé
        assertThat(marquee.taxonTadarida()).isEqualTo("Pippip");
        assertThat(marquee.modeValidation()).isEqualTo(ModeValidation.NON_VALIDE); // mode inchangé

        Observation retiree = service().marquerReference(1L, false);
        assertThat(retiree.reference()).isFalse();

        // L'objet réellement persisté porte bien le flag demandé (pose puis retrait).
        verify(observationDao, times(2)).update(ecrites.capture());
        assertThat(ecrites.getAllValues()).extracting(Observation::reference).containsExactly(true, false);
    }

    @Test
    @DisplayName("cheminAudio : résout le WAV transformé (R22) de la séquence (E7.S3)")
    void chemin_audio_resout_le_wav() {
        when(sequenceDao.findById(10L))
                .thenReturn(Optional.of(new SequenceDEcoute(
                        10L, "seq_000.wav", 1L, 0, 0.0, 5.0, "/ws/transformes/seq_000.wav", true, 1L)));

        assertThat(service().cheminAudio(10L)).contains(Path.of("/ws/transformes/seq_000.wav"));
    }

    @Test
    @DisplayName("cheminAudio : vide si idSequence null ou séquence introuvable")
    void chemin_audio_vide_si_introuvable() {
        assertThat(service().cheminAudio(null)).isEmpty();

        when(sequenceDao.findById(99L)).thenReturn(Optional.empty());
        assertThat(service().cheminAudio(99L)).isEmpty();
    }

    @Test
    @DisplayName("#549 : plage nuit par défaut = heures pleines du coucher/lever au GPS du point")
    void plage_nuit_par_defaut_depuis_ephemeride() {
        when(passageDao.findById(1L)).thenReturn(Optional.of(passageAix()));
        when(coordonnees.pour(5L)).thenReturn(Optional.of(new PositionGeo(43.529, 5.447)));

        // Aix, nuit du 20 juin 2026 : coucher ~21:23 (heure 21), lever ~05:58 (heure 5).
        assertThat(plageNuit().pour(1L)).contains(new PlageNuit(21, 5));
    }

    @Test
    @DisplayName("#549 : point sans GPS → pas de plage nuit (repli sur le défaut fixe 21 h → 6 h)")
    void plage_nuit_sans_gps_est_vide() {
        when(passageDao.findById(1L)).thenReturn(Optional.of(passageAix()));
        when(coordonnees.pour(5L)).thenReturn(Optional.empty());

        assertThat(plageNuit().pour(1L)).isEmpty();
    }

    @Test
    @DisplayName("#549 : passage introuvable → pas de plage nuit")
    void plage_nuit_passage_introuvable_est_vide() {
        when(passageDao.findById(1L)).thenReturn(Optional.empty());

        assertThat(plageNuit().pour(1L)).isEmpty();
    }

    @Test
    @DisplayName("#719 : importerDepuisVigieChiro convertit les donnees et insère les observations rattachées")
    void importer_depuis_vigiechiro() throws Exception {
        Long idPassage = 42L;
        Long idSession = 7L;
        // Séquence audio déjà importée (nuit) : la donnée VigieChiro se rattache par nom de fichier.
        SequenceDEcoute sequence = new SequenceDEcoute(
                100L,
                "Car130711-Z41_000.wav",
                null,
                null,
                null,
                null,
                "/x/Car130711-Z41_000.wav",
                true,
                idSession,
                null);
        when(sessionDao.trouverParPassage(idPassage))
                .thenReturn(Optional.of(new SessionDEnregistrement(idSession, "/x", null, null, idPassage)));
        when(sequenceDao.findBySession(idSession)).thenReturn(List.of(sequence));
        when(taxonDao.findAll()).thenReturn(List.of()); // référentiel vide -> le taxon Tadarida est auto-créé
        when(resultatsDao.insert(any(), any()))
                .thenReturn(new ResultatsIdentification(9L, "vigiechiro", "VigieChiro", "2026-05-31", idPassage));
        // Exécute réellement le bloc transactionnel (connexion inutile : les DAO sont mockés).
        doAnswer(invocation -> {
                    invocation
                            .getArgument(
                                    0, fr.univ_amu.iut.commun.persistence.UniteDeTravail.TravailTransactionnel.class)
                            .executer(null);
                    return null;
                })
                .when(uniteDeTravail)
                .executer(any());

        // Titre sans extension (comme l'API) ; le rattachement compare sans l'extension .wav.
        List<DonneeVigieChiro> donnees = List.of(new DonneeVigieChiro(
                "d1",
                "Car130711-Z41_000",
                List.of(new ObservationVigieChiro(
                        0, "Pipkuh", 0.99, 44.0, 0.8, 4.7, "noise", null, null, null, null, List.of()))));

        BilanImport bilan = service().importerDepuisVigieChiro(idPassage, donnees, false);

        assertThat(bilan.importees()).isEqualTo(1);
        assertThat(bilan.taxonsHorsReferentiel()).isEqualTo(1); // Pipkuh auto-créé (référentiel vide)
        verify(taxonDao).enregistrerHorsReferentiel(any(), any());
        verify(observationDao).insererTout(any(), observationsCaptor.capture());
        assertThat(observationsCaptor.getValue()).singleElement().satisfies(obs -> {
            assertThat(obs.idSequence()).isEqualTo(100L);
            assertThat(obs.taxonTadarida()).isEqualTo("Pipkuh");
            assertThat(obs.frequenceMedianeKHz()).isEqualTo(44);
            assertThat(obs.idResultats()).isEqualTo(9L);
            assertThat(obs.modeValidation()).isEqualTo(ModeValidation.NON_VALIDE);
            assertThat(obs.idDonneeVigieChiro())
                    .as("l'ancrage plateforme (donnée, indice) est posé à l'import (#1139)")
                    .isEqualTo("d1");
            assertThat(obs.indiceVigieChiro()).isEqualTo(0);
        });
    }

    private static ResultatsIdentification resultats(Long idPassage) {
        return new ResultatsIdentification(100L, "observations.csv", "\"Brut\"", "2026-07-07T10:00:00", idPassage);
    }

    /// Observation **ancrée** à la plateforme (`idDonneeVigieChiro` renseigné) : le reste est indifférent au
    /// calcul de [ServiceValidation#publicationImpossible].
    private static Observation observationAncree() {
        return new Observation(
                1L,
                10L,
                0.0,
                5.0,
                45,
                "Pippip",
                0.8,
                null,
                "Pippip",
                0.9,
                null,
                false,
                ModeValidation.MANUEL,
                100L,
                false,
                "d1",
                0,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("publicationImpossible : reconstruit par CSV et nuit non rattachée → true (#1596)")
    void publication_impossible_passage_reconstruit_non_rattache() {
        when(resultatsDao.findByPassage(7L)).thenReturn(Optional.of(resultats(7L)));
        when(observationDao.findByResults(100L))
                .thenReturn(List.of(observation("Pippip", "Pippip", 0.9), observation("noise", "Pippip", 0.9)));

        assertThat(service().publicationImpossible(7L)).isTrue();
    }

    @Test
    @DisplayName("publicationImpossible : aucun ancrage mais nuit rattachée → false (la publication l'acquerra, #1838)")
    void publication_impossible_passage_reconstruit_mais_rattache() {
        when(resultatsDao.findByPassage(7L)).thenReturn(Optional.of(resultats(7L)));
        when(observationDao.findByResults(100L))
                .thenReturn(List.of(observation("Pippip", "Pippip", 0.9), observation("noise", "Pippip", 0.9)));
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "7")).thenReturn(Optional.of("participation-1"));

        // Même état d'ancrage que le test précédent : seul le rattachement change. C'est lui qui décide,
        // depuis que la publication sait rapatrier l'ancrage manquant — griser ici interdirait le cas.
        assertThat(service().publicationImpossible(7L)).isFalse();
    }

    @Test
    @DisplayName("publicationImpossible : au moins une observation ancrée → false (publication partielle possible)")
    void publication_impossible_partiel_reste_publiable() {
        when(resultatsDao.findByPassage(7L)).thenReturn(Optional.of(resultats(7L)));
        when(observationDao.findByResults(100L))
                .thenReturn(List.of(observation("Pippip", "Pippip", 0.9), observationAncree()));

        assertThat(service().publicationImpossible(7L)).isFalse();
    }

    @Test
    @DisplayName("publicationImpossible : passage sans résultats → false (rien à publier)")
    void publication_impossible_sans_resultats() {
        when(resultatsDao.findByPassage(7L)).thenReturn(Optional.empty());

        assertThat(service().publicationImpossible(7L)).isFalse();
    }
}

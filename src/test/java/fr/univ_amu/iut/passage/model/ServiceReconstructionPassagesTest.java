package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SuiviPagination;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.PointParLocalite;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Reconstruction d'un passage **jamais importé localement** (#1305) : la plateforme est bouchonnée
/// (`ClientVigieChiro` mocké), la base est jetable. On vérifie que le passage naît **archivé**, que ses
/// lignes de séquences sont recréées à partir des noms distants (sans quoi l'import d'observations
/// n'aurait rien à quoi se rattacher), que le lien est posé, et que les refus sont explicites.
class ServiceReconstructionPassagesTest {

    private static final String ID_USER = "u-1";
    private static final String PARTICIPATION = "6a53f5faae21902a597394d3";

    /// Seconde participation du même point : de quoi vérifier qu'un geste porte bien sur la nuit demandée
    /// et pas sur sa voisine (#2638). Une base réelle en compte des dizaines depuis #2554.
    private static final String PARTICIPATION_VOISINE = "6a53f5faae21902a597394e7";
    private static final String SEQ_1 = "Car130711-2026-Pass1-Z41-PaRec_20260703_220529_000";
    private static final String SEQ_2 = "Car130711-2026-Pass1-Z41-PaRec_20260703_220534_000";
    private static final String SEQ_VOISINE_1 = "Car130711-2026-Pass2-Z41-PaRec_20260704_221030_000";
    private static final String SEQ_VOISINE_2 = "Car130711-2026-Pass2-Z41-PaRec_20260704_221035_000";
    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 7, 14, 2, 0);

    /// CSV Tadarida BRUT réel en miniature (séparateur `;`, entête quotée) : 3 observations sur 2 fichiers.
    private static final String CSV_OBSERVATIONS =
            "\"nom du fichier\";\"temps_debut\";\"temps_fin\";\"frequence_mediane\";\"tadarida_taxon\";\"tadarida_probabilite\"\n"
                    + "\"" + SEQ_1 + "\";0.1;2.8;45.0;\"Pippip\";0.9\n"
                    + "\"" + SEQ_1 + "\";3.0;3.5;30.0;\"noise\";0.4\n"
                    + "\"" + SEQ_2 + "\";0.0;2.7;22.0;\"Pippip\";0.8\n";

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private ClientVigieChiro client;
    private ImportObservations importObservations;
    private PassageDao passageDao;
    private SessionDao sessionDao;
    private SequenceDao sequenceDao;
    private LienVigieChiroDao liens;
    private Long idPoint;
    private ServiceReconstructionPassages service;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "130711", "Carré", Protocole.STANDARD, null, "2026-05-01", ID_USER));
        idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "Z41", null, null, null, site.id()))
                .id();
        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        sequenceDao = new SequenceDao(source);
        liens = new LienVigieChiroDao(source);

        client = mock(ClientVigieChiro.class);
        importObservations = mock(ImportObservations.class);
        PointParLocalite pointParLocalite = (carre, point) ->
                "130711".equals(carre) && "Z41".equals(point) ? Optional.of(idPoint) : Optional.empty();
        service = new ServiceReconstructionPassages(
                source,
                client,
                pointParLocalite,
                Optional.of(importObservations),
                new Workspace(dossier),
                new HorlogeFigee(MAINTENANT),
                hydratation(Optional.of(importObservations)));
    }

    /// Le noyau de **contenu** de la synchro (#2557), branché sur la même plateforme bouchonnée : c'est lui
    /// qui hydrate les nuits sans séquences, celles qui viennent d'être créées comme les squelettes déjà là.
    private HydratationSquelette hydratation(Optional<ImportObservations> importateur) {
        return new HydratationSquelette(
                source, client, new Workspace(dossier), new HorlogeFigee(MAINTENANT), importateur);
    }

    @Test
    @DisplayName("Les participations sans passage local sont listées, avec le point local résolu")
    void orphelines_listees() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));

        List<ParticipationOrpheline> orphelines = service.orphelines();

        assertThat(orphelines).singleElement().satisfies(orpheline -> {
            assertThat(orpheline.idParticipation()).isEqualTo(PARTICIPATION);
            assertThat(orpheline.numeroCarre()).isEqualTo("130711");
            assertThat(orpheline.codePoint()).isEqualTo("Z41");
            assertThat(orpheline.pointLocalConnu()).isTrue();
        });
    }

    @Test
    @DisplayName("Une participation déjà rattachée à un passage local n'est plus orpheline")
    void participation_rattachee_exclue() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, "42", PARTICIPATION));

        assertThat(service.orphelines()).isEmpty();
    }

    @Test
    @DisplayName("Reconstruire : passage archivé, séquences recréées depuis les noms distants, lien posé")
    void reconstruire_cree_un_passage_archive() {
        bouchonnerPlateforme();

        RapportReconstruction rapport = service.reconstruire(PARTICIPATION);

        assertThat(rapport.sequencesRecreees()).isEqualTo(2);
        assertThat(rapport.observationsImportees()).isEqualTo(3);
        assertThat(rapport.lacunes()).isNotEmpty();

        Passage passage = passageDao.findById(rapport.idPassage()).orElseThrow();
        assertThat(passage.statutWorkflow())
                .as("la participation existe sur la plateforme : le passage est déposé")
                .isEqualTo(StatutWorkflow.DEPOSE);
        assertThat(passage.idPoint()).isEqualTo(idPoint);
        assertThat(passage.annee()).isEqualTo(2026);

        SessionDEnregistrement session =
                sessionDao.trouverParPassage(rapport.idPassage()).orElseThrow();
        assertThat(session.volumeSequencesOctets())
                .as("le passage naît sans audio : aucun fichier n'a jamais été importé ici")
                .isZero();
        assertThat(sequenceDao.findBySession(session.id()))
                .as("les lignes de séquences existent (sans fichier) : sans elles, aucune observation ne se rattache")
                .extracting(SequenceDEcoute::nomFichier)
                .containsExactlyInAnyOrder(SEQ_1 + ".wav", SEQ_2 + ".wav");
        assertThat(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(rapport.idPassage())))
                .contains(PARTICIPATION);
        verify(importObservations).importer(eq(rapport.idPassage()), any(), eq(false));
    }

    @Test
    @DisplayName("#1689 : la reconstruction rapatrie le n° de série (clé canonique), la météo et le micro")
    void reconstruire_rapatrie_serie_meteo_micro() {
        bouchonnerPlateforme();
        // La participation porte le n° de série sous la clé CANONIQUE VigieChiro (formulaire web), plus la
        // météo et le matériel micro : tout doit être ramené, plutôt que « INCONNU » + météo/micro vides.
        when(client.participation(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(detailComplet()));

        RapportReconstruction rapport = service.reconstruire(PARTICIPATION);

        Passage passage = passageDao.findById(rapport.idPassage()).orElseThrow();
        assertThat(passage.idEnregistreur())
                .as("n° de série lu sous la clé canonique, pas « INCONNU »")
                .isEqualTo("1997632");
        assertThat(passage.donneesMeteo())
                .as("la météo (vent/couverture) est rapatriée de la participation")
                .isNotNull();
        MaterielMicro micro = new MaterielMicroDao(source).pour(rapport.idPassage());
        assertThat(micro.typeMicro()).isEqualTo("ICS");
        assertThat(micro.positionMicro()).isEqualTo(PositionMicro.CANOPEE);
    }

    @Test
    @DisplayName("Reconstruction par CSV (#1565) : un seul téléchargement, séquences recréées, sans paginer donnees")
    void reconstruire_par_csv_evite_la_pagination() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));
        when(client.participation(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(detail()));
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(Optional.of(CSV_OBSERVATIONS)));
        when(importObservations.nomsSequencesCsv(CSV_OBSERVATIONS)).thenReturn(List.of(SEQ_1, SEQ_2));
        when(importObservations.importerCsv(anyLong(), eq(CSV_OBSERVATIONS), eq(false)))
                .thenReturn("3 observation(s) importée(s).");

        RapportReconstruction rapport = service.reconstruire(PARTICIPATION);

        assertThat(rapport.sequencesRecreees()).isEqualTo(2);
        assertThat(rapport.observationsImportees())
                .as("le compte d'observations vient des lignes de données du CSV")
                .isEqualTo(3);
        SessionDEnregistrement session =
                sessionDao.trouverParPassage(rapport.idPassage()).orElseThrow();
        assertThat(sequenceDao.findBySession(session.id()))
                .as("les séquences sont recréées depuis les noms de fichiers du CSV")
                .extracting(SequenceDEcoute::nomFichier)
                .containsExactlyInAnyOrder(SEQ_1 + ".wav", SEQ_2 + ".wav");
        verify(importObservations).importerCsv(eq(rapport.idPassage()), eq(CSV_OBSERVATIONS), eq(false));
        verify(client, never()).donnees(any(), any());
        verify(importObservations, never()).importer(anyLong(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Le passage reconstruit porte le VRAI préfixe R6 (carré, année, passage, point)")
    void reconstruire_pose_le_vrai_prefixe() {
        bouchonnerPlateforme();

        RapportReconstruction rapport = service.reconstruire(PARTICIPATION);

        // Le préfixe est celui que l'audit RECALCULE depuis le passage : un préfixe fabriqué
        // (« Car000000-…-P<idPoint> ») faisait signaler le passage PREFIXE_NON_CONFORME à vie (#1050).
        SessionDEnregistrement session =
                sessionDao.trouverParPassage(rapport.idPassage()).orElseThrow();
        assertThat(Path.of(session.cheminRacine()).getFileName()).hasToString("Car130711-2026-Pass1-Z41");
        assertThat(new EnregistrementOriginalDao(source).findBySession(session.id()))
                .as("l'original porteur aussi : c'est lui que le contrôle de préfixe voyait échouer")
                .singleElement()
                .satisfies(original -> assertThat(original.nomFichier()).startsWith("Car130711-2026-Pass1-Z41-"));
    }

    @Test
    @DisplayName("Le passage reconstruit est ABSENTE : consultable, pas écoutable")
    void passage_reconstruit_est_absente() {
        bouchonnerPlateforme();

        RapportReconstruction rapport = service.reconstruire(PARTICIPATION);

        ServiceDisponibiliteAudio disponibilite =
                new ServiceDisponibiliteAudio(sessionDao, sequenceDao, new Workspace(dossier));
        assertThat(disponibilite.decompte(rapport.idPassage())).isEqualTo(new DecompteAudio(0, 2));
        assertThat(disponibilite.disponibilite(rapport.idPassage())).isEqualTo(DisponibiliteAudio.ABSENTE);
    }

    @Test
    @DisplayName("Point d'écoute inconnu localement : refus explicite (créez d'abord le site et le point)")
    void point_inconnu_refuse() {
        when(client.mesParticipations())
                .thenReturn(new ReponseApi.Succes<>(List.of(new ParticipationVigieChiro(
                        PARTICIPATION, "A9", "2026-07-03T22:00:00+02:00", "Vigiechiro - Point Fixe-999999"))));
        when(client.participation(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(detail()));

        assertThatThrownBy(() -> service.reconstruire(PARTICIPATION))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("n'existe pas localement");
    }

    @Test
    @DisplayName("Hors connexion : refus explicite, aucun passage créé")
    void hors_connexion_refuse() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.NonConnecte<>());

        assertThatThrownBy(() -> service.orphelines())
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Non connecté");
        assertThat(passageDao.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Analyse non terminée (aucune donnée) : refus explicite, aucun passage créé")
    void aucune_donnee_refuse() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));
        when(client.participation(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(detail()));
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(Optional.empty()));
        when(client.donnees(eq(PARTICIPATION), any())).thenReturn(new ReponseApi.Succes<>(List.of()));

        assertThatThrownBy(() -> service.reconstruire(PARTICIPATION))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("analyse");
        assertThat(passageDao.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Feature « Import Vigie-Chiro » désactivée : refus explicite, aucun passage créé")
    void import_desactive_refuse() {
        bouchonnerPlateforme();
        ServiceReconstructionPassages sansImport = new ServiceReconstructionPassages(
                source,
                client,
                (carre, point) -> Optional.of(idPoint),
                Optional.empty(),
                new Workspace(dossier),
                new HorlogeFigee(MAINTENANT),
                hydratation(Optional.empty()));

        assertThatThrownBy(() -> sansImport.reconstruire(PARTICIPATION))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Import Vigie-Chiro");
        assertThat(passageDao.findAll())
                .as("le refus tombe avant toute écriture : mieux vaut rien créer que créer à moitié")
                .isEmpty();
    }

    @Test
    @DisplayName("La reconstruction émet une progression croissante jusqu'à « Terminé » (100 %)")
    void reconstruire_emet_une_progression_jusqua_termine() {
        bouchonnerPlateforme();
        List<Progression> points = new ArrayList<>();

        service.reconstruire(PARTICIPATION, points::add, JetonAnnulation.neutre());

        assertThat(points)
                .as("la barre ne doit jamais reculer")
                .extracting(Progression::fraction)
                .isSorted();
        assertThat(points).last().satisfies(dernier -> {
            assertThat(dernier.fraction()).isEqualTo(1.0);
            assertThat(dernier.libelle()).contains("Terminé");
        });
    }

    @Test
    @DisplayName("Annulation pendant la création des séquences : aucun passage partiel ne subsiste")
    void annulation_ne_laisse_aucun_passage_partiel() {
        bouchonnerPlateforme();
        JetonAnnulation jeton = new JetonAnnulation();
        // On demande l'annulation au point « Création des séquences » (émis juste avant le lot) : la
        // reconstruction la constate avant l'import et compense le passage déjà écrit.
        Consumer<Progression> annuleAuxSequences = point -> {
            if (point.libelle().contains("séquences")) {
                jeton.annuler();
            }
        };

        assertThatThrownBy(() -> service.reconstruire(PARTICIPATION, annuleAuxSequences, jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        assertThat(passageDao.findAll())
                .as("l'annulation compense le passage à moitié reconstruit (ON DELETE CASCADE)")
                .isEmpty();
        assertThat(liens.tous(LienVigieChiro.ENTITE_PASSAGE)).isEmpty();
    }

    @Test
    @DisplayName("Échec de l'import après écriture : le passage partiel est compensé, l'erreur remonte")
    void echec_import_compense_le_passage_partiel() {
        bouchonnerPlateforme();
        when(importObservations.importer(anyLong(), any(), anyBoolean()))
                .thenThrow(new IllegalStateException("import cassé"));

        assertThatThrownBy(() -> service.reconstruire(PARTICIPATION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("import cassé");

        assertThat(passageDao.findAll())
                .as("un échec de l'import ne doit pas laisser de passage sans ses observations")
                .isEmpty();
        assertThat(liens.tous(LienVigieChiro.ENTITE_PASSAGE)).isEmpty();
    }

    @Test
    @DisplayName("Depuis une orpheline en main : reconstruit sans re-télécharger toute la liste (#1522)")
    void reconstruire_depuis_orpheline_ne_retelecharge_pas_la_liste() {
        when(client.participation(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(detail()));
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(Optional.empty()));
        when(client.donnees(eq(PARTICIPATION), any()))
                .thenReturn(
                        new ReponseApi.Succes<>(List.of(new DonneeVigieChiro("d-1", SEQ_1, List.of(observation())))));
        when(importObservations.importer(anyLong(), any(), anyBoolean())).thenReturn("1 observation(s) importée(s).");
        ParticipationOrpheline orpheline =
                new ParticipationOrpheline(PARTICIPATION, "130711", "Z41", "2026-07-03T22:00:00+02:00", true);

        RapportReconstruction rapport = service.reconstruire(orpheline, progression -> {}, JetonAnnulation.neutre());

        assertThat(rapport.idPassage()).isNotNull();
        verify(client, never()).mesParticipations();
    }

    @Test
    @DisplayName("Téléchargement : progression page par page, et « Annuler » interrompt sans rien écrire")
    void telechargement_progresse_par_page_et_sannule() {
        when(client.participation(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(detail()));
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(Optional.empty()));
        JetonAnnulation jeton = new JetonAnnulation();
        // Le client relaie chaque page ; on annule après la première : le relais consulte le jeton et lève.
        when(client.donnees(eq(PARTICIPATION), any())).thenAnswer(invocation -> {
            SuiviPagination suivi = invocation.getArgument(1);
            suivi.surPage(1, 3);
            jeton.annuler();
            suivi.surPage(2, 3);
            return new ReponseApi.Succes<>(List.of());
        });
        List<Progression> points = new ArrayList<>();
        ParticipationOrpheline orpheline =
                new ParticipationOrpheline(PARTICIPATION, "130711", "Z41", "2026-07-03T22:00:00+02:00", true);

        assertThatThrownBy(() -> service.reconstruire(orpheline, points::add, jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        assertThat(points)
                .as("chaque page lue fait avancer la barre, avec le total (page XX/YY)")
                .extracting(Progression::libelle)
                .anyMatch(libelle -> libelle.contains("page 1/3"));
        assertThat(passageDao.findAll())
                .as("annulé pendant le téléchargement : rien n'a été écrit")
                .isEmpty();
    }

    // --- Fixture ---------------------------------------------------------------------------------

    /// Deux participations sur le même point, chacune avec ses propres fichiers : le montage minimal qui
    /// ressemble à une vraie base. Sans lui, tout test de « quelle nuit est visée » passe par construction.
    private void bouchonnerDeuxParticipations() {
        when(client.mesParticipations())
                .thenReturn(new ReponseApi.Succes<>(
                        List.of(participation(PARTICIPATION), participation(PARTICIPATION_VOISINE))));
        bouchonnerUne(PARTICIPATION, SEQ_1, SEQ_2);
        bouchonnerUne(PARTICIPATION_VOISINE, SEQ_VOISINE_1, SEQ_VOISINE_2);
        when(importObservations.importer(anyLong(), any(), anyBoolean())).thenReturn("3 observation(s) importée(s).");
    }

    private void bouchonnerUne(String idParticipation, String premier, String second) {
        when(client.participation(idParticipation)).thenReturn(new ReponseApi.Succes<>(detail()));
        when(client.csvObservations(idParticipation)).thenReturn(new ReponseApi.Succes<>(Optional.empty()));
        when(client.donnees(eq(idParticipation), any()))
                .thenReturn(new ReponseApi.Succes<>(List.of(
                        new DonneeVigieChiro("d-" + premier, premier, List.of(observation(), observation())),
                        new DonneeVigieChiro("d-" + second, second, List.of(observation())))));
    }

    private List<SequenceDEcoute> sequencesDe(String idParticipation) {
        Long idPassage = Long.valueOf(liens.tous(LienVigieChiro.ENTITE_PASSAGE).entrySet().stream()
                .filter(entree -> entree.getValue().equals(idParticipation))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucun passage lié à " + idParticipation)));
        return sequenceDao.findBySession(
                sessionDao.trouverParPassage(idPassage).orElseThrow().id());
    }

    private void bouchonnerPlateforme() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));
        when(client.participation(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(detail()));
        // Pas de CSV exposé : ces tests exercent le REPLI par pagination donnees (#1565).
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(Optional.empty()));
        when(client.donnees(eq(PARTICIPATION), any()))
                .thenReturn(new ReponseApi.Succes<>(List.of(
                        new DonneeVigieChiro("d-1", SEQ_1, List.of(observation(), observation())),
                        new DonneeVigieChiro("d-2", SEQ_2, List.of(observation())))));
        when(importObservations.importer(anyLong(), any(), anyBoolean())).thenReturn("3 observation(s) importée(s).");
    }

    @Test
    @DisplayName("#1814 synchro : une orpheline située devient un passage archivé PORTANT son identité "
            + "(n° série réel, météo), toujours 0 séquence")
    void synchroniser_rapatrie_l_identite() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));
        when(client.participation(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(detailComplet()));
        // Dit explicitement ce que le montage veut dire : la plateforme RÉPOND, mais n'expose pas encore le
        // CSV (analyse non terminée). Sans ce bouchon, le mock rendait `null`, donc un ÉCHEC DE LECTURE - le
        // test affirmait « en attente d'analyse » en produisant tout autre chose (#2554, passe 1).
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(Optional.empty()));

        Optional<RapportSynchro> rapport = service.synchroniser(client);

        assertThat(rapport).hasValueSatisfying(compteRendu -> {
            // #2557 : la nuit apparaît, mais la plateforme n'expose pas encore son CSV. Le compteur ne la
            // revendique donc PAS comme récupérée - elle est annoncée pour ce qu'elle est, en attente.
            assertThat(compteRendu.nombre()).isZero();
            // #2655 : le TOTAL du tour est dit, pour que la part se lise sans recomposer de tête. Il n'est
            // exact que parce que les trois issues partitionnent les squelettes balayés.
            assertThat(compteRendu.enClair())
                    .startsWith("0 nuit(s) récupérée(s)")
                    .contains("sur 1")
                    .contains("1 en attente d'analyse");
            assertThat(compteRendu.libelle())
                    .as("le libellé sert de CLÉ (LIBELLE_SITES.equals, E2E) : il reste stable")
                    .isEqualTo("nuit(s) récupérée(s)");
        });
        List<Passage> passages = passageDao.findAll();
        assertThat(passages).hasSize(1);
        Passage passage = passages.get(0);
        assertThat(passage.statutWorkflow())
                .as("la participation existe sur la plateforme : le passage est déposé")
                .isEqualTo(StatutWorkflow.DEPOSE);
        assertThat(passage.idPoint()).isEqualTo(idPoint);
        assertThat(passage.annee()).isEqualTo(2026);
        assertThat(passage.idEnregistreur())
                .as("#1814 : l'enregistreur réel est rapatrié depuis le détail, plus « INCONNU »")
                .isEqualTo("1997632");
        assertThat(passage.donneesMeteo())
                .as("#1814 : la météo du détail est rapatriée dès la synchro")
                .isNotNull();

        SessionDEnregistrement session =
                sessionDao.trouverParPassage(passage.id()).orElseThrow();
        assertThat(session.volumeSequencesOctets())
                .as("la nuit naît sans audio : rien n'a jamais été importé ici")
                .isZero();
        assertThat(sequenceDao.findBySession(session.id()))
                .as("elle reste un squelette : 0 séquence, l'audio et les observations viennent à la reconstruction")
                .isEmpty();
        assertThat(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(passage.id())))
                .contains(PARTICIPATION);
        verify(importObservations, never()).importer(anyLong(), any(), anyBoolean());
    }

    @Test
    @DisplayName(
            "#1814 synchro : détail indisponible → repli sur le squelette nu (INCONNU), la nuit apparaît quand même")
    void synchroniser_detail_indisponible_repli() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));
        when(client.participation(PARTICIPATION)).thenReturn(new ReponseApi.Injoignable<>("timeout réseau"));

        Optional<RapportSynchro> rapport = service.synchroniser(client);

        assertThat(rapport)
                .as("la nuit est rapatriée malgré le détail manquant")
                .isPresent();
        List<Passage> passages = passageDao.findAll();
        assertThat(passages).hasSize(1);
        assertThat(passages.get(0).idEnregistreur())
                .as("détail indisponible : repli honnête sur « INCONNU », rattrapable à la reconstruction")
                .isEqualTo("INCONNU");
        assertThat(passages.get(0).donneesMeteo())
                .as("sans détail, pas de météo non plus")
                .isNull();
        assertThat(sequenceDao.findBySession(sessionDao
                        .trouverParPassage(passages.get(0).id())
                        .orElseThrow()
                        .id()))
                .as("repli = toujours un squelette, 0 séquence")
                .isEmpty();
    }

    @Test
    @DisplayName("#1814 synchro : chaque nuit reçoit SON détail (appariement préservé malgré les appels en parallèle)")
    void synchroniser_apparie_chaque_nuit_a_son_detail() {
        ParticipationVigieChiro premiere = new ParticipationVigieChiro(
                "p-1", "Z41", "2026-07-03T22:00:00+02:00", "Vigiechiro - Point Fixe-130711");
        ParticipationVigieChiro seconde = new ParticipationVigieChiro(
                "p-2", "Z41", "2026-07-05T22:00:00+02:00", "Vigiechiro - Point Fixe-130711");
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(premiere, seconde)));
        when(client.participation("p-1"))
                .thenReturn(new ReponseApi.Succes<>(detailAvecSerie("p-1", "2026-07-03T22:00:00+02:00", "1111111")));
        when(client.participation("p-2"))
                .thenReturn(new ReponseApi.Succes<>(detailAvecSerie("p-2", "2026-07-05T22:00:00+02:00", "2222222")));

        service.synchroniser(client);

        List<Passage> passages = passageDao.findAll();
        assertThat(passages).hasSize(2);
        for (Passage passage : passages) {
            String participation = liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(passage.id()))
                    .orElseThrow();
            assertThat(passage.idEnregistreur())
                    .as("la nuit %s doit porter SON n° de série, pas celui de l'autre", participation)
                    .isEqualTo("p-1".equals(participation) ? "1111111" : "2222222");
        }
        assertThat(passages)
                .as("deux nuits du même point la même année : numéros de passage successifs")
                .extracting(Passage::numeroPassage)
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("#1707 synchro : relancée, elle ne recrée rien et n'annonce rien (idempotence)")
    void synchroniser_idempotent() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));

        assertThat(service.synchroniser(client))
                .as("1re synchro : crée le squelette")
                .isPresent();
        Optional<RapportSynchro> seconde = service.synchroniser(client);

        assertThat(seconde).as("2e synchro : déjà rattaché, rien à annoncer").isEmpty();
        assertThat(passageDao.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("#2557 synchro : la nuit rapatriée reçoit AUSSI ses séquences et ses observations")
    void synchroniser_va_jusqu_au_contenu() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));
        when(client.participation(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(detailComplet()));
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(Optional.of(CSV_OBSERVATIONS)));
        when(importObservations.nomsSequencesCsv(CSV_OBSERVATIONS)).thenReturn(List.of(SEQ_1, SEQ_2));

        Optional<RapportSynchro> rapport = service.synchroniser(client);

        assertThat(rapport)
                .hasValueSatisfying(compteRendu -> assertThat(compteRendu.enClair())
                        .as("rien ne reste en suspens : la précision ne s'affiche pas")
                        .isEqualTo("1 nuit(s) récupérée(s)"));
        Long idPassage = passageDao.findAll().getFirst().id();
        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        assertThat(sequenceDao.findBySession(idSession))
                .as("la nuit n'est plus un squelette : elle porte les fichiers que le CSV annonce")
                .hasSize(2);
        verify(importObservations).importerCsv(idPassage, CSV_OBSERVATIONS, false);
    }

    @Test
    @DisplayName("#2557 : un squelette rapatrié AVANT ce correctif est complété au tour suivant, pas laissé à vie")
    void synchroniser_rattrape_un_squelette_preexistant() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));
        when(client.participation(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(detailComplet()));
        // 1er tour : la plateforme n'expose pas encore le CSV. La nuit existe, vide - exactement l'état
        // dans lequel #1707 laissait les bases, et que la création seule ne rattraperait JAMAIS puisqu'elle
        // saute toute nuit déjà rattachée (le piège de #1814).
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(Optional.empty()));
        service.synchroniser(client);
        Long idPassage = passageDao.findAll().getFirst().id();
        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        assertThat(sequenceDao.findBySession(idSession)).isEmpty();

        // 2e tour : l'analyse est terminée, le CSV est là.
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(Optional.of(CSV_OBSERVATIONS)));
        when(importObservations.nomsSequencesCsv(CSV_OBSERVATIONS)).thenReturn(List.of(SEQ_1, SEQ_2));

        Optional<RapportSynchro> second = service.synchroniser(client);

        assertThat(second)
                .hasValueSatisfying(
                        compteRendu -> assertThat(compteRendu.enClair()).isEqualTo("1 nuit(s) récupérée(s)"));
        assertThat(sequenceDao.findBySession(idSession))
                .as("la nuit préexistante a été complétée SANS être recréée")
                .hasSize(2);
        assertThat(passageDao.findAll())
                .extracting(Passage::id)
                .as("même passage : la synchro complète, elle ne remplace pas")
                .containsExactly(idPassage);
    }

    @Test
    @DisplayName("#2558 : renoncer TRAVERSE le contrat best-effort, au lieu d'être avalé comme une panne")
    void synchroniser_annulation_remonte() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));
        when(client.participation(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(detailComplet()));
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(Optional.of(CSV_OBSERVATIONS)));
        when(importObservations.nomsSequencesCsv(CSV_OBSERVATIONS)).thenReturn(List.of(SEQ_1, SEQ_2));
        JetonAnnulation renonce = new JetonAnnulation();
        renonce.annuler();

        // Avalée, l'annulation ferait enchaîner le rapprocheur suivant alors qu'on vient de demander
        // l'arrêt, et priverait la surface du moyen de distinguer « annulé » de « terminé ».
        assertThatThrownBy(() -> service.synchroniser(client, progres -> {}, renonce))
                .isInstanceOf(OperationAnnuleeException.class);
    }

    @Test
    @DisplayName("#2558 : une synchro interrompue est REPRENABLE - le tour suivant achève le travail")
    void synchroniser_annulation_reprenable() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));
        when(client.participation(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(detailComplet()));
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(Optional.of(CSV_OBSERVATIONS)));
        when(importObservations.nomsSequencesCsv(CSV_OBSERVATIONS)).thenReturn(List.of(SEQ_1, SEQ_2));
        JetonAnnulation renonce = new JetonAnnulation();
        renonce.annuler();

        assertThatThrownBy(() -> service.synchroniser(client, progres -> {}, renonce))
                .isInstanceOf(OperationAnnuleeException.class);
        Long idPassage = passageDao.findAll().getFirst().id();
        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        assertThat(sequenceDao.findBySession(idSession))
                .as("interrompue avant d'écrire : la nuit est restée un squelette, pas une nuit à moitié faite")
                .isEmpty();

        // C'est ce qui rend l'annulation acceptable comme garde-fou à la place d'une borne : rien n'est
        // perdu, la synchro suivante reprend le travail restant.
        Optional<RapportSynchro> reprise = service.synchroniser(client);

        assertThat(reprise)
                .hasValueSatisfying(
                        compteRendu -> assertThat(compteRendu.enClair()).isEqualTo("1 nuit(s) récupérée(s)"));
        assertThat(sequenceDao.findBySession(idSession)).hasSize(2);
    }

    @Test
    @DisplayName("#2554 P1-C : une nuit qu'on n'a PAS PU lire n'est pas annoncée « en attente d'analyse »")
    void synchroniser_distingue_echec_de_lecture_et_attente() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));
        when(client.participation(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(detailComplet()));
        // La plateforme est injoignable au moment de lire le CSV : l'analyse est peut-être terminée depuis
        // des jours. Annoncer « en attente d'analyse » serait affirmer une cause qu'on n'a pas constatée,
        // et orienter vers l'attente là où il faut réessayer.
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Injoignable<>("délai dépassé"));

        Optional<RapportSynchro> rapport = service.synchroniser(client);

        assertThat(rapport)
                .hasValueSatisfying(compteRendu -> assertThat(compteRendu.enClair())
                        .contains("1 non récupérée(s), à réessayer")
                        .doesNotContain("en attente d'analyse"));
    }

    @Test
    @DisplayName("#1707 synchro : une participation dont le point n'est pas encore local est ignorée")
    void synchroniser_ignore_point_non_local() {
        when(client.mesParticipations())
                .thenReturn(new ReponseApi.Succes<>(List.of(new ParticipationVigieChiro(
                        "autre", "Z99", "2026-07-03T22:00:00+02:00", "Vigiechiro - Point Fixe-130711"))));

        assertThat(service.synchroniser(client))
                .as("point Z99 inconnu du référentiel local : pas encore situable")
                .isEmpty();
        assertThat(passageDao.findAll()).isEmpty();
    }

    @Test
    @DisplayName("#1707 synchro : hors connexion, best-effort silencieux (Optional vide, aucune exception)")
    void synchroniser_hors_connexion_silencieux() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.NonConnecte<>());

        assertThat(service.synchroniser(client)).isEmpty();
        assertThat(passageDao.findAll()).isEmpty();
    }

    @Test
    @DisplayName("#1710 : reconstruire une nuit déjà rapatriée en squelette la remplace par un passage complet")
    void reconstruire_hydrate_un_squelette() {
        // La synchro (#1707) rapatrie d'abord la nuit en SQUELETTE : rattachée, archivée, 0 séquence.
        bouchonnerPlateforme();
        service.synchroniser(client);
        assertThat(passageDao.findAll())
                .as("un squelette a été créé par la synchro")
                .hasSize(1);
        Long idSquelette = passageDao.findAll().getFirst().id();
        assertThat(sequenceDao.findBySession(
                        sessionDao.trouverParPassage(idSquelette).orElseThrow().id()))
                .as("le squelette n'a aucune séquence")
                .isEmpty();

        // Reconstruire : le squelette est REMPLACÉ par un passage complet (séquences + observations).
        RapportReconstruction rapport = service.reconstruire(PARTICIPATION);

        assertThat(rapport.sequencesRecreees()).isEqualTo(2);
        assertThat(rapport.observationsImportees()).isEqualTo(3);
        assertThat(passageDao.findAll())
                .as("toujours un seul passage : le squelette est remplacé, pas doublé")
                .hasSize(1);
        assertThat(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(rapport.idPassage())))
                .as("la participation est rattachée au passage reconstruit (lien reposé)")
                .contains(PARTICIPATION);
    }

    @Test
    @DisplayName("#2639 : une reconstruction dit où elle en est, et sa barre va jusqu'au bout")
    void reconstruire_dit_ou_elle_en_est() {
        bouchonnerPlateforme();
        List<Progression> points = new ArrayList<>();

        service.reconstruire(PARTICIPATION, points::add, JetonAnnulation.neutre());

        assertThat(points)
                .extracting(Progression::libelle)
                .contains("Lecture de la participation…", "Import des observations…", "Terminé.");
        assertThat(points).extracting(Progression::fraction).isSorted().endsWith(1.0);
    }

    @Test
    @DisplayName("#2639 : un jeton déjà annulé arrête la reconstruction avant la lecture du détail")
    void reconstruire_annulee_avant_la_lecture() {
        bouchonnerPlateforme();
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler();

        assertThatThrownBy(() -> service.reconstruire(PARTICIPATION, progres -> {}, jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        verify(client, never()).participation(PARTICIPATION);
        assertThat(passageDao.findAll()).isEmpty();
    }

    @Test
    @DisplayName("#2639 : annuler pendant les lectures distantes arrête AVANT la première écriture")
    void reconstruire_annulee_avant_la_premiere_ecriture() {
        bouchonnerPlateforme();
        JetonAnnulation jeton = new JetonAnnulation();
        // L'utilisateur clique « Annuler » pendant le rapatriement des observations, c'est-à-dire après
        // le contrôle d'entrée et avant celui qui garde l'écriture. Sans ce second contrôle, la
        // reconstruction irait au bout et il n'y aurait rien à compenser : elle aurait réussi.
        when(client.donnees(eq(PARTICIPATION), any())).thenAnswer(appel -> {
            jeton.annuler();
            return new ReponseApi.Succes<>(List.of(
                    new DonneeVigieChiro("d-1", SEQ_1, List.of(observation(), observation())),
                    new DonneeVigieChiro("d-2", SEQ_2, List.of(observation()))));
        });

        List<Progression> points = new ArrayList<>();
        assertThatThrownBy(() -> service.reconstruire(PARTICIPATION, points::add, jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        assertThat(passageDao.findAll()).as("rien n'a été écrit").isEmpty();
        assertThat(liens.tous(LienVigieChiro.ENTITE_PASSAGE)).isEmpty();
        // Une base vide ne prouve PAS qu'on n'a pas écrit : la compensation nettoie aussi bien. Ce qui
        // distingue « on n'a pas commencé » de « on a écrit puis défait », ce sont les étapes annoncées.
        // Sans cette assertion, retirer le contrôle d'annulation d'avant l'écriture laissait la suite
        // verte (mutant survivant, #2639).
        assertThat(points)
                .extracting(Progression::libelle)
                .doesNotContain("Création des séquences…", "Import des observations…");
    }

    @Test
    @DisplayName("#2639 : reconstruire une nuit dont le point est inconnu ici le DIT, et n'écrit rien")
    void reconstruire_refuse_un_point_inconnu() {
        bouchonnerPlateforme();
        ParticipationOrpheline ailleurs =
                new ParticipationOrpheline(PARTICIPATION, "999999", "Z9", "2026-07-03T22:00:00+02:00", false);

        assertThatThrownBy(() -> service.reconstruire(ailleurs, progres -> {}, JetonAnnulation.neutre()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("n'existe pas localement")
                .hasMessageContaining("Créez d'abord le site et le point");

        assertThat(passageDao.findAll()).isEmpty();
    }

    @Test
    @DisplayName("#2639 : une participation sans date exploitable le DIT au lieu d'inventer une nuit")
    void reconstruire_refuse_une_date_illisible() {
        bouchonnerPlateforme();
        when(client.participation(PARTICIPATION))
                .thenReturn(new ReponseApi.Succes<>(new ParticipationDetail(
                        PARTICIPATION,
                        "etag-1",
                        "Z41",
                        "pas une date",
                        "pas une date non plus",
                        null,
                        Map.of(),
                        Traitement.absent())));

        assertThatThrownBy(() -> service.reconstruire(PARTICIPATION, progres -> {}, JetonAnnulation.neutre()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("impossible de dater la nuit");

        assertThat(passageDao.findAll()).isEmpty();
    }

    @Test
    @DisplayName("#2639 : ce rapprocheur est DÉPENDANT - il passe après ceux qui posent sites et points")
    void phase_dependante() {
        // Pas une formalité : rapatrier une nuit suppose que son point d'écoute existe déjà localement.
        // Basculer cette phase ferait échouer la synchro d'un compte neuf, en silence et à l'exécution.
        assertThat(service.phase()).isEqualTo(RapprochementVigieChiro.Phase.DEPENDANTE);
    }

    @Test
    @DisplayName("#2639 : la barre du lot vaut k / N, elle ne saute pas au bout dès la première nuit")
    void reconstruire_tout_avance_par_fractions() {
        ServiceReconstructionPassages espion = spy(service);
        ParticipationOrpheline n1 =
                new ParticipationOrpheline("p1", "130711", "Z41", "2026-07-03T22:00:00+02:00", true);
        ParticipationOrpheline n2 =
                new ParticipationOrpheline("p2", "130711", "Z41", "2026-07-04T22:00:00+02:00", true);
        doReturn(new RapportReconstruction(1L, 1, 1, RapportReconstruction.lacunesConnues()))
                .when(espion)
                .reconstruire(any(ParticipationOrpheline.class), any(), any());
        List<Progression> global = new ArrayList<>();

        espion.reconstruireTout(List.of(n1, n2), global::add, progression -> {}, issue -> {}, JetonAnnulation.neutre());

        // Les libellés étaient déjà tenus ; les FRACTIONS ne l'étaient pas. Une barre qui annonce 100 %
        // sur la première des deux nuits est pire qu'une barre absente : elle promet une fin qui ne vient
        // pas.
        assertThat(global).extracting(Progression::fraction).containsExactly(0.0, 0.5, 1.0);
    }

    @Test
    @DisplayName("#2639 : la nuit créée par la synchro prend sa fin du détail, pas son début")
    void synchroniser_pose_la_fin_de_nuit_du_detail() {
        bouchonnerPlateforme();

        service.synchroniser(client);

        // C'est l'un des quatre éléments d'identité que #1814 est allé chercher : sans lui, la nuit
        // s'afficherait comme durant zéro minute.
        //
        // L'heure attendue se DÉRIVE, elle ne se code pas en dur : le détail porte un décalage (+02:00) et
        // l'application le convertit dans le fuseau de la machine. Une valeur murale écrite ici passerait à
        // Paris et échouerait sur un runner en UTC - vécu.
        LocalDateTime finDuDetail =
                ParticipationOrpheline.horodatage("2026-07-04T06:30:00+02:00").orElseThrow();
        assertThat(passageDao.findAll()).singleElement().satisfies(nuit -> {
            assertThat(nuit.heureFin())
                    .startsWith(finDuDetail.toLocalTime().toString().substring(0, 5));
            assertThat(nuit.heureFin())
                    .as("la fin vient du détail, pas du début recopié")
                    .isNotEqualTo(nuit.heureDebut());
        });
    }

    @Test
    @DisplayName("#2638 : compléter une participation touche SA nuit, jamais celle d'à côté")
    void completer_designe_la_nuit_demandee() {
        bouchonnerDeuxParticipations();
        service.synchroniser(client); // deux squelettes, deux liens

        service.reconstruire(PARTICIPATION_VOISINE);

        assertThat(sequencesDe(PARTICIPATION_VOISINE))
                .as("la nuit demandée a reçu son contenu")
                .hasSize(2);
        assertThat(sequencesDe(PARTICIPATION))
                .as("la voisine n'a pas été touchée")
                .isEmpty();
    }

    @Test
    @DisplayName("#2638 : et dans l'autre sens, pour que l'ordre des liens ne décide de rien")
    void completer_designe_la_nuit_demandee_dans_l_autre_sens() {
        bouchonnerDeuxParticipations();
        service.synchroniser(client);

        service.reconstruire(PARTICIPATION);

        // Les deux sens, délibérément. Le passage est retrouvé en filtrant la table des liens : si le
        // filtre venait à ne plus filtrer, `findFirst` rendrait une entrée arbitraire - déterministe pour
        // une base donnée, donc un test unique pourrait tomber du bon côté par chance et ne rien prouver.
        assertThat(sequencesDe(PARTICIPATION)).hasSize(2);
        assertThat(sequencesDe(PARTICIPATION_VOISINE)).isEmpty();
    }

    @Test
    @DisplayName("#2554 : compléter un squelette GARDE son identifiant et ses saisies manuelles")
    void completer_preserve_le_passage_et_ses_saisies() {
        bouchonnerPlateforme();
        service.synchroniser(client);
        Long idAvant = passageDao.findAll().getFirst().id();

        // L'utilisateur corrige à la main les heures de la nuit : c'est précisément ce que #1892 lui permet
        // sur une nuit rapatriée, dont rien n'atteste les bornes.
        Passage saisi = passageDao.findById(idAvant).orElseThrow();
        passageDao.update(new Passage(
                saisi.id(),
                saisi.numeroPassage(),
                saisi.annee(),
                saisi.dateEnregistrement(),
                "21:07",
                "05:42",
                saisi.parametresAcquisition(),
                saisi.statutWorkflow(),
                saisi.verdictVerification(),
                saisi.commentaire(),
                saisi.donneesMeteo(),
                saisi.deposeLe(),
                saisi.idPoint(),
                saisi.idEnregistreur(),
                saisi.idCampagne()));

        List<Progression> points = new ArrayList<>();
        RapportReconstruction rapport = service.reconstruire(PARTICIPATION, points::add, JetonAnnulation.neutre());

        // Le geste s'appelle « Compléter » : il ajoute le contenu, il ne refait pas la nuit. Un
        // delete + recreate rendait ici les bornes de la plateforme et effaçait la correction, sous un
        // identifiant neuf - donc sous l'écran éventuellement ouvert sur l'ancien.
        assertThat(rapport.idPassage())
                .as("le passage survit : même identifiant")
                .isEqualTo(idAvant);
        assertThat(passageDao.findById(idAvant).orElseThrow()).satisfies(apres -> {
            assertThat(apres.heureDebut()).isEqualTo("21:07");
            assertThat(apres.heureFin()).isEqualTo("05:42");
        });
        assertThat(rapport.sequencesRecreees()).isEqualTo(2);
        assertThat(rapport.observationsImportees()).isEqualTo(3);
        // La barre va jusqu'au bout. Ici l'hydratation est TOUTE l'opération : ses fractions passent, et
        // c'est le service qui pose le point final - sans quoi la modale resterait à 85 % sur un succès.
        assertThat(points).extracting(Progression::fraction).isSorted().endsWith(1.0);
    }

    @Test
    @DisplayName("#1710 : une nuit rapatriée en squelette reste listée « à reconstruire » (à hydrater)")
    void orphelines_inclut_un_squelette() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));
        service.synchroniser(client); // crée le squelette (rattache la participation)

        assertThat(service.orphelines())
                .as("le squelette est à hydrater : il reste listé malgré son rattachement")
                .singleElement()
                .satisfies(orpheline -> assertThat(orpheline.idParticipation()).isEqualTo(PARTICIPATION));
    }

    @Test
    @DisplayName("#1710 : compléter une nuit qui a déjà son contenu est refusé")
    void reconstruire_refuse_un_passage_deja_hydrate() {
        bouchonnerPlateforme();
        service.reconstruire(PARTICIPATION); // 1re reconstruction : passage complet (séquences + observations)

        assertThatThrownBy(() -> service.reconstruire(PARTICIPATION))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("qui a son contenu");
    }

    @Test
    @DisplayName("#1708 import groupé : la boucle best-effort vit au service - reconstruites + ignorée, bilan + issues")
    void reconstruire_tout_boucle_best_effort() {
        ServiceReconstructionPassages espion = spy(service);
        ParticipationOrpheline n1 =
                new ParticipationOrpheline("p1", "130711", "Z41", "2026-07-03T22:00:00+02:00", true);
        ParticipationOrpheline n2 =
                new ParticipationOrpheline("p2", "130711", "Z41", "2026-07-04T22:00:00+02:00", true);
        ParticipationOrpheline ko =
                new ParticipationOrpheline("ko", "999999", "Z9", "2026-07-05T22:00:00+02:00", false);
        doReturn(new RapportReconstruction(1L, 10, 20, RapportReconstruction.lacunesConnues()))
                .when(espion)
                .reconstruire(eq(n1), any(), any());
        doReturn(new RapportReconstruction(2L, 5, 8, RapportReconstruction.lacunesConnues()))
                .when(espion)
                .reconstruire(eq(n2), any(), any());
        doThrow(new RegleMetierException("Le point d'écoute n'existe pas localement."))
                .when(espion)
                .reconstruire(eq(ko), any(), any());

        List<Progression> global = new ArrayList<>();
        List<ServiceReconstructionPassages.IssueNuit> issues = new ArrayList<>();
        ServiceReconstructionPassages.BilanReconstructionGroupe bilan = espion.reconstruireTout(
                List.of(n1, n2, ko), global::add, progression -> {}, issues::add, JetonAnnulation.neutre());

        assertThat(bilan.reussies()).isEqualTo(2);
        assertThat(bilan.ignorees()).isEqualTo(1);
        assertThat(bilan.sequences()).isEqualTo(15);
        assertThat(bilan.observations()).isEqualTo(28);
        assertThat(global)
                .as("la progression globale annonce chaque nuit puis « Terminé »")
                .extracting(Progression::libelle)
                .contains("Nuit 1 / 3…", "Nuit 3 / 3…", "Terminé.");
        assertThat(issues).hasSize(3);
        assertThat(issues.get(0)).isInstanceOf(ServiceReconstructionPassages.IssueNuit.Reconstruite.class);
        assertThat(issues.get(2))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                        ServiceReconstructionPassages.IssueNuit.Ignoree.class))
                .satisfies(ignoree -> assertThat(ignoree.cause()).contains("n'existe pas"));
    }

    @Test
    @DisplayName("#1708 import groupé : annulé avant la première nuit, aucune n'est touchée")
    void reconstruire_tout_annulation_avant_la_premiere() {
        ServiceReconstructionPassages espion = spy(service);
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler();
        ParticipationOrpheline n1 =
                new ParticipationOrpheline("p1", "130711", "Z41", "2026-07-03T22:00:00+02:00", true);

        ServiceReconstructionPassages.BilanReconstructionGroupe bilan =
                espion.reconstruireTout(List.of(n1), progression -> {}, progression -> {}, issue -> {}, jeton);

        verify(espion, never()).reconstruire(any(ParticipationOrpheline.class), any(), any());
        assertThat(bilan.reussies()).isZero();
        assertThat(bilan.interrompu()).isTrue();
    }

    @Test
    @DisplayName("Annulé APRÈS la première nuit : le bilan dit qu'elle a été complétée, il ne se tait pas")
    void reconstruire_tout_annulation_restitue_ce_qui_est_fait() {
        // Le lot levait : la modale affichait « aucune nuit n'a été complétée » alors qu'une l'était,
        // et ne rechargeait pas sa liste - la nuit déjà complétée restait offerte à la complétion.
        ServiceReconstructionPassages espion = spy(service);
        JetonAnnulation jeton = new JetonAnnulation();
        ParticipationOrpheline n1 =
                new ParticipationOrpheline("p1", "130711", "Z41", "2026-07-03T22:00:00+02:00", true);
        ParticipationOrpheline n2 =
                new ParticipationOrpheline("p2", "130711", "Z41", "2026-07-04T22:00:00+02:00", true);
        doAnswer(invocation -> {
                    jeton.annuler(); // l'utilisateur renonce pendant la première nuit, qui va au bout
                    return new RapportReconstruction(1L, 10, 20, RapportReconstruction.lacunesConnues());
                })
                .when(espion)
                .reconstruire(eq(n1), any(), any());

        ServiceReconstructionPassages.BilanReconstructionGroupe bilan =
                espion.reconstruireTout(List.of(n1, n2), progression -> {}, progression -> {}, issue -> {}, jeton);

        assertThat(bilan.reussies())
                .as("la première nuit est complétée pour de bon : le bilan doit le dire")
                .isEqualTo(1);
        assertThat(bilan.interrompu()).isTrue();
        verify(espion, never()).reconstruire(eq(n2), any(), any());
    }

    private static ParticipationVigieChiro participation(String id) {
        return new ParticipationVigieChiro(id, "Z41", "2026-07-03T22:00:00+02:00", "Vigiechiro - Point Fixe-130711");
    }

    /// Détail minimal portant un n° de série donné : pour vérifier l'appariement nuit → détail.
    private static ParticipationDetail detailAvecSerie(String id, String debut, String serie) {
        return new ParticipationDetail(
                id,
                "etag-" + id,
                "Z41",
                debut,
                debut,
                null,
                Map.of("detecteur_enregistreur_numserie", serie),
                Traitement.absent());
    }

    private static ParticipationDetail detail() {
        return new ParticipationDetail(
                PARTICIPATION,
                "etag-1",
                "Z41",
                "2026-07-03T22:00:00+02:00",
                "2026-07-04T06:30:00+02:00",
                null,
                Map.of("detecteur_enregistreur_numserie", "1925492"),
                Traitement.absent());
    }

    /// Détail complet façon formulaire web : n° de série sous la clé **canonique**, météo et micro renseignés.
    private static ParticipationDetail detailComplet() {
        return new ParticipationDetail(
                PARTICIPATION,
                "etag-1",
                "Z41",
                "2026-07-03T22:00:00+02:00",
                "2026-07-04T06:30:00+02:00",
                new MeteoDepot("FAIBLE", "0-25"),
                Map.of(
                        "detecteur_enregistreur_numero_serie", "1997632",
                        "micro0_type", "ICS",
                        "micro0_position", "CANOPEE",
                        "micro0_hauteur", "4"),
                Traitement.absent());
    }

    private static fr.univ_amu.iut.commun.api.ObservationVigieChiro observation() {
        return new fr.univ_amu.iut.commun.api.ObservationVigieChiro(
                0, "Pippip", 0.9, 45.0, 0.20, 0.32, null, null, null, null, null, List.of());
    }
}

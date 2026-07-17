package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
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
    private static final String SEQ_1 = "Car130711-2026-Pass1-Z41-PaRec_20260703_220529_000";
    private static final String SEQ_2 = "Car130711-2026-Pass1-Z41-PaRec_20260703_220534_000";
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
                new HorlogeFigee(MAINTENANT));
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
        assertThat(session.archivee())
                .as("le passage naît archivé : aucun fichier n'a jamais été importé ici")
                .isTrue();
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
    @DisplayName("Feature « Import VigieChiro » désactivée : refus explicite, aucun passage créé")
    void import_desactive_refuse() {
        bouchonnerPlateforme();
        ServiceReconstructionPassages sansImport = new ServiceReconstructionPassages(
                source,
                client,
                (carre, point) -> Optional.of(idPoint),
                Optional.empty(),
                new Workspace(dossier),
                new HorlogeFigee(MAINTENANT));

        assertThatThrownBy(() -> sansImport.reconstruire(PARTICIPATION))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Import VigieChiro");
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
    @DisplayName("#1707 synchro : une orpheline située devient un squelette archivé (INCONNU, 0 séquence), rattaché")
    void synchroniser_cree_un_squelette() {
        when(client.mesParticipations()).thenReturn(new ReponseApi.Succes<>(List.of(participation(PARTICIPATION))));

        Optional<RapportSynchro> rapport = service.synchroniser(client);

        assertThat(rapport).hasValueSatisfying(compteRendu -> {
            assertThat(compteRendu.nombre()).isEqualTo(1);
            assertThat(compteRendu.enClair()).isEqualTo("1 passage(s) rapatrié(s)");
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
                .as("aucun détail n'est téléchargé pour un squelette : enregistreur honnêtement « inconnu »")
                .isEqualTo("INCONNU");
        assertThat(passage.donneesMeteo())
                .as("le squelette ne porte pas de météo : elle viendra à l'hydratation (#1710)")
                .isNull();

        SessionDEnregistrement session =
                sessionDao.trouverParPassage(passage.id()).orElseThrow();
        assertThat(session.archivee())
                .as("un squelette naît archivé : rien n'a jamais été importé ici")
                .isTrue();
        assertThat(sequenceDao.findBySession(session.id()))
                .as("un squelette n'a pas encore de séquence : l'hydratation les créera")
                .isEmpty();
        assertThat(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(passage.id())))
                .contains(PARTICIPATION);
        verify(importObservations, never()).importer(anyLong(), any(), anyBoolean());
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
    @DisplayName("#1710 : reconstruire une nuit déjà reconstruite (avec séquences) est refusé")
    void reconstruire_refuse_un_passage_deja_hydrate() {
        bouchonnerPlateforme();
        service.reconstruire(PARTICIPATION); // 1re reconstruction : passage complet (séquences + observations)

        assertThatThrownBy(() -> service.reconstruire(PARTICIPATION))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("déjà reconstruit");
    }

    private static ParticipationVigieChiro participation(String id) {
        return new ParticipationVigieChiro(id, "Z41", "2026-07-03T22:00:00+02:00", "Vigiechiro - Point Fixe-130711");
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

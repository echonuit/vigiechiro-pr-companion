package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Archivage d'un passage (#1300) sur une base SQLite jetable et de vrais fichiers sous `@TempDir` :
/// l'audio part (séquences + bruts), tout le reste survit (CSV Tadarida, journal, relevé, lignes en
/// base), le geste est marqué, les empreintes sont capturées in extremis, et un passage non déposé
/// est refusé net.
class ServiceArchivagePassageTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";
    private static final Prefixe PREFIXE = new Prefixe("040962", 2026, 1, "A1");
    private static final String NOM_ORIGINAL = PREFIXE.nommerOriginal("PaRecPR" + SERIE + "_20260620_213000.wav");
    private static final LocalDateTime GESTE = LocalDateTime.of(2026, 7, 13, 18, 30, 0);
    private static final int NB_SEQUENCES = 3;

    @TempDir
    Path dossier;

    private Path racineSession;
    private Path transformes;
    private PassageDao passageDao;
    private SessionDao sessionDao;
    private SequenceDao sequenceDao;
    private EnregistrementOriginalDao originalDao;
    private ResultatsIdentificationDao resultatsDao;
    private ServiceDisponibiliteAudio disponibilite;
    private ServiceArchivagePassage service;
    private Long idPoint;
    private Path csvTadarida;
    private Path journal;
    private Path releve;

    private SourceDeDonnees source;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", ID_USER));
        idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, null, null));
        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        sequenceDao = new SequenceDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        resultatsDao = new ResultatsIdentificationDao(source);
        racineSession = dossier.resolve(PREFIXE.nomDossierSession());
        transformes = racineSession.resolve("transformes");
        Workspace workspace = new Workspace(dossier);
        disponibilite = new ServiceDisponibiliteAudio(sessionDao, sequenceDao, workspace);
        service = new ServiceArchivagePassage(
                passageDao,
                sessionDao,
                sequenceDao,
                new BackfillEmpreintes(sequenceDao, originalDao),
                new ServicePurgeOriginaux(workspace),
                disponibilite,
                new HorlogeFigee(GESTE));
    }

    @Test
    @DisplayName("Archiver libère l'espace annoncé et le passage devient ABSENTE")
    void archiver_libere_l_espace_annonce() throws IOException {
        Long idPassage = creerPassageComplet(StatutWorkflow.DEPOSE);
        long annonce = service.volumeRecuperable(idPassage);
        assertThat(annonce).isPositive();
        assertThat(disponibilite.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.COMPLETE);

        ServiceArchivagePassage.BilanArchivage bilan = service.archiver(idPassage);

        assertThat(bilan.octetsLiberes()).isEqualTo(annonce);
        assertThat(disponibilite.disponibilite(idPassage))
                .as("le cache de disponibilité a été invalidé par l'archivage")
                .isEqualTo(DisponibiliteAudio.ABSENTE);
        assertThat(Files.list(transformes).filter(f -> f.toString().endsWith(".wav")))
                .as("plus aucun WAV dans transformes/")
                .isEmpty();
        assertThat(racineSession.resolve("bruts")).doesNotExist();
    }

    @Test
    @DisplayName("Observations, résultats Tadarida (CSV), journal et relevé climatique survivent")
    void donnees_de_validation_survivent() throws IOException {
        Long idPassage = creerPassageComplet(StatutWorkflow.DEPOSE);

        service.archiver(idPassage);

        assertThat(csvTadarida)
                .as("le CSV Tadarida n'est pas une séquence : préservé")
                .exists();
        assertThat(journal).exists();
        assertThat(releve).exists();
        assertThat(resultatsDao.findByPassage(idPassage)).isPresent();
        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        assertThat(sequenceDao.findBySession(idSession))
                .as("les lignes de séquences survivent : ce sont elles qu'on rebranchera (#1302)")
                .hasSize(NB_SEQUENCES);
    }

    @Test
    @DisplayName("Le geste est marqué (archived_at posé à l'horloge), distinguable d'une disparition subie")
    void geste_marque() throws IOException {
        Long idPassage = creerPassageComplet(StatutWorkflow.DEPOSE);

        service.archiver(idPassage);

        SessionDEnregistrement session = sessionDao.trouverParPassage(idPassage).orElseThrow();
        assertThat(session.archivee()).isTrue();
        assertThat(session.horodatageArchivage()).isEqualTo(GESTE);
    }

    @Test
    @DisplayName("Les empreintes (#1299) sont capturées AVANT la purge : réactivation par empreinte possible")
    void empreintes_capturees_avant_purge() throws IOException {
        Long idPassage = creerPassageComplet(StatutWorkflow.DEPOSE);
        assertThat(service.sequencesSansEmpreinte(idPassage)).isEqualTo(NB_SEQUENCES);

        ServiceArchivagePassage.BilanArchivage bilan = service.archiver(idPassage);

        assertThat(bilan.empreintesCapturees()).isEqualTo(NB_SEQUENCES);
        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        assertThat(sequenceDao.findBySession(idSession))
                .as("les fichiers sont partis mais leur identité est en base")
                .allSatisfy(s -> assertThat(s.empreinte()).isNotNull());
    }

    @Test
    @DisplayName("Un passage non déposé est refusé net (perdre l'audio avant l'analyse serveur)")
    void passage_non_depose_refuse() throws IOException {
        Long idPassage = creerPassageComplet(StatutWorkflow.VERIFIE);

        assertThatThrownBy(() -> service.archiver(idPassage))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("déposé");
        assertThat(Files.list(transformes).filter(f -> f.toString().endsWith(".wav")))
                .as("rien n'a été supprimé")
                .hasSize(NB_SEQUENCES);
    }

    @Test
    @DisplayName("Idempotent : ré-archiver ne libère plus rien et conserve le marqueur")
    void idempotent() throws IOException {
        Long idPassage = creerPassageComplet(StatutWorkflow.DEPOSE);
        service.archiver(idPassage);

        ServiceArchivagePassage.BilanArchivage second = service.archiver(idPassage);

        assertThat(second.octetsLiberes()).isZero();
        assertThat(sessionDao.trouverParPassage(idPassage).orElseThrow().archivee())
                .isTrue();
    }

    // --- Fixture ---------------------------------------------------------------------------------

    /// Passage complet sur disque et en base : bruts (1 original), transformes (3 séquences WAV +
    /// le CSV Tadarida), journal du capteur, relevé climatique, jeu de résultats.
    private Long creerPassageComplet(StatutWorkflow statut) throws IOException {
        Long idPassage = passageDao
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        statut,
                        Verdict.OK,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
        Long idSession = sessionDao
                .insert(new SessionDEnregistrement(null, racineSession.toString(), 4096L, 4096L, idPassage))
                .id();
        Path bruts = Files.createDirectories(racineSession.resolve("bruts"));
        Files.createDirectories(transformes);
        Path original = Files.write(bruts.resolve(NOM_ORIGINAL), new byte[4_096]);
        Long idOriginal = originalDao
                .insert(new EnregistrementOriginal(
                        null, NOM_ORIGINAL, original.toString(), 15.0, 384_000, null, idSession))
                .id();
        for (int index = 0; index < NB_SEQUENCES; index++) {
            String nom = PREFIXE.nommerSequence(NOM_ORIGINAL, index);
            Path fichier = Files.write(transformes.resolve(nom), new byte[1_024]);
            sequenceDao.insert(new SequenceDEcoute(
                    null, nom, idOriginal, index, index * 5.0, 5.0, fichier.toString(), true, idSession));
        }
        csvTadarida = Files.write(transformes.resolve("observations.csv"), new byte[128]);
        resultatsDao.insert(
                new ResultatsIdentification(null, csvTadarida.toString(), "tadarida", "2026-07-01", idPassage));
        journal = Files.write(racineSession.resolve("LogPR" + SERIE + ".txt"), new byte[64]);
        new JournalDuCapteurDao(source).insert(new JournalDuCapteur(null, journal.toString(), null, null, idSession));
        releve = Files.write(racineSession.resolve("PaRecPR" + SERIE + "_THLog.csv"), new byte[64]);
        new ReleveClimatiqueDao(source).insert(new ReleveClimatique(null, releve.toString(), null, idSession));
        return idPassage;
    }
}

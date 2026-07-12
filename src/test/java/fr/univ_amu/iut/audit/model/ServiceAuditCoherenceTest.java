package fr.univ_amu.iut.audit.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ReleveClimatique;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Audit de cohérence disque / base sur une base SQLite jetable et de **vrais fichiers** sous
/// `@TempDir`. Les `file_path` sont stockés en **absolu** (comme en production : `Path.of(cheminFichier())`
/// est résolu tel quel). Chaque test construit son propre graphe site -> point -> passage -> session.
class ServiceAuditCoherenceTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";
    private static final Prefixe PREFIXE = new Prefixe("040962", 2026, 1, "A1");
    private static final String NOM_ORIGINAL = PREFIXE.nommerOriginal("PaRecPR" + SERIE + "_20260620_213000.wav");

    @TempDir
    Path dossier;

    private Path racineSession;
    private ServiceAuditCoherence service;
    private PassageDao passageDao;
    private SessionDao sessionDao;
    private EnregistrementOriginalDao originalDao;
    private SequenceDao sequenceDao;
    private JournalDuCapteurDao journalDao;
    private ReleveClimatiqueDao releveDao;
    private DepotUniteDao depotDao;
    private Long idPoint;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        Site site = siteDao.insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", ID_USER));
        idPoint = pointDao.insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));

        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        sequenceDao = new SequenceDao(source);
        journalDao = new JournalDuCapteurDao(source);
        releveDao = new ReleveClimatiqueDao(source);
        depotDao = new DepotUniteDao(source);

        racineSession = dossier.resolve(PREFIXE.nomDossierSession());
        service = new ServiceAuditCoherence(source, new Workspace(dossier));
    }

    @Test
    @DisplayName("Session entièrement cohérente : rapport sain, aucun écart")
    void session_coherente_rapport_sain() throws IOException {
        Long idPassage = creerSessionCoherente(1);

        assertThat(service.auditerPassage(idPassage).sain()).isTrue();
    }

    @Test
    @DisplayName("Séquence manquante sur disque : une erreur DISQUE_MANQUANT")
    void sequence_manquante_erreur() throws IOException {
        Long idPassage = creerSessionCoherente(1);
        Files.delete(racineSession.resolve("transformes").resolve(PREFIXE.nommerSequence(NOM_ORIGINAL, 0)));

        List<ConstatAudit> constats = service.auditerPassage(idPassage).constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(SeveriteConstat.ERREUR);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.DISQUE_MANQUANT);
        });
    }

    @Test
    @DisplayName("Fichier parasite dans transformes/ : orphelin ; rien sous depot/")
    void fichier_parasite_orphelin_mais_pas_depot() throws IOException {
        Long idPassage = creerSessionCoherente(1);
        Files.write(racineSession.resolve("transformes").resolve("intrus.wav"), new byte[8]);
        Files.createDirectories(racineSession.resolve("depot"));
        Files.write(racineSession.resolve("depot").resolve("Car040962-2026-Pass1-A1-1.zip"), new byte[8]);

        List<ConstatAudit> constats = service.auditerPassage(idPassage).constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(SeveriteConstat.AVERTISSEMENT);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.DISQUE_ORPHELIN);
            assertThat(c.cible()).endsWith("intrus.wav");
        });
    }

    @Test
    @DisplayName("Nom de fichier au mauvais préfixe : PREFIXE_NON_CONFORME")
    void prefixe_non_conforme() throws IOException {
        Long idPassage = creerPassage(1);
        Long idSession = creerSession(idPassage, 4096L);
        Path bruts = Files.createDirectories(racineSession.resolve("bruts"));
        Path fichier = Files.write(bruts.resolve("MAUVAIS_NOM.wav"), new byte[16]);
        originalDao.insert(new EnregistrementOriginal(
                null, "MAUVAIS_NOM.wav", fichier.toString(), 12.0, 384_000, null, idSession));

        List<ConstatAudit> constats = service.auditerPassage(idPassage).constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(SeveriteConstat.ERREUR);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.PREFIXE_NON_CONFORME);
        });
    }

    @Test
    @DisplayName("Originaux purgés (volume 0, fichiers absents) : aucun constat sur les originaux")
    void originaux_purges_aucun_constat() throws IOException {
        Long idPassage = creerSessionCoherente(1);
        // Purge : volume des originaux à 0 (les lignes subsistent), puis suppression des fichiers bruts.
        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        sessionDao.marquerOriginauxPurges(idSession);
        Files.delete(racineSession.resolve("bruts").resolve(NOM_ORIGINAL));

        assertThat(service.auditerPassage(idPassage).sain()).isTrue();
    }

    @Test
    @DisplayName("Original externe (hors workspace) absent : INFO, pas ERREUR")
    void original_externe_absent_info() {
        Long idPassage = creerPassage(1);
        Long idSession = creerSession(idPassage, 4096L);
        String cheminExterne =
                Path.of("/media", "carte-sd-absente", NOM_ORIGINAL).toString();
        originalDao.insert(
                new EnregistrementOriginal(null, NOM_ORIGINAL, cheminExterne, 12.0, 384_000, null, idSession));

        List<ConstatAudit> constats = service.auditerPassage(idPassage).constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(SeveriteConstat.INFO);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.DISQUE_MANQUANT);
        });
    }

    @Test
    @DisplayName("Unité déposée à l'ancien préfixe (Pass2) sur un passage Pass1 : DEPOT_DIVERGENT")
    void depot_divergent() throws IOException {
        Long idPassage = creerSessionCoherente(1);
        String nomDivergent = new Prefixe("040962", 2026, 2, "A1").prefixeFichier() + "1.zip";
        depotDao.synchroniserPlan(
                idPassage,
                List.of(DepotUnite.aDeposer(idPassage, nomDivergent, TypeDepotUnite.ZIP, "2026-07-11T15:00:00")));

        List<ConstatAudit> constats = service.auditerPassage(idPassage).constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(SeveriteConstat.ERREUR);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.DEPOT_DIVERGENT);
            assertThat(c.cible()).isEqualTo(nomDivergent);
        });
    }

    @Test
    @DisplayName("Passage sans session : SESSION_ABSENTE en INFO")
    void passage_sans_session_info() {
        Long idPassage = creerPassage(1);

        List<ConstatAudit> constats = service.auditerPassage(idPassage).constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(SeveriteConstat.INFO);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.SESSION_ABSENTE);
        });
    }

    @Test
    @DisplayName("auditerTout : dossier de session sur disque sans passage -> DOSSIER_ORPHELIN")
    void dossier_orphelin_via_auditer_tout() throws IOException {
        Files.createDirectories(dossier.resolve("Car040962-2026-Pass9-A1"));

        List<ConstatAudit> constats = service.auditerTout().constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(SeveriteConstat.AVERTISSEMENT);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.DOSSIER_ORPHELIN);
        });
    }

    // --- Fabriques -------------------------------------------------------------------------------

    private Long creerPassage(int numeroPassage) {
        return passageDao
                .insert(new Passage(
                        null,
                        numeroPassage,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.VERIFIE,
                        Verdict.OK,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
    }

    private Long creerSession(Long idPassage, Long volumeOriginaux) {
        return sessionDao
                .insert(new SessionDEnregistrement(null, racineSession.toString(), volumeOriginaux, 4096L, idPassage))
                .id();
    }

    private Long creerSessionCoherente(int numeroPassage) throws IOException {
        Long idPassage = creerPassage(numeroPassage);
        Long idSession = creerSession(idPassage, 4096L);
        Path bruts = Files.createDirectories(racineSession.resolve("bruts"));
        Path transformes = Files.createDirectories(racineSession.resolve("transformes"));

        Path original = Files.write(bruts.resolve(NOM_ORIGINAL), new byte[16]);
        Long idOriginal = originalDao
                .insert(new EnregistrementOriginal(
                        null, NOM_ORIGINAL, original.toString(), 12.0, 384_000, null, idSession))
                .id();
        for (int index = 0; index < 2; index++) {
            String nomSequence = PREFIXE.nommerSequence(NOM_ORIGINAL, index);
            Path sequence = Files.write(transformes.resolve(nomSequence), new byte[16]);
            sequenceDao.insert(new SequenceDEcoute(
                    null, nomSequence, idOriginal, index, index * 5.0, 5.0, sequence.toString(), true, idSession));
        }
        Path journal = Files.write(racineSession.resolve("LogPR" + SERIE + ".txt"), new byte[16]);
        journalDao.insert(new JournalDuCapteur(null, journal.toString(), null, null, idSession));
        Path releve = Files.write(racineSession.resolve("PaRecPR" + SERIE + "_THLog.csv"), new byte[16]);
        releveDao.insert(new ReleveClimatique(null, releve.toString(), null, idSession));
        return idPassage;
    }
}

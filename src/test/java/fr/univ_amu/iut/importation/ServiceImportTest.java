package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.CopieProtegee;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.Progression;
import fr.univ_amu.iut.importation.model.Renommeur;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.model.TransformationAudio;
import fr.univ_amu.iut.importation.model.dao.AgregatImportDao;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.MicroDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Test d'orchestration de bout en bout de [ServiceImport] (parcours P2), sur une base SQLite
/// jetable (`@TempDir` + [MigrationSchema]) et de vrais moteurs/DAO (pas de mock). On vérifie
/// l'agrégat persisté, l'atomicité (O7), l'unicité (R5) et la protection de la source (R9).
///
/// Les WAV source sont synthétiques (petite fréquence) : le moteur lit la fréquence du fichier,
/// indépendante de celle annoncée dans le journal.
class ServiceImportTest {

    private static final String ID_USER = "u-1";
    private static final int FREQUENCE_WAV = 2000; // Hz, multiple de 10
    private static final int TRAMES = 3000; // 1,5 s -> ceil(2 × 1,5) = 3 séquences par original
    private static final String SERIE = "1925492";

    private static final String LOG =
            "22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492, V1.01,"
                    + " CPU 600000000, T4.1\n"
                    + "22/04/26 - 16:02:21 PR1925492 Sonde température/hygrométrie présente, lecture toutes"
                    + " les 600s\n"
                    + "22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz FL N FPH"
                    + " 00, S. R. 16dB 1dt. GN0, Bd. Freq. 8-120kHz, Wav 2-30s SD 99%\n";

    @TempDir
    Path racine;

    private SourceDeDonnees source;
    private ServiceImport service;
    private Path sd;
    private Long idPoint;
    private final Prefixe prefixe = new Prefixe("640380", 2026, 2, "Z1");

    private EnregistreurDao enregistreurDao;
    private MicroDao microDao;
    private SessionDao sessionDao;
    private EnregistrementOriginalDao originalDao;
    private SequenceDao sequenceDao;
    private JournalDuCapteurDao journalDao;
    private ReleveClimatiqueDao releveDao;

    @BeforeEach
    void preparer() throws IOException {
        Workspace workspace = new Workspace(racine.resolve("ws"));
        source = new SourceDeDonnees(workspace);
        new MigrationSchema(source).migrer();

        // Parents FK : utilisateur -> site (carré 640380) -> point (Z1).
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        Site site = siteDao.insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-05-31", ID_USER));
        PointDEcoute point = pointDao.insert(new PointDEcoute(null, "Z1", 43.5, 5.4, null, site.id()));
        idPoint = point.id();

        enregistreurDao = new EnregistreurDao(source);
        microDao = new MicroDao(source);
        sessionDao = new SessionDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        sequenceDao = new SequenceDao(source);
        journalDao = new JournalDuCapteurDao(source);
        releveDao = new ReleveClimatiqueDao(source);

        service = new ServiceImport(
                new InspecteurDossier(new AnalyseurLogPR()),
                new CopieProtegee(),
                new Renommeur(),
                new TransformationAudio(),
                new AgregatImportDao(source),
                new UniteDeTravail(source),
                workspace,
                new HorlogeFigee(LocalDate.of(2026, 5, 31)));

        sd = preparerCarteSD(racine.resolve("sd"));
    }

    @Test
    @DisplayName("Import complet : agrégat persisté (passage Transformé, session, originaux, séquences)")
    void import_complet_persiste_l_agregat() {
        ResultatImport resultat = service.importer(sd, idPoint, prefixe);

        assertThat(resultat.passage().id()).isNotNull();
        assertThat(resultat.passage().statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(resultat.passage().annee()).isEqualTo(2026);
        assertThat(resultat.passage().numeroPassage()).isEqualTo(2);
        assertThat(resultat.passage().idPoint()).isEqualTo(idPoint);
        assertThat(resultat.passage().idEnregistreur()).isEqualTo(SERIE);
        assertThat(resultat.passage().dateEnregistrement()).isEqualTo("2026-04-22");
        assertThat(resultat.passage().heureDebut()).isEqualTo("20:25:00");
        assertThat(resultat.passage().heureFin()).isEqualTo("07:47:00");
        assertThat(resultat.nombreOriginaux()).isEqualTo(2);
        assertThat(resultat.nombreSequences()).isEqualTo(6); // 3 par original

        Long idSession = resultat.session().id();
        assertThat(sessionDao.trouverParPassage(resultat.passage().id())).isPresent();
        assertThat(originalDao.findBySession(idSession)).hasSize(2);
        assertThat(sequenceDao.findBySession(idSession)).hasSize(6);
        assertThat(originalDao.findBySession(idSession))
                .allSatisfy(o -> assertThat(o.frequenceEchantillonnageHz()).isEqualTo(FREQUENCE_WAV));
    }

    @Test
    @DisplayName("#33 : le callback de progression couvre copie + transformation, fraction monotone 0→1")
    void progression_notifiee_pendant_l_import() {
        List<Progression> points = new ArrayList<>();
        service.importer(sd, idPoint, prefixe, points::add);

        // 2 originaux → 2 copies puis 2 transformations = 4 points de progression.
        assertThat(points)
                .extracting(Progression::libelle)
                .containsExactly("Copie 1/2", "Copie 2/2", "Transformation 1/2", "Transformation 2/2");
        assertThat(points).extracting(Progression::fraction).containsExactly(0.25, 0.5, 0.75, 1.0);
        assertThat(points).extracting(Progression::fraction).isSorted();
    }

    @Test
    @DisplayName("#54 : un second import lancé pendant qu'un autre tourne est refusé (garde anti-concurrent)")
    void second_import_concurrent_refuse() {
        AtomicReference<Throwable> tentativeConcurrente = new AtomicReference<>();
        // Le callback de progression s'exécute *à l'intérieur* de l'import (verrou tenu) : on y tente un
        // second import, qui doit être refusé immédiatement par la garde (avant même la règle R5).
        Consumer<Progression> pendantImport = progression -> {
            if (tentativeConcurrente.get() == null) {
                tentativeConcurrente.set(catchThrowable(() -> service.importer(sd, idPoint, prefixe, p -> {})));
            }
        };

        service.importer(sd, idPoint, prefixe, pendantImport);

        assertThat(tentativeConcurrente.get())
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("déjà en cours");

        // Le verrou est relâché à la fin (finally) : un import ultérieur n'est plus bloqué par la garde
        // (ici c'est R5 — doublon du même quadruplet — qui le refuse, preuve que la garde a libéré).
        assertThatThrownBy(() -> service.importer(sd, idPoint, prefixe))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("R5");
    }

    @Test
    @DisplayName("#12 : le découpage parallélisé traite tous les originaux, progression complète et ordonnée")
    void decoupage_parallele_traite_tous_les_originaux() throws IOException {
        Path multi = racine.resolve("sd-multi");
        Files.createDirectories(multi);
        Files.writeString(multi.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        int nb = 6;
        for (int i = 0; i < nb; i++) {
            // 6 originaux distincts (secondes croissantes) → 6 découpages lancés en parallèle.
            ecrireWav(multi.resolve("PaRecPR1925492_20260422_2039" + String.format("%02d", i) + ".wav"));
        }
        List<Progression> points = new ArrayList<>();

        ResultatImport resultat = service.importer(multi, idPoint, prefixe, points::add);

        // Aucun résultat perdu par la parallélisation : les 6 originaux sont persistés.
        assertThat(originalDao.findBySession(resultat.session().id())).hasSize(nb);
        // L'émission de progression est sérialisée + comptée sous verrou → libellés monotones 1..N,
        // même si l'ordre d'achèvement des threads virtuels est quelconque.
        assertThat(points)
                .filteredOn(p -> p.libelle().startsWith("Transformation"))
                .extracting(Progression::libelle)
                .containsExactly(
                        "Transformation 1/6",
                        "Transformation 2/6",
                        "Transformation 3/6",
                        "Transformation 4/6",
                        "Transformation 5/6",
                        "Transformation 6/6");
        assertThat(points).extracting(Progression::fraction).isSorted().endsWith(1.0);
    }

    @Test
    @DisplayName("Upsert enregistreur + micro depuis le journal, journal et relevé persistés")
    void upsert_materiel_et_annexes() {
        ResultatImport resultat = service.importer(sd, idPoint, prefixe);
        Long idSession = resultat.session().id();

        assertThat(enregistreurDao.findById(SERIE)).isPresent();
        assertThat(enregistreurDao.findById(SERIE).orElseThrow().versionModele())
                .isEqualTo("V1.01, T4.1");
        assertThat(microDao.trouverActifParEnregistreur(SERIE)).isPresent();
        assertThat(microDao.trouverActifParEnregistreur(SERIE).orElseThrow().bandePassante())
                .isEqualTo("8-120kHz");
        assertThat(journalDao.trouverParSession(idSession)).isPresent();
        assertThat(journalDao.trouverParSession(idSession).orElseThrow().anomaliesDetectees())
                .isEqualTo("[]"); // journal nominal : aucune anomalie
        assertThat(releveDao.trouverParSession(idSession)).isPresent();
    }

    @Test
    @DisplayName("Les fichiers transformés sont écrits dans transformes/ (R22)")
    void sequences_ecrites_sur_disque() throws IOException {
        service.importer(sd, idPoint, prefixe);

        Path transformes =
                racine.resolve("ws").resolve(prefixe.nomDossierSession()).resolve("transformes");
        try (var flux = Files.list(transformes)) {
            assertThat(flux.filter(p -> p.getFileName().toString().endsWith(".wav")))
                    .hasSize(6);
        }
    }

    @Test
    @DisplayName("R9 : la carte SD source est strictement inchangée après l'import")
    void source_sd_inchangee() throws IOException {
        List<String> avant = listerNoms(sd);

        service.importer(sd, idPoint, prefixe);

        assertThat(listerNoms(sd)).as("aucune écriture sur la SD (R9)").isEqualTo(avant);
    }

    @Test
    @DisplayName("R5 : réimporter le même quadruplet est refusé (RegleMetierException)")
    void reimport_du_meme_quadruplet_refuse() {
        service.importer(sd, idPoint, prefixe);

        assertThatThrownBy(() -> service.importer(sd, idPoint, prefixe))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("R5");
    }

    @Test
    @DisplayName("O7 : un échec de persistance annule TOUT (rollback) : aucun enregistreur committé")
    void rollback_si_echec_de_persistance() {
        // point_id inexistant : la persistance échoue sur la contrainte FK du passage, APRÈS l'upsert
        // de l'enregistreur dans la même transaction.
        assertThatThrownBy(() -> service.importer(sd, 9999L, prefixe)).isInstanceOf(DataAccessException.class);

        assertThat(enregistreurDao.findById(SERIE))
                .as("l'upsert de l'enregistreur doit avoir été annulé avec la transaction")
                .isEmpty();
        assertThat(microDao.trouverActifParEnregistreur(SERIE)).isEmpty();
    }

    @Test
    @DisplayName("Un dossier sans journal LogPR est refusé (enregistreur non identifiable)")
    void sans_journal_refuse() throws IOException {
        Files.delete(sd.resolve("LogPR1925492.txt"));

        assertThatThrownBy(() -> service.importer(sd, idPoint, prefixe)).isInstanceOf(RegleMetierException.class);
    }

    // --- Helpers (autonomes) ----------------------------------------------------

    private Path preparerCarteSD(Path dossier) throws IOException {
        Files.createDirectories(dossier);
        Files.writeString(dossier.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(dossier.resolve("PaRecPR1925492_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
        ecrireWav(dossier.resolve("PaRecPR1925492_20260422_203922.wav"));
        ecrireWav(dossier.resolve("PaRecPR1925492_20260422_204326.wav"));
        return dossier;
    }

    private static void ecrireWav(Path fichier) throws IOException {
        byte[] pcm = new byte[TRAMES * 2];
        for (int i = 0; i < TRAMES; i++) {
            short e = (short) (((i * 41) % 1000) - 500);
            pcm[2 * i] = (byte) (e & 0xFF);
            pcm[2 * i + 1] = (byte) ((e >> 8) & 0xFF);
        }
        ByteBuffer buf = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + pcm.length);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(FREQUENCE_WAV);
        buf.putInt(FREQUENCE_WAV * 2);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        Files.write(fichier, buf.array());
    }

    private static List<String> listerNoms(Path dossier) throws IOException {
        try (var flux = Files.list(dossier)) {
            return flux.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }
}

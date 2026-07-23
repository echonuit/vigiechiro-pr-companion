package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.importation.model.ServiceImportReference;
import fr.univ_amu.iut.importation.model.ServiceImportReference.ResultatImportReference;
import fr.univ_amu.iut.importation.model.dao.AgregatImportDao;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Test de bout en bout de [ServiceImportReference] sur une base SQLite jetable (`@TempDir` +
/// [MigrationSchema]) et de vrais DAO (pas de mock). Les fixtures sont de vraies petites séquences WAV
/// **déjà transformées** (en-tête à Fe/10, comme un brut PR expansé ×10), dans un dossier **externe** à
/// l'espace de travail.
///
/// On vérifie l'invariant central : en mode référence, le passage référence les WAV **en place** (aucun
/// octet audio recopié dans l'espace de travail), ses originaux sont des **placeholders** (sans fréquence
/// d'acquisition), et chaque séquence porte ses preuves d'identité (taille + empreinte). En mode copie,
/// les WAV atterrissent dans `transformes/`.
class ServiceImportReferenceTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";

    /// En-tête des fixtures « déjà transformées » : Fe/10 (un brut PR expansé ×10 porte 38400 Hz alors que
    /// le log déclarerait 384000). La durée réelle se lit alors « en-tête ÷ 10 ».
    private static final int FREQUENCE_ENTETE = 38_400;

    /// Trames par séquence : 3840 / 38400 = 0,1 s à l'écoute → 0,01 s réelle (÷ 10). Petit, mais assez pour
    /// une empreinte courte discriminante.
    private static final int TRAMES = 3_840;

    @TempDir
    Path racine;

    private SourceDeDonnees source;
    private ServiceImportReference service;
    private Workspace workspace;
    private Long idPoint;

    private SessionDao sessionDao;
    private EnregistrementOriginalDao originalDao;
    private SequenceDao sequenceDao;
    private PassageDao passageDao;
    private EnregistreurDao enregistreurDao;
    private JournalDuCapteurDao journalDao;

    @BeforeEach
    void preparer() {
        workspace = new Workspace(racine.resolve("ws"));
        source = new SourceDeDonnees(workspace);
        new MigrationSchema(source).migrer();

        // Parents FK : utilisateur -> site (carré 640380) -> point (Z1).
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        Site site = siteDao.insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-05-31", ID_USER));
        PointDEcoute point = pointDao.insert(new PointDEcoute(null, "Z1", 43.5, 5.4, null, site.id()));
        idPoint = point.id();

        sessionDao = new SessionDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        sequenceDao = new SequenceDao(source);
        passageDao = new PassageDao(source);
        enregistreurDao = new EnregistreurDao(source);
        journalDao = new JournalDuCapteurDao(source);

        service = new ServiceImportReference(
                pointDao,
                siteDao,
                new AgregatImportDao(source),
                new UniteDeTravail(source),
                workspace,
                new HorlogeFigee(LocalDate.of(2026, 5, 31)),
                new ServiceDisponibiliteAudio(sessionDao, sequenceDao, workspace));
    }

    @Test
    @DisplayName("Mode référence : passage créé, séquences pointant le dossier externe, aucun WAV sous le workspace")
    void reference_cree_le_passage_et_reference_en_place() throws IOException {
        Path externe = preparerDossierTransforme(racine.resolve("externe"));

        ResultatImportReference resultat =
                service.importer(externe, idPoint, 2026, 1, true, p -> {}, JetonAnnulation.neutre());

        assertThat(resultat.nombreSequences()).isEqualTo(3); // 2 tranches + 1 tranche
        Long idSession =
                sessionDao.trouverParPassage(resultat.idPassage()).orElseThrow().id();
        assertThat(originalDao.findBySession(idSession)).hasSize(2); // 2 originaux placeholders

        // (a) Les séquences pointent le dossier EXTERNE, pas l'espace de travail.
        assertThat(sequenceDao.findBySession(idSession))
                .hasSize(3)
                .allSatisfy(s -> assertThat(s.cheminFichier())
                        .as("mode référence : le WAV reste chez l'utilisateur")
                        .startsWith(externe.toString()));
        // (a bis) Aucun octet audio n'est recopié sous la racine de l'espace de travail.
        assertThat(wavSousLaRacine(workspace.racine()))
                .as("mode référence : rien d'audio n'est écrit dans l'espace de travail")
                .isEmpty();
    }

    @Test
    @DisplayName("Mode référence : chaque séquence porte taille et empreinte, l'original est un placeholder")
    void reference_pose_identite_et_placeholder() throws IOException {
        Path externe = preparerDossierTransforme(racine.resolve("externe"));

        ResultatImportReference resultat =
                service.importer(externe, idPoint, 2026, 1, true, p -> {}, JetonAnnulation.neutre());
        Long idSession =
                sessionDao.trouverParPassage(resultat.idPassage()).orElseThrow().id();

        // (b) Taille et empreinte présentes, et recalculables depuis le fichier référencé.
        assertThat(sequenceDao.findBySession(idSession)).isNotEmpty().allSatisfy(s -> {
            Path fichier = Path.of(s.cheminFichier());
            assertThat(s.tailleOctets()).isNotNull().isEqualTo(taille(fichier));
            assertThat(s.empreinte()).isNotNull().isEqualTo(Empreintes.empreinteCourte(fichier));
        });
        // (c) Les originaux sont des placeholders : pas de fréquence d'acquisition (ni SHA, ni taille).
        assertThat(originalDao.findBySession(idSession)).isNotEmpty().allSatisfy(o -> {
            assertThat(o.frequenceEchantillonnageHz())
                    .as("placeholder reconnu par la réactivation : aucune fréquence d'acquisition")
                    .isNull();
            assertThat(o.sha256()).isNull();
            assertThat(o.tailleOctets()).isNull();
            assertThat(o.cheminFichier()).as("sentinelle NOT NULL").isNotBlank();
        });
    }

    @Test
    @DisplayName("Mode copie : les WAV sont recopiés dans transformes/, les séquences pointent la copie interne")
    void copie_materialise_dans_transformes() throws IOException {
        Path externe = preparerDossierTransforme(racine.resolve("externe"));

        ResultatImportReference resultat =
                service.importer(externe, idPoint, 2026, 1, false, p -> {}, JetonAnnulation.neutre());

        // (d) Les WAV atterrissent sous <workspace>/<session>/transformes/.
        Path transformes = workspace.dossierTransformes("Car640380-2026-Pass1-Z1");
        assertThat(wavSousLaRacine(transformes)).hasSize(3);
        Long idSession =
                sessionDao.trouverParPassage(resultat.idPassage()).orElseThrow().id();
        assertThat(sequenceDao.findBySession(idSession))
                .hasSize(3)
                .allSatisfy(s -> assertThat(s.cheminFichier()).startsWith(transformes.toString()));
    }

    @Test
    @DisplayName("La progression est notifiée et sa fraction atteint 1.0 en fin d'import")
    void progression_atteint_un() throws IOException {
        Path externe = preparerDossierTransforme(racine.resolve("externe"));
        List<Progression> points = new ArrayList<>();

        service.importer(externe, idPoint, 2026, 1, true, points::add, JetonAnnulation.neutre());

        assertThat(points).isNotEmpty();
        assertThat(points).extracting(Progression::fraction).isSorted().endsWith(1.0);
    }

    @Test
    @DisplayName("Durée réelle : en-tête WAV ÷ 10 (0,1 s à l'écoute → 0,01 s réelle d'acquisition)")
    void reference_pose_la_duree_reelle_entete_divisee_par_dix() throws IOException {
        // Durée en-tête = TRAMES / FREQUENCE_ENTETE = 3840 / 38400 = 0,1 s (à l'écoute). La durée réelle
        // d'acquisition persistée est cette valeur divisée par le facteur d'expansion ×10, soit 0,01 s.
        Path externe = preparerDossierTransforme(racine.resolve("externe"));

        ResultatImportReference resultat =
                service.importer(externe, idPoint, 2026, 1, true, p -> {}, JetonAnnulation.neutre());
        Long idSession =
                sessionDao.trouverParPassage(resultat.idPassage()).orElseThrow().id();

        assertThat(sequenceDao.findBySession(idSession))
                .isNotEmpty()
                .allSatisfy(s -> assertThat(s.dureeSecondes())
                        .as("durée réelle = durée en-tête (0,1 s) ÷ 10")
                        .isCloseTo(0.01, within(1e-6)));
    }

    @Test
    @DisplayName("Index et offset : la tranche _001 porte index 1 et offset 5,0 s ; _000 index 0 et offset 0,0 s")
    void index_et_offset_derives_du_suffixe_nnn() throws IOException {
        Path externe = preparerDossierTransforme(racine.resolve("externe"));

        ResultatImportReference resultat =
                service.importer(externe, idPoint, 2026, 1, true, p -> {}, JetonAnnulation.neutre());
        Long idSession =
                sessionDao.trouverParPassage(resultat.idPassage()).orElseThrow().id();
        List<SequenceDEcoute> sequences = sequenceDao.findBySession(idSession);

        // La deuxième tranche du même original : index 1, offset 1 × 5 s = 5,0 s (offset = index × durée
        // de séquence). Un index figé à 0 donnerait 0,0 s ; un offset divisé au lieu de multiplié, 0,2 s.
        assertThat(sequences)
                .filteredOn(s -> s.nomFichier().endsWith("203922_001.wav"))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.indexSource()).isEqualTo(1);
                    assertThat(s.offsetSourceSecondes()).isEqualTo(5.0);
                });
        assertThat(sequences)
                .filteredOn(s -> s.nomFichier().endsWith("203922_000.wav"))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.indexSource()).isZero();
                    assertThat(s.offsetSourceSecondes()).isEqualTo(0.0);
                });
    }

    @Test
    @DisplayName("Identité dérivée des noms : date du passage 2026-04-22 (pas l'horloge) et série de l'enregistreur")
    void identite_du_passage_derivee_des_noms_de_fichiers() throws IOException {
        // L'horloge est figée au 2026-05-31 : si l'identité ne venait pas des noms (série + date), le
        // passage serait daté du 31/05 et l'enregistreur « inconnu ». Le _20260422_ des noms fait foi.
        Path externe = preparerDossierTransforme(racine.resolve("externe"));

        ResultatImportReference resultat =
                service.importer(externe, idPoint, 2026, 1, true, p -> {}, JetonAnnulation.neutre());
        Passage passage = passageDao.findById(resultat.idPassage()).orElseThrow();

        assertThat(passage.dateEnregistrement())
                .as("date lue du _20260422_ des noms, pas de l'horloge figée")
                .isEqualTo("2026-04-22");
        assertThat(passage.idEnregistreur()).isEqualTo(SERIE);
        assertThat(enregistreurDao.findById(SERIE)).isPresent();
    }

    @Test
    @DisplayName("Défauts : année ET numéro null → année de l'horloge (2026) et prochain numéro libre (1)")
    void defauts_annee_courante_et_prochain_numero_libre() throws IOException {
        Path externe = preparerDossierTransforme(racine.resolve("externe"));

        ResultatImportReference resultat =
                service.importer(externe, idPoint, null, null, true, p -> {}, JetonAnnulation.neutre());
        Passage passage = passageDao.findById(resultat.idPassage()).orElseThrow();

        assertThat(passage.annee()).as("année de l'horloge figée (2026)").isEqualTo(2026);
        assertThat(passage.numeroPassage()).as("prochain numéro libre du point").isEqualTo(1);
        assertThat(resultat.nomSession()).isEqualTo("Car640380-2026-Pass1-Z1");
    }

    @Test
    @DisplayName("Annulation d'emblée : jeton déjà annulé → exception, aucune progression, aucun passage")
    void annulation_immediate_ne_persiste_rien() throws IOException {
        Path externe = preparerDossierTransforme(racine.resolve("externe"));
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler();
        List<Progression> points = new ArrayList<>();

        assertThatThrownBy(() -> service.importer(externe, idPoint, 2026, 1, true, points::add, jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        assertThat(points)
                .as("annulé avant tout travail : aucune progression émise")
                .isEmpty();
        assertThat(passageDao.findAll()).as("aucun passage persisté").isEmpty();
    }

    @Test
    @DisplayName("Annulation en cours : le jeton s'annule pendant la matérialisation → session nettoyée, rien persisté")
    void annulation_en_cours_nettoie_la_session_partielle() throws IOException {
        Path externe = preparerDossierTransforme(racine.resolve("externe"));
        JetonAnnulation jeton = new JetonAnnulation();
        // La trace de journal est déjà écrite dans la session quand la matérialisation démarre : on annule
        // au premier point de progression, ce qui doit lever l'annulation ET nettoyer la session partielle.
        Consumer<Progression> annulerAuPremier = p -> jeton.annuler();

        assertThatThrownBy(() -> service.importer(externe, idPoint, 2026, 1, true, annulerAuPremier, jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        assertThat(passageDao.findAll()).as("aucun passage persisté").isEmpty();
        assertThat(workspace.dossierSession("Car640380-2026-Pass1-Z1"))
                .as("la session partielle (trace de journal) a été supprimée")
                .doesNotExist();
    }

    @Test
    @DisplayName("Filtre .wav : les fichiers non-audio du dossier sont ignorés (toujours 3 séquences)")
    void fichiers_non_wav_ignores() throws IOException {
        Path externe = preparerDossierTransforme(racine.resolve("externe"));
        Files.writeString(externe.resolve("notes.txt"), "pas un wav", StandardCharsets.UTF_8);
        Files.writeString(externe.resolve("resume.csv"), "a,b\n", StandardCharsets.UTF_8);

        ResultatImportReference resultat =
                service.importer(externe, idPoint, 2026, 1, true, p -> {}, JetonAnnulation.neutre());

        assertThat(resultat.nombreSequences())
                .as("les non-.wav ne deviennent pas des séquences")
                .isEqualTo(3);
        Long idSession =
                sessionDao.trouverParPassage(resultat.idPassage()).orElseThrow().id();
        assertThat(originalDao.findBySession(idSession)).hasSize(2);
    }

    @Test
    @DisplayName("Un journal du capteur (trace synthétique) est persisté pour la session")
    void journal_du_capteur_persiste() throws IOException {
        Path externe = preparerDossierTransforme(racine.resolve("externe"));

        ResultatImportReference resultat =
                service.importer(externe, idPoint, 2026, 1, true, p -> {}, JetonAnnulation.neutre());
        Long idSession =
                sessionDao.trouverParPassage(resultat.idPassage()).orElseThrow().id();

        assertThat(journalDao.trouverParSession(idSession)).isPresent();
    }

    @Test
    @DisplayName("Un WAV sans suffixe _NNN forme son propre original (index 0)")
    void wav_sans_suffixe_forme_son_original() throws IOException {
        Path externe = Files.createDirectories(racine.resolve("externe-sans-suffixe"));
        ecrireWavTransforme(externe.resolve("Car640380-2026-Pass1-Z1-PaRecPR" + SERIE + "_20260422_210000.wav"), 9);

        ResultatImportReference resultat =
                service.importer(externe, idPoint, 2026, 1, true, p -> {}, JetonAnnulation.neutre());
        Long idSession =
                sessionDao.trouverParPassage(resultat.idPassage()).orElseThrow().id();

        assertThat(resultat.nombreSequences()).isEqualTo(1);
        assertThat(originalDao.findBySession(idSession))
                .singleElement()
                .satisfies(o -> assertThat(o.nomFichier()).endsWith("_210000.wav"));
        assertThat(sequenceDao.findBySession(idSession))
                .singleElement()
                .satisfies(s -> assertThat(s.indexSource()).isZero());
    }

    @Test
    @DisplayName("Refus métier : dossier introuvable, dossier sans WAV, point inconnu")
    void refus_metier() throws IOException {
        assertThatThrownBy(() -> service.importer(
                        racine.resolve("absent"), idPoint, 2026, 1, true, p -> {}, JetonAnnulation.neutre()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("introuvable");

        Path vide = Files.createDirectories(racine.resolve("vide"));
        assertThatThrownBy(() -> service.importer(vide, idPoint, 2026, 1, true, p -> {}, JetonAnnulation.neutre()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Aucun fichier .wav");

        Path externe = preparerDossierTransforme(racine.resolve("externe"));
        assertThatThrownBy(() -> service.importer(externe, 9999L, 2026, 1, true, p -> {}, JetonAnnulation.neutre()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Point d'écoute introuvable");
    }

    /// Prépare un dossier **externe** de séquences déjà transformées : deux originaux (2 tranches + 1
    /// tranche), noms horodatés portant la série (`PaRecPR<série>_<date>_<heure>_NNN.wav`) pour que le
    /// journal de repli déduise série et date.
    private static Path preparerDossierTransforme(Path dossier) throws IOException {
        Files.createDirectories(dossier);
        String base = "Car640380-2026-Pass1-Z1-PaRecPR" + SERIE + "_20260422_";
        ecrireWavTransforme(dossier.resolve(base + "203922_000.wav"), 1);
        ecrireWavTransforme(dossier.resolve(base + "203922_001.wav"), 2);
        ecrireWavTransforme(dossier.resolve(base + "204326_000.wav"), 3);
        return dossier;
    }

    /// Noms de fichiers WAV (triés) présents sous une racine, récursivement.
    private static List<String> wavSousLaRacine(Path racine) throws IOException {
        if (!Files.isDirectory(racine)) {
            return List.of();
        }
        try (Stream<Path> flux = Files.walk(racine)) {
            return flux.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(nom -> nom.toLowerCase(java.util.Locale.ROOT).endsWith(".wav"))
                    .sorted()
                    .toList();
        }
    }

    private static long taille(Path fichier) {
        try {
            return Files.size(fichier);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// Écrit un petit WAV mono 16 bits de `TRAMES` trames à `FREQUENCE_ENTETE` Hz (Fe/10). Le `germe`
    /// varie le contenu PCM pour que deux séquences aient des empreintes distinctes.
    private static void ecrireWavTransforme(Path fichier, int germe) throws IOException {
        byte[] pcm = new byte[TRAMES * 2];
        for (int i = 0; i < TRAMES; i++) {
            short e = (short) (((i * 41 + germe * 7) % 1000) - 500);
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
        buf.putInt(FREQUENCE_ENTETE);
        buf.putInt(FREQUENCE_ENTETE * 2);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        Files.write(fichier, buf.array());
    }
}

package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.CopieProtegee;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.NuitAImporter;
import fr.univ_amu.iut.importation.model.NuitDetectee;
import fr.univ_amu.iut.importation.model.OutilsImport;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.Renommeur;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.model.StatutImportFichier;
import fr.univ_amu.iut.importation.model.TransformationAudio;
import fr.univ_amu.iut.importation.model.dao.AgregatImportDao;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.MicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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
/// Les WAV source sont synthétiques ; c'est la **fréquence d'acquisition du journal** (`Fe384kHz`) qui
/// pilote la transformation, pas l'en-tête WAV (cf. `FrequenceAcquisition`).
class ServiceImportTest {

    /// Marge du garde-fou d'espace disque de l'import (#2041), reprise de `MoteurImport` : les tests de
    /// seuil doivent viser **entre** les deux besoins, pas à côté.
    private static final long MARGE_GARDE_OCTETS = 100L * 1000 * 1000;

    private static final String ID_USER = "u-1";
    // En-tête des fixtures « directes » : égal à la Fe du log (384 kHz). Un vrai brut PR aurait l'en-tête à
    // Fe/10 (cf. nuit_pr_deja_expansee_importe_sans_rejet), mais l'arithmétique se pilote sur le log.
    private static final int FREQUENCE_WAV = 384_000; // Hz, multiple de 10, = Fe du log
    private static final int TRAMES = 576_000; // 1,5 s à 384 kHz -> ceil(1,5 / 5) = 1 séquence par original
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
                OutilsImport.reels(new CopieProtegee(), new Renommeur(), new TransformationAudio()),
                new AgregatImportDao(source),
                new UniteDeTravail(source),
                workspace,
                new HorlogeFigee(LocalDate.of(2026, 5, 31)),
                idPassage -> 0,
                new ServiceSauvegarde(source, new HorlogeFigee(LocalDate.of(2026, 5, 31))),
                Optional.empty());

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
        assertThat(resultat.nombreSequences()).isEqualTo(2); // 1 par original (1,5 s < 5 s)

        Long idSession = resultat.session().id();
        assertThat(sessionDao.trouverParPassage(resultat.passage().id())).isPresent();
        assertThat(resultat.session().volumeOriginauxOctets())
                .as("mode conservation : le volume des bruts archivés est persisté")
                .isPositive();
        assertThat(originalDao.findBySession(idSession)).hasSize(2);
        assertThat(sequenceDao.findBySession(idSession)).hasSize(2);
        assertThat(originalDao.findBySession(idSession))
                .allSatisfy(o -> assertThat(o.frequenceEchantillonnageHz()).isEqualTo(FREQUENCE_WAV));
    }

    @Test
    @DisplayName("#1299 : séquences et originaux portent taille et empreinte dès l'import")
    void import_pose_taille_et_empreinte() {
        ResultatImport resultat = service.importer(sd, idPoint, prefixe);

        Long idSession = resultat.session().id();
        assertThat(sequenceDao.findBySession(idSession)).isNotEmpty().allSatisfy(s -> {
            Path fichier = Path.of(s.cheminFichier());
            assertThat(s.tailleOctets()).isEqualTo(taille(fichier));
            assertThat(s.empreinte())
                    .as("l'empreinte persistée doit être recalculable depuis le fichier écrit")
                    .isEqualTo(Empreintes.empreinteCourte(fichier));
        });
        assertThat(originalDao.findBySession(idSession))
                .isNotEmpty()
                .allSatisfy(o -> assertThat(o.tailleOctets()).isEqualTo(taille(Path.of(o.cheminFichier()))));
    }

    /// Taille d'un fichier sans exception vérifiée (assertions dans les lambdas AssertJ).
    private static long taille(Path fichier) {
        try {
            return Files.size(fichier);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("Bug date : mono-nuit → la date vient de la soirée des WAV, pas de la 1re ligne du LogPR")
    void import_mono_nuit_date_vient_des_wav_pas_du_deploiement() throws IOException {
        // Le LogPR démarre au DÉPLOIEMENT (22/04/26, sa 1re ligne), mais les WAV importés sont d'une nuit
        // ULTÉRIEURE (24/04). Auparavant, l'import mono-nuit datait le passage d'après le journal (22/04) :
        // un dossier de passe ultérieure héritait donc à tort de la 1re nuit (collision constatée au dépôt).
        // Désormais la date vient de la soirée réelle des WAV (24/04), comme l'import multi-nuits.
        Path nuitTardive = racine.resolve("sd-nuit-tardive");
        Files.createDirectories(nuitTardive);
        Files.writeString(nuitTardive.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        ecrireWav(nuitTardive.resolve("PaRecPR1925492_20260424_203922.wav"));
        ecrireWav(nuitTardive.resolve("PaRecPR1925492_20260424_204326.wav"));

        ResultatImport resultat = service.importer(nuitTardive, idPoint, prefixe);

        assertThat(resultat.passage().dateEnregistrement())
                .as("date de soirée des WAV (24/04), pas la 1re ligne du LogPR (22/04, déploiement)")
                .isEqualTo("2026-04-24");
    }

    @Test
    @DisplayName("Phase 1d-B : la participation Vigie-Chiro est créée à l'import (best-effort) pour le passage")
    void participation_creee_a_l_import() {
        SynchronisationParticipation sync = mock(SynchronisationParticipation.class);
        ServiceImport avecSync = new ServiceImport(
                new InspecteurDossier(new AnalyseurLogPR()),
                OutilsImport.reels(new CopieProtegee(), new Renommeur(), new TransformationAudio()),
                new AgregatImportDao(source),
                new UniteDeTravail(source),
                new Workspace(racine.resolve("ws")),
                new HorlogeFigee(LocalDate.of(2026, 5, 31)),
                idPassage -> 0,
                new ServiceSauvegarde(source, new HorlogeFigee(LocalDate.of(2026, 5, 31))),
                Optional.of(sync));

        ResultatImport resultat = avecSync.importer(sd, idPoint, prefixe);

        // Le passage fraîchement importé se voit créer sa participation VigieChiro au plus tôt (best-effort) ;
        // le dépôt la réutilisera ensuite (pas de doublon).
        verify(sync).creerPour(resultat.passage().id());
    }

    @Test
    @DisplayName("Réalité PR : des bruts déjà expansés ×10 (en-tête Fe/10) s'importent sans rejet ni double expansion")
    void nuit_pr_deja_expansee_importe_sans_rejet() throws IOException {
        // L'enregistreur PR écrit ses bruts avec un en-tête à 38400 Hz (= Fe/10) alors que le log déclare
        // Fe384kHz. Ces fichiers doivent s'importer SANS avertissement (plus de garde-fou « déjà ralenti »)
        // et NE PAS être ré-expansés.
        Path carte = racine.resolve("sd-pr");
        Files.createDirectories(carte);
        Files.writeString(carte.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        ecrireWav(carte.resolve("PaRecPR1925492_20260422_203922.wav"), 38_400);

        ResultatImport resultat = service.importer(carte, idPoint, prefixe);

        assertThat(resultat.passage().statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(resultat.nombreOriginaux()).isEqualTo(1);
        assertThat(resultat.nombreSequences()).isEqualTo(1); // 576000 / 384000 = 1,5 s réelle < 5 s

        Long idSession = resultat.session().id();
        // La vraie fréquence d'acquisition (384 kHz du log) est persistée, PAS l'en-tête 38400 Hz.
        assertThat(originalDao.findBySession(idSession))
                .singleElement()
                .satisfies(o -> assertThat(o.frequenceEchantillonnageHz()).isEqualTo(FREQUENCE_WAV));
        // La durée persistée est **réelle** : 576000 trames / 384000 Hz (fréquence d'ACQUISITION, lue au
        // log) = 1,5 s (#1051). Une double expansion se lirait 15 s (÷ Fe/10 = 38400) : la durée réelle est
        // le témoin qu'on n'a pas ré-expansé un brut déjà expansé.
        assertThat(sequenceDao.findBySession(idSession))
                .singleElement()
                .satisfies(s -> assertThat(s.dureeSecondes()).isEqualTo(1.5));
    }

    @Test
    @DisplayName("#2041 : un disque trop plein refuse l'import AVANT d'écrire, et dit combien il manque")
    void disque_insuffisant_refuse_avant_ecriture() {
        // Refuser tôt plutôt qu'échouer à mi-import en laissant une session partielle : c'est ce que
        // `ExtracteurZip` constatait en ENOSPC sans pouvoir le prévenir.
        ServiceImport serre = serviceAvecDisque(dossier -> 1L);

        assertThatThrownBy(() -> serre.importer(sd, idPoint, prefixe))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Espace disque insuffisant")
                .hasMessageContaining("Go disponibles");

        assertThat(racine.resolve("ws").resolve(prefixe.nomDossierSession()))
                .as("aucune session partielle n'a été écrite")
                .doesNotExist();
    }

    @Test
    @DisplayName("#2041 : le refus propose de désactiver la conservation, qui divise le besoin par deux")
    void refus_en_conservation_propose_l_option() {
        // En conservation il faut deux fois le volume source (les bruts copiés, puis les séquences qui
        // pèsent autant — la transformation ne recalcule aucun échantillon). L'utilisateur a donc une
        // issue autre que « libérez de l'espace », et le message la lui donne.
        ServiceImport serre = serviceAvecDisque(dossier -> 1L);

        assertThatThrownBy(() -> serre.importer(sd, idPoint, prefixe)).hasMessageContaining("Conserver les originaux");
    }

    @Test
    @DisplayName("#2041 : sans conservation, le besoin est moitié moindre — un disque juste suffisant passe")
    void sans_conservation_le_besoin_est_moitie_moindre() {
        // Le même disque qui refuse en conservation accepte sans copie : c'est exactement ce que
        // l'option fait gagner, et le garde-fou doit le refléter.
        // Fenêtre calculée sur le volume réel, pas sur un ordre de grandeur deviné : les WAV de la
        // fixture sont minuscules, un écart fixe de 150 Mo couvrait les deux seuils à la fois.
        // conservation exige 2V + marge, sans copie V + marge : on vise entre les deux.
        long volumeSource = volumeDe(sd);
        long presqueJuste = MARGE_GARDE_OCTETS + volumeSource + volumeSource / 2;
        ServiceImport serre = serviceAvecDisque(dossier -> presqueJuste);

        assertThatThrownBy(() -> serre.importer(sd, idPoint, prefixe))
                .as("en conservation, il faut le double")
                .isInstanceOf(RegleMetierException.class);

        assertThat(serre.importer(sd, idPoint, prefixe, p -> {}, JetonAnnulation.neutre(), false)
                        .nombreSequences())
                .as("sans copie, le même disque suffit")
                .isPositive();
    }

    /// Un service dont la lecture d'espace disque est pilotée par le test (#2041).
    private ServiceImport serviceAvecDisque(fr.univ_amu.iut.commun.model.EspaceDisque espace) {
        return new ServiceImport(
                new InspecteurDossier(new AnalyseurLogPR()),
                new OutilsImport(new CopieProtegee(), new Renommeur(), new TransformationAudio(), espace),
                new AgregatImportDao(source),
                new UniteDeTravail(source),
                new Workspace(racine.resolve("ws")),
                new HorlogeFigee(LocalDate.of(2026, 5, 31)),
                idPassage -> 0,
                new ServiceSauvegarde(source, new HorlogeFigee(LocalDate.of(2026, 5, 31))),
                Optional.empty());
    }

    /// Volume total des WAV d'un dossier source, base du besoin estimé.
    private static long volumeDe(Path dossierSource) {
        try (var chemins = Files.walk(dossierSource)) {
            return chemins.filter(Files::isRegularFile)
                    .filter(c -> c.toString().endsWith(".wav"))
                    .mapToLong(c -> {
                        try {
                            return Files.size(c);
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .sum();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("#2255 : la source est un dossier quelconque - un NAS aussi bien qu'une carte SD")
    void import_sans_conservation_reference_un_dossier_quelconque() throws IOException {
        // Le test frère nomme sa source « sd », ce qui laisse croire à une spécificité carte SD. Rien ne
        // l'impose : l'import lit un DOSSIER, et sans conservation il y laisse les originaux. C'est ce qui
        // permet d'importer une nuit dont les bruts vivent sur un NAS, sans rien rapatrier (ADR 0048).
        Path nas = preparerCarteSD(racine.resolve("nas").resolve("captures-2026"));

        ResultatImport resultat = service.importer(nas, idPoint, prefixe, p -> {}, JetonAnnulation.neutre(), false);

        Path session = racine.resolve("ws").resolve(prefixe.nomDossierSession());
        assertThat(originalDao.findBySession(resultat.session().id()))
                .hasSize(2)
                .allSatisfy(o -> assertThat(o.cheminFichier())
                        .as("les bruts restent chez l'utilisateur, et la base les y suit")
                        .startsWith(nas.toString()));
        assertThat(session.resolve("bruts"))
                .as("rien n'est rapatrié dans l'espace de travail")
                .doesNotExist();
        assertThat(session.resolve("transformes"))
                .as("les séquences, elles, sont PRODUITES par l'application : on ne référence que ce qui"
                        + " appartient à l'utilisateur")
                .isDirectory();
    }

    @Test
    @DisplayName(
            "Sans conservation : aucun dossier bruts/, mais séquences produites et originaux tracés vers la source (SD)")
    void import_sans_conservation_ne_cree_pas_bruts() {
        ResultatImport resultat = service.importer(sd, idPoint, prefixe, p -> {}, JetonAnnulation.neutre(), false);

        Path session = racine.resolve("ws").resolve(prefixe.nomDossierSession());
        assertThat(session.resolve("bruts"))
                .as("mode sans copie : aucune archive des originaux n'est écrite dans le workspace")
                .doesNotExist();
        assertThat(session.resolve("transformes"))
                .as("les séquences d'écoute sont produites normalement")
                .isDirectory();

        // Sortie identique au mode conservation : même nombre d'originaux et de séquences.
        assertThat(resultat.nombreOriginaux()).isEqualTo(2);
        assertThat(resultat.nombreSequences()).isEqualTo(2);

        // Rien n'est conservé dans le workspace : le volume bruts persisté est nul, sinon M-Passage
        // afficherait le volume de la source (carte SD) et proposerait une purge fantôme.
        assertThat(resultat.session().volumeOriginauxOctets())
                .as("mode sans copie : aucun original conservé, volume bruts persisté = 0")
                .isZero();

        Long idSession = resultat.session().id();
        assertThat(originalDao.findBySession(idSession)).hasSize(2).allSatisfy(o -> {
            // Nommage R6 préservé (clé de jointure Tadarida inchangée)…
            assertThat(o.nomFichier()).startsWith(prefixe.prefixeFichier());
            // …mais le file_path pointe la SOURCE (SD) : les originaux n'ont pas été copiés.
            assertThat(o.cheminFichier()).startsWith(sd.toString());
        });
        assertThat(sequenceDao.findBySession(idSession)).hasSize(2);
    }

    @Test
    @DisplayName("#33 : le callback de progression couvre copie + transformation, fraction monotone 0→1")
    void progression_notifiee_pendant_l_import() {
        List<Progression> points = new ArrayList<>();
        service.importer(sd, idPoint, prefixe, points::add);

        // 2 originaux → 2 copies puis 2 transformations = 4 points de progression. Le compteur (libellé
        // sans le suffixe « · <fichier courant> » #146) suit la séquence attendue.
        assertThat(points)
                .extracting(p -> p.libelle().replaceAll(" · .*$", ""))
                .containsExactly("Copie 1/2", "Copie 2/2", "Transformation 1/2", "Transformation 2/2");
        // Le fichier courant (#146) est exposé : chaque libellé nomme un .wav.
        assertThat(points).allSatisfy(p -> assertThat(p.libelle()).containsPattern(" · .+\\.wav$"));
        assertThat(points).extracting(Progression::fraction).containsExactly(0.25, 0.5, 0.75, 1.0);
        assertThat(points).extracting(Progression::fraction).isSorted();
    }

    @Test
    @DisplayName("Sans conservation : la progression ne compte que les transformations (pas de phase de copie)")
    void progression_sans_conservation_saute_la_copie() {
        List<Progression> points = new ArrayList<>();
        service.importer(sd, idPoint, prefixe, points::add, JetonAnnulation.neutre(), false);

        // Pas de « Copie X/N » : la source est lue en place. Seules les 2 transformations sont notifiées.
        assertThat(points)
                .extracting(p -> p.libelle().replaceAll(" · .*$", ""))
                .containsExactly("Transformation 1/2", "Transformation 2/2");
        assertThat(points).extracting(Progression::fraction).containsExactly(0.5, 1.0);
    }

    @Test
    @DisplayName("#146 : un import annulé d'emblée ne persiste aucun passage ni session, et relâche le verrou")
    void import_annule_avant_copie() {
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler();

        assertThatThrownBy(() -> service.importer(sd, idPoint, prefixe, p -> {}, jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        // Aucun demi-état : pas de passage pour le quadruplet, pas de dossier de session sur disque.
        assertThat(service.numeroPassageDejaUtilise(idPoint, 2026, 2)).isFalse();
        assertThat(racine.resolve("ws").resolve(prefixe.nomDossierSession())).doesNotExist();
        // Le verrou anti-concurrent (#54) a été relâché : un import normal repasse ensuite.
        assertThat(service.importer(sd, idPoint, prefixe).passage().id()).isNotNull();
    }

    @Test
    @DisplayName("#146 : une annulation en cours de copie nettoie la session partielle (aucun demi-état)")
    void import_annule_en_cours_nettoie_la_session() {
        JetonAnnulation jeton = new JetonAnnulation();
        // On annule dès le premier point de progression (après la 1re copie) : la 2e copie ne part pas.
        Consumer<Progression> annulerApresPremier = p -> jeton.annuler();

        assertThatThrownBy(() -> service.importer(sd, idPoint, prefixe, annulerApresPremier, jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        assertThat(service.numeroPassageDejaUtilise(idPoint, 2026, 2)).isFalse();
        // La session partielle (un fichier déjà copié) a été supprimée.
        assertThat(racine.resolve("ws").resolve(prefixe.nomDossierSession())).doesNotExist();
    }

    @Test
    @DisplayName("#146 : une annulation pendant la DERNIÈRE transformation empêche encore la persistance")
    void import_annule_apres_derniere_transformation_ne_persiste_pas() {
        JetonAnnulation jeton = new JetonAnnulation();
        // On n'annule qu'au tout dernier point (après la 2e transformation) : aucun point de contrôle
        // par fichier ne le voit → seule la re-vérification post-transformation / pré-persistance l'attrape.
        Consumer<Progression> annulerAuDernier = p -> {
            if (p.libelle().startsWith("Transformation 2/2")) {
                jeton.annuler();
            }
        };

        assertThatThrownBy(() -> service.importer(sd, idPoint, prefixe, annulerAuDernier, jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        // Le point de non-retour (persistance) n'a pas été franchi : aucun passage, session nettoyée.
        assertThat(service.numeroPassageDejaUtilise(idPoint, 2026, 2)).isFalse();
        assertThat(racine.resolve("ws").resolve(prefixe.nomDossierSession())).doesNotExist();
    }

    @Test
    @DisplayName("#231 : reprise — des originaux déjà copiés+renommés ne sont pas re-copiés (ni conflit de renommage)")
    void reprise_saute_la_copie_des_originaux_deja_presents() throws IOException {
        // Simule un import interrompu APRÈS copie + renommage : bruts/ contient déjà les deux originaux
        // sous leur nom préfixé (R6). Sans passage en base (R5 ne bloque donc pas) → c'est une reprise.
        Path bruts = racine.resolve("ws").resolve(prefixe.nomDossierSession()).resolve("bruts");
        Files.createDirectories(bruts);
        for (String src : List.of("PaRecPR1925492_20260422_203922.wav", "PaRecPR1925492_20260422_204326.wav")) {
            ecrireWav(bruts.resolve(Renommeur.nomApresRenommage(src, prefixe)));
        }

        // CopieProtegee instrumentée : compte les copies de .wav effectivement réalisées.
        AtomicInteger copiesWav = new AtomicInteger();
        CopieProtegee copieComptee = new CopieProtegee() {
            @Override
            public Path copier(Path s, Path d) {
                if (s.getFileName().toString().toLowerCase().endsWith(".wav")) {
                    copiesWav.incrementAndGet();
                }
                return super.copier(s, d);
            }
        };
        ServiceImport reprise = new ServiceImport(
                new InspecteurDossier(new AnalyseurLogPR()),
                OutilsImport.reels(copieComptee, new Renommeur(), new TransformationAudio()),
                new AgregatImportDao(source),
                new UniteDeTravail(source),
                new Workspace(racine.resolve("ws")),
                new HorlogeFigee(LocalDate.of(2026, 5, 31)),
                idPassage -> 0,
                new ServiceSauvegarde(source, new HorlogeFigee(LocalDate.of(2026, 5, 31))),
                Optional.empty());

        ResultatImport resultat = reprise.importer(sd, idPoint, prefixe);

        // Aucune re-copie (les deux originaux étaient déjà matérialisés), et l'agrégat est complet.
        assertThat(copiesWav.get())
                .as("les originaux déjà présents ne sont pas re-copiés")
                .isZero();
        assertThat(resultat.nombreOriginaux()).isEqualTo(2);
        assertThat(resultat.nombreSequences()).isEqualTo(2);
        assertThat(resultat.passage().statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
    }

    @Test
    @DisplayName("#231 : reprise sécurisée — un original présent au mauvais contenu (empreinte ≠ source) est re-copié")
    void reprise_recopie_un_original_au_contenu_different() throws IOException {
        Path bruts = racine.resolve("ws").resolve(prefixe.nomDossierSession()).resolve("bruts");
        Files.createDirectories(bruts);
        // Original A : déjà présent et FIDÈLE (copie d'un import antérieur) -> doit être sauté.
        ecrireWav(bruts.resolve(Renommeur.nomApresRenommage("PaRecPR1925492_20260422_203922.wav", prefixe)));
        // Original B : présent au BON nom mais au MAUVAIS contenu (corruption silencieuse) -> re-copié.
        Files.write(
                bruts.resolve(Renommeur.nomApresRenommage("PaRecPR1925492_20260422_204326.wav", prefixe)),
                new byte[] {1, 2, 3});

        AtomicInteger copiesWav = new AtomicInteger();
        CopieProtegee copieComptee = new CopieProtegee() {
            @Override
            public Path copier(Path s, Path d) {
                if (s.getFileName().toString().toLowerCase().endsWith(".wav")) {
                    copiesWav.incrementAndGet();
                }
                return super.copier(s, d);
            }
        };
        ServiceImport reprise = new ServiceImport(
                new InspecteurDossier(new AnalyseurLogPR()),
                OutilsImport.reels(copieComptee, new Renommeur(), new TransformationAudio()),
                new AgregatImportDao(source),
                new UniteDeTravail(source),
                new Workspace(racine.resolve("ws")),
                new HorlogeFigee(LocalDate.of(2026, 5, 31)),
                idPassage -> 0,
                new ServiceSauvegarde(source, new HorlogeFigee(LocalDate.of(2026, 5, 31))),
                Optional.empty());

        ResultatImport resultat = reprise.importer(sd, idPoint, prefixe);

        // Seul l'original corrompu a été re-copié (le fidèle est sauté) ; l'agrégat final est correct.
        assertThat(copiesWav.get())
                .as("le fidèle est sauté, le corrompu est re-copié")
                .isEqualTo(1);
        assertThat(resultat.nombreOriginaux()).isEqualTo(2);
        assertThat(resultat.nombreSequences()).isEqualTo(2);
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
                .hasMessageContaining("existe déjà");
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
        // L'émission de progression est sérialisée + comptée sous verrou → compteurs monotones 1..N
        // (libellé sans le suffixe « · <fichier courant> » #146), même si l'ordre d'achèvement des
        // threads virtuels est quelconque.
        assertThat(points)
                .filteredOn(p -> p.libelle().startsWith("Transformation"))
                .extracting(p -> p.libelle().replaceAll(" · .*$", ""))
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
    @DisplayName("#155 : un original illisible est rejeté et consigné, l'import réussit avec les autres")
    void original_illisible_est_rejete_et_consigne() throws IOException {
        Path corrompu = racine.resolve("sd-corrompu");
        Files.createDirectories(corrompu);
        Files.writeString(corrompu.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        ecrireWav(corrompu.resolve("PaRecPR1925492_20260422_203922.wav")); // WAV valide
        Files.writeString(corrompu.resolve("PaRecPR1925492_20260422_204326.wav"), "pas un WAV"); // illisible

        ResultatImport resultat = service.importer(corrompu, idPoint, prefixe);

        // L'import résilient réussit avec le WAV valide ; l'illisible est rejeté + consigné, pas bloquant.
        assertThat(resultat.nombreOriginaux()).isEqualTo(1);
        assertThat(originalDao.findBySession(resultat.session().id())).hasSize(1);
        RapportImport rapport = resultat.rapport();
        assertThat(rapport.compte(StatutImportFichier.IMPORTE)).isEqualTo(1);
        assertThat(rapport.compte(StatutImportFichier.REJETE)).isEqualTo(1);
        assertThat(rapport.lignes())
                .filteredOn(l -> l.statut() == StatutImportFichier.REJETE)
                .singleElement()
                .satisfies(l -> assertThat(l.nomFichier()).contains("204326"));
    }

    @Test
    @DisplayName("#155 : aucun original importable (tous illisibles) → import refusé, session nettoyée")
    void tous_originaux_illisibles_refuse_l_import() throws IOException {
        Path corrompu = racine.resolve("sd-tout-corrompu");
        Files.createDirectories(corrompu);
        Files.writeString(corrompu.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(corrompu.resolve("PaRecPR1925492_20260422_203922.wav"), "pas un WAV");

        assertThatThrownBy(() -> service.importer(corrompu, idPoint, prefixe))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Aucun enregistrement original");
        assertThat(service.numeroPassageDejaUtilise(idPoint, 2026, 2)).isFalse();
    }

    @Test
    @DisplayName("#155 : une erreur d'écriture du workspace est FATALE (pas un rejet) et nettoie la session")
    void erreur_ecriture_workspace_est_fatale() throws IOException {
        // Piège : « transformes » est un FICHIER → la création du dossier de sortie échoue (erreur d'E/S
        // workspace). Cela ne doit PAS être classé « fichier rejeté » mais remonter et nettoyer la session.
        Path session = Files.createDirectories(racine.resolve("ws").resolve(prefixe.nomDossierSession()));
        Files.writeString(session.resolve("transformes"), "collision");

        assertThatThrownBy(() -> service.importer(sd, idPoint, prefixe))
                .isInstanceOf(java.io.UncheckedIOException.class);
        assertThat(service.numeroPassageDejaUtilise(idPoint, 2026, 2)).isFalse();
        assertThat(session).doesNotExist();
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
                    .hasSize(2); // 2 originaux × 1 tranche (1,5 s < 5 s)
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
                .hasMessageContaining("existe déjà");
    }

    @Test
    @DisplayName("#108 : le pré-contrôle R5 détecte le quadruplet pris et propose le prochain n° libre")
    void precontrole_numero_passage_deja_pris() {
        // Aucun passage encore : rien n'est pris, le premier n° libre est 1.
        assertThat(service.numeroPassageDejaUtilise(idPoint, 2026, 2)).isFalse();
        assertThat(service.prochainNumeroPassageLibre(idPoint, 2026)).isEqualTo(1);

        service.importer(sd, idPoint, prefixe); // crée le passage n° 2 / 2026 pour ce point

        assertThat(service.numeroPassageDejaUtilise(idPoint, 2026, 2)).isTrue();
        assertThat(service.numeroPassageDejaUtilise(idPoint, 2026, 1)).isFalse(); // autre n°
        assertThat(service.numeroPassageDejaUtilise(idPoint, 2025, 2)).isFalse(); // autre année
        // Premier trou libre : le passage n° 2 existe, le n° 1 reste donc disponible (comble le trou).
        assertThat(service.prochainNumeroPassageLibre(idPoint, 2026)).isEqualTo(1);
        // Rattachement incomplet : aucun signalement (pas d'exception SQL brute).
        assertThat(service.numeroPassageDejaUtilise(null, 2026, 2)).isFalse();
        assertThat(service.numeroPassageDejaUtilise(idPoint, 2026, 0)).isFalse();
    }

    @Test
    @DisplayName(
            "#… : prochainBlocPassagesLibre — 1,3,5,7 pris → premier bloc de N consécutifs libres, sans casser la consécutivité")
    void prochain_bloc_consecutif_libre_evite_les_trous() {
        PassageDao passageDao = new PassageDao(source);
        enregistreurDao.insert(new Enregistreur("9999999", "V1.01", null));
        for (int numero : new int[] {1, 3, 5, 7}) {
            passageDao.insert(new Passage(
                    null,
                    numero,
                    2026,
                    "2026-04-22",
                    "20:25:00",
                    "07:47:00",
                    null,
                    StatutWorkflow.TRANSFORME,
                    null,
                    null,
                    null,
                    null,
                    idPoint,
                    "9999999"));
        }

        // Bloc de 1 : comble le premier trou (2). Bloc de 2 ou 3 : les trous 2/4/6 sont isolés → 8.
        assertThat(service.prochainBlocPassagesLibre(idPoint, 2026, 1)).isEqualTo(2);
        assertThat(service.prochainNumeroPassageLibre(idPoint, 2026)).isEqualTo(2); // équivaut à bloc(1)
        assertThat(service.prochainBlocPassagesLibre(idPoint, 2026, 2)).isEqualTo(8);
        assertThat(service.prochainBlocPassagesLibre(idPoint, 2026, 3)).isEqualTo(8);
        // Sans passage : le bloc démarre à 1 quelle que soit la taille.
        assertThat(service.prochainBlocPassagesLibre(idPoint, 2025, 3)).isEqualTo(1);
    }

    @Test
    @DisplayName("#147 : nuitDejaImportee détecte une nuit déjà en base (même enregistreur + même date)")
    void detection_nuit_deja_importee() {
        // Avant tout import : rien en base.
        assertThat(service.nuitDejaImportee(SERIE, "2026-04-22")).isEmpty();

        service.importer(sd, idPoint, prefixe); // crée un passage n° 2 / 2026, nuit du 2026-04-22

        assertThat(service.nuitDejaImportee(SERIE, "2026-04-22"))
                .as("la nuit importée est détectée")
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.numeroPassage()).isEqualTo(2);
                    assertThat(p.annee()).isEqualTo(2026);
                    assertThat(p.carre()).isEqualTo("640380"); // rattachement remonté (carré + point)
                    assertThat(p.codePoint()).isEqualTo("Z1");
                });
        // Autre enregistreur, autre date ou identité nulle : aucune détection (pas d'exception SQL brute).
        assertThat(service.nuitDejaImportee("0000000", "2026-04-22")).isEmpty();
        assertThat(service.nuitDejaImportee(SERIE, "2026-04-23")).isEmpty();
        assertThat(service.nuitDejaImportee(null, "2026-04-22")).isEmpty();
        assertThat(service.nuitDejaImportee(SERIE, null)).isEmpty();
    }

    @Test
    @DisplayName("#214 : ecraserEtImporter supprime le passage existant (cascade) puis réimporte sans doublon")
    void ecraser_et_reimporter_supprime_en_cascade() {
        ResultatImport premier = service.importer(sd, idPoint, prefixe);
        Long idPassageInitial = premier.passage().id();
        Long idSessionInitiale =
                sessionDao.trouverParPassage(idPassageInitial).orElseThrow().id();
        int sequencesInitiales = sequenceDao.findBySession(idSessionInitiale).size();
        assertThat(sequencesInitiales).isPositive();
        // Le quadruplet est pris : un import normal serait refusé (R5).
        assertThat(service.numeroPassageDejaUtilise(idPoint, 2026, 2)).isTrue();
        assertThat(service.apercuEcrasement(idPoint, 2026, 2).sequences()).isEqualTo(sequencesInitiales);

        // Écrasement : supprime le passage n° 2 existant (cascade) puis réimporte la nuit.
        ResultatImport reimport = service.ecraserEtImporter(sd, idPoint, prefixe, p -> {}, JetonAnnulation.neutre());

        // Un nouveau passage a remplacé l'ancien (id différent), et il n'y a pas de doublon de la nuit.
        assertThat(reimport.passage().id()).isNotEqualTo(idPassageInitial);
        assertThat(reimport.passage().statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(service.nuitDejaImportee(SERIE, "2026-04-22")).hasSize(1);
        // Cascade : l'ancien passage, son ancienne session et ses séquences ont bien disparu (pas d'orphelins).
        assertThat(sessionDao.trouverParPassage(idPassageInitial)).isEmpty();
        assertThat(sequenceDao.findBySession(idSessionInitiale)).isEmpty();
        // Le nouveau passage a régénéré le même nombre de séquences.
        assertThat(service.apercuEcrasement(idPoint, 2026, 2).sequences()).isEqualTo(sequencesInitiales);
        // L'écrasement est un REMPLACEMENT, pas un doublon : le rapport ne le signale pas comme doublon.
        assertThat(reimport.rapport().aDoublonDeNuit())
                .as("un écrasement remplace, il n'est pas un doublon de nuit")
                .isFalse();
        // #148 : avant la suppression destructive, une sauvegarde automatique de la base a été écrite.
        assertThat(source.workspace().racine().resolve("sauvegardes"))
                .as("l'écrasement doit d'abord sauvegarder la base (#148)")
                .isDirectoryContaining("glob:**/vigiechiro-sauvegarde-*.db");
    }

    @Test
    @DisplayName("#214 : apercuEcrasement interroge le compteur de validations pour le passage écrasé, 0 si n° libre")
    void compte_les_validations_du_passage_ecrase() {
        service.importer(sd, idPoint, prefixe); // crée le passage n° 2 au quadruplet courant
        // Compteur de validations doublé : renvoie 3 pour tout passage résolu (id non nul).
        ServiceImport avecCompteur = new ServiceImport(
                new InspecteurDossier(new AnalyseurLogPR()),
                OutilsImport.reels(new CopieProtegee(), new Renommeur(), new TransformationAudio()),
                new AgregatImportDao(source),
                new UniteDeTravail(source),
                new Workspace(racine.resolve("ws")),
                new HorlogeFigee(LocalDate.of(2026, 5, 31)),
                idPassage -> 3,
                new ServiceSauvegarde(source, new HorlogeFigee(LocalDate.of(2026, 5, 31))),
                Optional.empty());

        // Quadruplet déjà pris → le passage est résolu et interrogé (3) ; n° libre → aucun passage (0).
        assertThat(avecCompteur.apercuEcrasement(idPoint, 2026, 2).validations())
                .isEqualTo(3);
        assertThat(avecCompteur.apercuEcrasement(idPoint, 2026, 99).validations())
                .isZero();
    }

    @Test
    @DisplayName("#214/#147 : le rapport signale un doublon quand la nuit est déjà importée")
    void rapport_signale_doublon_de_nuit() {
        ResultatImport premier = service.importer(sd, idPoint, prefixe); // n° 2
        assertThat(premier.rapport().aDoublonDeNuit())
                .as("1er import d'une nuit neuve : aucun doublon")
                .isFalse();

        // Réimport de la MÊME nuit (même série + date, issues du journal/WAV) à un n° LIBRE : nouveau
        // passage, mais doublon de la nuit déjà en base.
        ResultatImport second = service.importer(sd, idPoint, new Prefixe("640380", 2026, 3, "Z1"));

        assertThat(second.rapport().aDoublonDeNuit())
                .as("réimport d'une nuit déjà en base : doublon signalé")
                .isTrue();
        assertThat(second.rapport().doublonsDeNuit())
                .as("le doublon référence le passage initial (n° 2)")
                .anySatisfy(p -> assertThat(p.numeroPassage()).isEqualTo(2));
    }

    @Test
    @DisplayName("#214 : écraser avec une SD réduite n'importe QUE les WAV courants (pas de fichier fantôme)")
    void ecraser_avec_sd_reduite_ne_resuscite_pas_les_anciens_wav() throws IOException {
        // 1er import : la SD complète a deux originaux.
        ResultatImport premier = service.importer(sd, idPoint, prefixe);
        Long idSessionInitiale = sessionDao
                .trouverParPassage(premier.passage().id())
                .orElseThrow()
                .id();
        assertThat(originalDao.findBySession(idSessionInitiale)).hasSize(2);

        // Nouvelle SD pour le MÊME quadruplet, mais ne contenant qu'UN des deux WAV (l'autre a disparu).
        Path sdReduite = racine.resolve("sd-reduite");
        Files.createDirectories(sdReduite);
        Files.writeString(sdReduite.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        ecrireWav(sdReduite.resolve("PaRecPR1925492_20260422_203922.wav"));

        // Écrasement : le dossier de session déterministe est réutilisé ; sans remise au propre, le WAV
        // fantôme du 1er import (204326) y traînerait et serait réimporté. On vérifie qu'il n'en est rien.
        ResultatImport reimport =
                service.ecraserEtImporter(sdReduite, idPoint, prefixe, p -> {}, JetonAnnulation.neutre());

        Long idSessionReimport = sessionDao
                .trouverParPassage(reimport.passage().id())
                .orElseThrow()
                .id();
        assertThat(originalDao.findBySession(idSessionReimport))
                .as("seul le WAV présent sur la nouvelle SD est importé, pas le fantôme de l'ancien import")
                .hasSize(1);
    }

    @Test
    @DisplayName("#214 : un écrasement ANNULÉ ne détruit pas l'ancien passage (remplacement atomique)")
    void ecraser_annule_preserve_l_ancien_passage() {
        ResultatImport premier = service.importer(sd, idPoint, prefixe);
        Long idPassageInitial = premier.passage().id();
        Long idSessionInitiale =
                sessionDao.trouverParPassage(idPassageInitial).orElseThrow().id();
        int sequencesInitiales = sequenceDao.findBySession(idSessionInitiale).size();
        assertThat(sequencesInitiales).isPositive();

        // Jeton déjà annulé : l'écrasement échoue APRÈS la mise de côté de l'ancienne session, mais AVANT
        // la suppression en base (différée dans la transaction d'insertion). L'ancien doit survivre intact.
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler();
        assertThatThrownBy(() -> service.ecraserEtImporter(sd, idPoint, prefixe, p -> {}, jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        // L'ancien passage, sa session et ses séquences sont préservés (rien perdu).
        assertThat(service.numeroPassageDejaUtilise(idPoint, 2026, 2)).isTrue();
        assertThat(sessionDao.trouverParPassage(idPassageInitial)).isPresent();
        assertThat(sequenceDao.findBySession(idSessionInitiale)).hasSize(sequencesInitiales);
        // Les fichiers physiques de l'ancienne session sont restaurés (un réimport reste possible).
        assertThat(racine.resolve("ws").resolve(prefixe.nomDossierSession()))
                .as("l'ancienne session physique est remise en place après l'annulation")
                .exists();
        assertThat(service.apercuEcrasement(idPoint, 2026, 2).sequences()).isEqualTo(sequencesInitiales);
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
    @DisplayName("#107 : un dossier sans journal LogPR s'importe en mode dégradé (série déduite des WAV)")
    void sans_journal_import_degrade() throws IOException {
        Files.delete(sd.resolve("LogPR" + SERIE + ".txt"));

        ResultatImport resultat = service.importer(sd, idPoint, prefixe);

        // L'import aboutit malgré l'absence de journal : l'enregistreur est déduit du nom des WAV
        // (PaRecPR<série>_…), le passage est créé au statut Transformé.
        assertThat(resultat.passage().statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(resultat.passage().idEnregistreur()).isEqualTo(SERIE);
        assertThat(resultat.numeroSerieEnregistreur()).isEqualTo(SERIE);
        assertThat(resultat.passage().dateEnregistrement()).isEqualTo("2026-04-22"); // date issue du nom de WAV
        // Une **trace synthétique** de journal est écrite (sinon la préparation du lot resterait bloquée) :
        // elle porte le marqueur d'anomalie « dégradé », pour assumer et tracer le mode dégradé.
        assertThat(journalDao.trouverParSession(resultat.session().id()))
                .as("une trace de journal synthétique est déposée en mode dégradé")
                .isPresent()
                .get()
                .satisfies(j -> {
                    assertThat(j.cheminFichier()).endsWith("JOURNAL-ABSENT.txt");
                    assertThat(j.anomaliesDetectees()).contains("dégradé");
                });
        // L'enregistreur déduit est bien committé (clé naturelle = série du nom de fichier).
        assertThat(enregistreurDao.findById(SERIE)).isPresent();
    }

    @Test
    @DisplayName("#107 : dossier mélangé SANS journal → import déterministe sous la PREMIÈRE série triée")
    void sans_journal_dossier_melange_premiere_identite() throws IOException {
        Path melange = racine.resolve("sd-melange-sans-journal");
        Files.createDirectories(melange);
        ecrireWav(melange.resolve("PaRecPR1925492_20260422_203922.wav"));
        ecrireWav(melange.resolve("PaRecPR1648011_20260422_210000.wav")); // autre série, même nuit
        // Aucun LogPR : mode dégradé ; deux séries → mélange (signalé non bloquant à l'inspection).

        ResultatImport resultat = service.importer(melange, idPoint, prefixe);

        // Choix **déterministe et documenté** (JournalDeRepli) : la PREMIÈRE série triée
        // (« 1648011 » < « 1925492 ») fait foi en l'absence de journal autoritatif.
        assertThat(resultat.passage().idEnregistreur()).isEqualTo("1648011");
        assertThat(resultat.passage().statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
    }

    // --- Helpers (autonomes) ----------------------------------------------------

    @Test
    @DisplayName("Import multi-nuits : une nuit = un passage (n° consécutifs, dates propres, même point)")
    void import_multi_nuits_cree_un_passage_par_nuit() throws IOException {
        Path carte = preparerCarteMultiNuits(racine.resolve("sd-multi"));
        List<NuitDetectee> nuits =
                new InspecteurDossier(new AnalyseurLogPR()).inspecter(carte).partitionNuits();
        assertThat(nuits).hasSize(3);

        int base = service.prochainNumeroPassageLibre(idPoint, 2026); // 1 : aucun passage encore
        List<NuitAImporter> aImporter = new ArrayList<>();
        for (int i = 0; i < nuits.size(); i++) {
            aImporter.add(new NuitAImporter(base + i, nuits.get(i)));
        }

        ResultatImportMultiNuits resultat = service.importerNuits(
                carte, idPoint, prefixe, aImporter, true, progression -> {}, JetonAnnulation.neutre());

        assertThat(resultat.nombrePassages()).isEqualTo(3);
        // Chaque nuit devient un passage : n° consécutifs 1,2,3 et dates propres croissantes.
        assertThat(resultat.parNuit())
                .extracting(r -> r.passage().numeroPassage())
                .containsExactly(1, 2, 3);
        assertThat(resultat.parNuit())
                .extracting(r -> r.passage().dateEnregistrement())
                .containsExactly("2026-04-22", "2026-04-23", "2026-04-24");
        // Tous rattachés au même point, persistés (statut Transformé + session en base).
        assertThat(resultat.parNuit()).allSatisfy(r -> {
            assertThat(r.passage().id()).isNotNull();
            assertThat(r.passage().idPoint()).isEqualTo(idPoint);
            assertThat(r.passage().statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
            assertThat(sessionDao.trouverParPassage(r.passage().id())).isPresent();
        });
    }

    @Test
    @DisplayName("Import multi-nuits : un sous-ensemble (exclusion) crée un passage par nuit incluse")
    void import_multi_nuits_sous_ensemble() throws IOException {
        Path carte = preparerCarteMultiNuits(racine.resolve("sd-sous-ensemble"));
        List<NuitDetectee> nuits =
                new InspecteurDossier(new AnalyseurLogPR()).inspecter(carte).partitionNuits();

        // On exclut la nuit du milieu : seules les nuits 0 et 2 sont importées (n° consécutifs 1,2).
        List<NuitAImporter> aImporter = List.of(new NuitAImporter(1, nuits.get(0)), new NuitAImporter(2, nuits.get(2)));

        ResultatImportMultiNuits resultat = service.importerNuits(
                carte, idPoint, prefixe, aImporter, true, progression -> {}, JetonAnnulation.neutre());

        assertThat(resultat.nombrePassages()).isEqualTo(2);
        assertThat(resultat.parNuit())
                .extracting(r -> r.passage().dateEnregistrement())
                .containsExactly("2026-04-22", "2026-04-24");
    }

    @Test
    @DisplayName(
            "#1696 : import multi-nuits à log unique → le relevé climatique de chaque session est restreint à sa nuit")
    void import_multi_nuits_releve_climatique_par_nuit() throws IOException {
        Path carte = preparerCarteMultiNuits(racine.resolve("sd-multi-thlog"));
        // Un THLog UNIQUE couvrant les trois nuits (bascule midi : un relevé d'avant midi = nuit de la veille).
        String thlog = "Date\tHour\tTemperature\tHumidity\n"
                + "22/04/2026\t22:30:00\t+16.1\t70\n"
                + "23/04/2026\t05:30:00\t+11.9\t84\n"
                + "23/04/2026\t22:30:00\t+15.0\t72\n"
                + "24/04/2026\t05:30:00\t+10.0\t88\n"
                + "24/04/2026\t22:30:00\t+14.0\t75\n";
        Files.writeString(carte.resolve("PaRecPR1925492_THLog.csv"), thlog, StandardCharsets.UTF_8);
        List<NuitDetectee> nuits =
                new InspecteurDossier(new AnalyseurLogPR()).inspecter(carte).partitionNuits();
        List<NuitAImporter> aImporter = List.of(new NuitAImporter(1, nuits.get(0)), new NuitAImporter(2, nuits.get(1)));

        ResultatImportMultiNuits resultat = service.importerNuits(
                carte, idPoint, prefixe, aImporter, true, progression -> {}, JetonAnnulation.neutre());

        // Nuit du 22/04 : uniquement ses mesures (soir + petit matin), pas celles des autres nuits.
        assertThat(lignesReleve(resultat.parNuit().get(0).passage().id()))
                .containsExactly(
                        "Date\tHour\tTemperature\tHumidity",
                        "22/04/2026\t22:30:00\t+16.1\t70",
                        "23/04/2026\t05:30:00\t+11.9\t84");
        // Nuit du 23/04 : ses mesures à elle, disjointes de la nuit précédente.
        assertThat(lignesReleve(resultat.parNuit().get(1).passage().id()))
                .containsExactly(
                        "Date\tHour\tTemperature\tHumidity",
                        "23/04/2026\t22:30:00\t+15.0\t72",
                        "24/04/2026\t05:30:00\t+10.0\t88");
    }

    /// Lignes du relevé climatique THLog persisté pour le passage `idPassage` (fichier de sa session).
    private List<String> lignesReleve(Long idPassage) throws IOException {
        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        String chemin = releveDao.trouverParSession(idSession).orElseThrow().cheminFichier();
        return Files.readAllLines(Path.of(chemin));
    }

    @Test
    @DisplayName(
            "#1696 : import multi-nuits à log unique → le journal de chaque session ne contient que les évènements de sa nuit")
    void import_multi_nuits_journal_par_nuit() throws IOException {
        Path carte = racine.resolve("sd-multi-journal");
        Files.createDirectories(carte);
        // Journal UNIQUE : lignes de déploiement (22/04) + un réveil daté par nuit (bascule midi).
        String log = LOG
                + "23/04/26 - 03:00:00 PR1925492 Wakeup by WATCHDOG Cpt3 nuit22\n"
                + "24/04/26 - 02:00:00 PR1925492 Wakeup by WATCHDOG Cpt5 nuit23\n";
        Files.writeString(carte.resolve("LogPR1925492.txt"), log, StandardCharsets.UTF_8);
        for (String jour : List.of("20260422", "20260423")) {
            ecrireWav(carte.resolve("PaRecPR1925492_" + jour + "_203922.wav"));
            ecrireWav(carte.resolve("PaRecPR1925492_" + jour + "_204326.wav"));
        }
        List<NuitDetectee> nuits =
                new InspecteurDossier(new AnalyseurLogPR()).inspecter(carte).partitionNuits();
        List<NuitAImporter> aImporter = List.of(new NuitAImporter(1, nuits.get(0)), new NuitAImporter(2, nuits.get(1)));

        ResultatImportMultiNuits resultat = service.importerNuits(
                carte, idPoint, prefixe, aImporter, true, progression -> {}, JetonAnnulation.neutre());

        // Le réveil du 23/04 03:00 revient à la nuit du 22 ; celui du 24/04 02:00 à la nuit du 23.
        assertThat(anomaliesSession(resultat.parNuit().get(0).passage().id()))
                .contains("nuit22")
                .doesNotContain("nuit23");
        assertThat(anomaliesSession(resultat.parNuit().get(1).passage().id()))
                .contains("nuit23")
                .doesNotContain("nuit22");
    }

    /// Anomalies (JSON `detected_anomalies`) persistées pour la session du passage `idPassage`.
    private String anomaliesSession(Long idPassage) {
        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        return journalDao.trouverParSession(idSession).orElseThrow().anomaliesDetectees();
    }

    @Test
    @DisplayName("Import multi-nuits : un n° déjà pris est refusé AVANT tout import (échec rapide, pas de demi-groupe)")
    void import_multi_nuits_precontrole_numero_pris() throws IOException {
        Path carte = preparerCarteMultiNuits(racine.resolve("sd-conflit"));
        List<NuitDetectee> nuits =
                new InspecteurDossier(new AnalyseurLogPR()).inspecter(carte).partitionNuits();

        // La nuit 0 est déjà importée au passage n°1.
        service.importerNuits(
                carte,
                idPoint,
                prefixe,
                List.of(new NuitAImporter(1, nuits.get(0))),
                true,
                progression -> {},
                JetonAnnulation.neutre());

        // On retente avec [1, 2] : le n°1 est pris → refus avant toute copie, le n°2 n'est jamais créé.
        List<NuitAImporter> conflit = List.of(new NuitAImporter(1, nuits.get(1)), new NuitAImporter(2, nuits.get(2)));
        assertThatThrownBy(() -> service.importerNuits(
                        carte, idPoint, prefixe, conflit, true, progression -> {}, JetonAnnulation.neutre()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("passage n°1");
        assertThat(service.numeroPassageDejaUtilise(idPoint, 2026, 2)).isFalse();
    }

    private Path preparerCarteSD(Path dossier) throws IOException {
        Files.createDirectories(dossier);
        Files.writeString(dossier.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(dossier.resolve("PaRecPR1925492_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
        ecrireWav(dossier.resolve("PaRecPR1925492_20260422_203922.wav"));
        ecrireWav(dossier.resolve("PaRecPR1925492_20260422_204326.wav"));
        return dossier;
    }

    /// Carte SD **multi-nuits** : trois soirées distinctes (2 WAV chacune) du même enregistreur, plus le
    /// journal LogPR de la carte. Chaque soirée (horodatage > midi) forme une nuit distincte.
    private Path preparerCarteMultiNuits(Path dossier) throws IOException {
        Files.createDirectories(dossier);
        Files.writeString(dossier.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        for (String jour : List.of("20260422", "20260423", "20260424")) {
            ecrireWav(dossier.resolve("PaRecPR1925492_" + jour + "_203922.wav"));
            ecrireWav(dossier.resolve("PaRecPR1925492_" + jour + "_204326.wav"));
        }
        return dossier;
    }

    private static void ecrireWav(Path fichier) throws IOException {
        ecrireWav(fichier, FREQUENCE_WAV);
    }

    /// Écrit un WAV mono 16 bits de `TRAMES` trames avec `frequenceEnTeteHz` dans l'en-tête. Un brut PR
    /// « déjà expansé ×10 » porte l'en-tête à Fe/10 (ex. 38400) alors que le log déclare Fe (384000).
    private static void ecrireWav(Path fichier, int frequenceEnTeteHz) throws IOException {
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
        buf.putInt(frequenceEnTeteHz);
        buf.putInt(frequenceEnTeteHz * 2);
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

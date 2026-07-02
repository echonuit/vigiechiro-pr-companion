package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.StatutImportFichier;
import fr.univ_amu.iut.importation.viewmodel.EtatImport;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Test E2E du parcours P2 « Importer une nuit d'enregistrement »**, piloté **sans IHM** : on
/// rejoue les étapes du parcours à travers le **ViewModel réel** de l'assistant M-Import
/// ([ImportationViewModel]) et les **services réels** (`ServiceImport`, `ServiceSites`) câblés par
/// l'injecteur applicatif [RacineInjecteur], sur une **base jetable** migrée par [MigrationSchema] et
/// un **workspace temporaire**. Aucune fenêtre n'est ouverte : on n'instancie ni `Stage`, ni `Scene`,
/// ni FXML (le pilotage à l'écran de cet assistant, dont le `DirectoryChooser` natif, est couvert par
/// les tests TestFX de la feature).
///
/// Le fil du parcours suit le brief :
///
/// 1. **Inspecter** la carte SD en lecture seule (R9) → journal détecté, n° de série extrait, relevé
///    climatique présent, N originaux, état de nommage `BRUT`.
/// 2. **Rattacher** la nuit à un site / point / année / n° de passage → le bouton « Importer »
///    s'active.
/// 3. **Importer** (copie protégée + renommage R6/R7 + transformation R10/R11 + persistance O7) → un
///    [fr.univ_amu.iut.passage.model.Passage] est créé au statut [StatutWorkflow#TRANSFORME].
///
/// On vérifie le **résultat métier** à chaque jalon, jusqu'à relire le passage **en base** (la base
/// est la source de vérité). Un second cas couvre le garde-fou **R5** : réimporter le même quadruplet
/// `(point, année, n° de passage)` est **bloqué en amont** par le pré-contrôle R5 (#108) — dès le
/// rattachement, le bouton « Importer » se désactive et aucun import n'est lancé, donc la base reste
/// intacte.
class ParcoursImporterNuitE2ETest {

    private static final String ID_USER = "u-e2e-import";
    private static final String SERIE = "1925492";
    private static final String CARRE = "640380";
    private static final String CODE_POINT = "A1";
    private static final int ANNEE = 2026;
    private static final int FREQUENCE_WAV = 384_000; // Hz, multiple de 10 (R10)
    private static final int TRAMES = 576_000;
    private static final String LOG = "22/04/26 - 16:02:20 PR1925492 Demarrage Passive Recorder numero de serie"
            + " 1925492, V1.01, CPU 600000000, T4.1\n";

    private Injector injector;
    private SourceDeDonnees source;
    private Site site;
    private PointDEcoute point;
    private Path dossierSource;

    @BeforeEach
    void preparer() throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-import");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        // Un seul utilisateur en base → idUtilisateurCourant (singleton) le désigne ; le site qu'on lui
        // rattache sera donc bien listé par l'assistant d'import. À insérer AVANT de résoudre le
        // ViewModel (qui mémorise l'utilisateur courant).
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur E2E"));
        ServiceSites sites = injector.getInstance(ServiceSites.class);
        site = sites.creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        point = sites.ajouterPoint(site.id(), CODE_POINT, 43.4010, -1.5740, "Près du chêne");

        dossierSource = creerNuitSynthetique(workspace.resolve("sd"));
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("P2 : inspecter une SD, rattacher la nuit, importer → un passage Transformé en base")
    void parcours_complet_inspecter_rattacher_importer() {
        ImportationViewModel vm = injector.getInstance(ImportationViewModel.class);

        // Jalon 1 — Inspection (lecture seule) : le dossier source est photographié sans rien y écrire.
        vm.inspection().dossierSourceProperty().set(dossierSource);
        vm.inspecter();
        assertThat(vm.inspection().estInspecte()).isTrue();
        assertThat(vm.inspection().aUnJournalProperty().get()).isTrue();
        assertThat(vm.inspection().aUnReleveClimatiqueProperty().get()).isTrue();
        assertThat(vm.inspection().nombreOriginauxProperty().get()).isEqualTo(1);
        assertThat(vm.inspection().etatNommageProperty().get()).isEqualTo(EtatNommage.BRUT);
        assertThat(vm.inspection().resumeJournalProperty().get()).contains(SERIE);
        // Tant que le rattachement n'est pas complet, l'import reste impossible.
        assertThat(vm.peutImporter().get()).isFalse();

        // Jalon 2 — Rattachement : le site de l'utilisateur est proposé, on choisit site + point + année
        // + n° de passage, puis le bouton « Importer » s'active.
        vm.chargerSites();
        assertThat(vm.rattachement().sites()).extracting(Site::id).containsExactly(site.id());
        vm.rattachement().siteSelectionneProperty().set(site);
        assertThat(vm.rattachement().points()).extracting(PointDEcoute::id).containsExactly(point.id());
        vm.rattachement().pointSelectionneProperty().set(point);
        vm.rattachement().anneeProperty().set(ANNEE);
        vm.rattachement().numeroPassageProperty().set(1);
        assertThat(vm.peutImporter().get()).isTrue();

        // Jalon 3 — Import : copie protégée + renommage + transformation + persistance atomique.
        vm.importer();
        assertThat(vm.etatProperty().get()).isEqualTo(EtatImport.TERMINE);

        ResultatImport resultat = vm.resultatProperty().get();
        assertThat(resultat).isNotNull();
        assertThat(resultat.numeroSerieEnregistreur()).isEqualTo(SERIE);
        assertThat(resultat.nombreOriginaux()).isEqualTo(1);
        assertThat(resultat.nombreSequences()).isPositive();
        assertThat(resultat.passage().statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);

        // Jalon final — Vérification métier en base (source de vérité) : le passage persisté est bien
        // rattaché au point et au statut Transformé (état final d'un import complet).
        Long idPassage = resultat.passage().id();
        assertThat(idPassage).isNotNull();
        var passagePersiste = new PassageDao(source).findById(idPassage).orElseThrow();
        assertThat(passagePersiste.statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(passagePersiste.idPoint()).isEqualTo(point.id());
        assertThat(passagePersiste.annee()).isEqualTo(ANNEE);
        assertThat(passagePersiste.numeroPassage()).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "P2 #155 : import résilient — un WAV illisible est rejeté+consigné, l'import réussit et le rapport le liste")
    void parcours_import_resilient_avec_rapport() throws Exception {
        // Nuit avec un WAV valide ET un WAV illisible (contenu non-WAV portant l'extension .wav).
        Path nuit = Files.createTempDirectory("vc-e2e-resilient");
        Files.writeString(nuit.resolve("LogPR" + SERIE + ".txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(nuit.resolve("PaRecPR" + SERIE + "_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
        ecrireWav(nuit.resolve("PaRecPR" + SERIE + "_20260422_203922.wav")); // valide
        Files.writeString(nuit.resolve("PaRecPR" + SERIE + "_20260422_204500.wav"), "pas un WAV"); // illisible

        ImportationViewModel vm = injector.getInstance(ImportationViewModel.class);
        vm.inspection().dossierSourceProperty().set(nuit);
        vm.inspecter();
        vm.chargerSites();
        vm.rattachement().siteSelectionneProperty().set(site);
        vm.rattachement().pointSelectionneProperty().set(point);
        vm.rattachement().anneeProperty().set(ANNEE);
        vm.rattachement().numeroPassageProperty().set(1);

        vm.importer();

        // L'import réussit avec le WAV valide ; le passage est persisté.
        assertThat(vm.etatProperty().get()).isEqualTo(EtatImport.TERMINE);
        ResultatImport resultat = vm.resultatProperty().get();
        assertThat(resultat.nombreOriginaux()).isEqualTo(1);
        assertThat(new PassageDao(source).findById(resultat.passage().id())).isPresent();

        // Le rapport liste 1 importé et 1 rejeté (l'illisible), et le VM expose le rejet pour M-Import.
        assertThat(resultat.rapport().compte(StatutImportFichier.IMPORTE)).isEqualTo(1);
        assertThat(resultat.rapport().compte(StatutImportFichier.REJETE)).isEqualTo(1);
        assertThat(vm.rejetsImport()).singleElement().asString().contains("204500");
    }

    @Test
    @DisplayName("P2 : importer une nuit livrée sous forme de .zip → un passage Transformé, temporaire nettoyé (#139)")
    void parcours_complet_depuis_un_zip() throws Exception {
        // La nuit n'est pas un dossier mais une archive .zip (cas d'usage #139 : l'utilisateur récupère
        // une nuit zippée). On la place hors du workspace pour bien distinguer source et extraction.
        Path zip = Files.createTempDirectory("vc-e2e-zip").resolve("nuit.zip");
        compresser(dossierSource, zip);

        ImportationViewModel vm = injector.getInstance(ImportationViewModel.class);

        // Jalon 0 (spécifique #139) — l'archive est décompressée dans un dossier temporaire dédié, hors
        // de l'arborescence d'origine ; l'inspection se fait ensuite sur ce dossier extrait.
        Path extrait = vm.extraireSiZip(zip);
        assertThat(extrait)
                .as("l'archive est extraite ailleurs que le .zip lui-même")
                .isNotEqualTo(zip);
        assertThat(Files.isDirectory(extrait)).isTrue();
        assertThat(extrait.resolve("LogPR" + SERIE + ".txt")).exists();

        // Jalons 1 à 3 — parcours nominal sur le dossier extrait.
        vm.inspection().dossierSourceProperty().set(extrait);
        vm.inspecter();
        assertThat(vm.inspection().estInspecte()).isTrue();
        assertThat(vm.inspection().nombreOriginauxProperty().get()).isEqualTo(1);

        vm.chargerSites();
        vm.rattachement().siteSelectionneProperty().set(site);
        vm.rattachement().pointSelectionneProperty().set(point);
        vm.rattachement().anneeProperty().set(ANNEE);
        vm.rattachement().numeroPassageProperty().set(1);
        assertThat(vm.peutImporter().get()).isTrue();

        vm.importer();
        assertThat(vm.etatProperty().get()).isEqualTo(EtatImport.TERMINE);

        // Jalon final — un passage Transformé existe en base, exactement comme pour un import depuis un
        // dossier, ET le dossier temporaire d'extraction a été nettoyé une fois l'import terminé (#139).
        Long idPassage = vm.resultatProperty().get().passage().id();
        var passagePersiste = new PassageDao(source).findById(idPassage).orElseThrow();
        assertThat(passagePersiste.statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(passagePersiste.idPoint()).isEqualTo(point.id());
        assertThat(Files.exists(extrait))
                .as("le dossier temporaire d'extraction est supprimé après l'import")
                .isFalse();
    }

    @Test
    @DisplayName("P2 : importer un .zip « compresser ce dossier » (dossier racine unique) → passage Transformé (#139)")
    void parcours_depuis_un_zip_avec_dossier_racine() throws Exception {
        // Cas courant : l'utilisateur a fait « clic droit → Compresser » sur le dossier de la nuit, donc
        // tout le contenu est sous un dossier racine « MaNuit/ » dans l'archive.
        Path zip = Files.createTempDirectory("vc-e2e-zip-wrap").resolve("MaNuit.zip");
        compresserSousDossier(dossierSource, "MaNuit", zip);

        ImportationViewModel vm = injector.getInstance(ImportationViewModel.class);

        // racineEffective déplie le dossier racine unique : l'inspection retrouve journal et WAV.
        Path extrait = vm.extraireSiZip(zip);
        assertThat(extrait.resolve("LogPR" + SERIE + ".txt")).exists();

        vm.inspection().dossierSourceProperty().set(extrait);
        vm.inspecter();
        assertThat(vm.inspection().nombreOriginauxProperty().get()).isEqualTo(1);

        vm.chargerSites();
        vm.rattachement().siteSelectionneProperty().set(site);
        vm.rattachement().pointSelectionneProperty().set(point);
        vm.rattachement().anneeProperty().set(ANNEE);
        vm.rattachement().numeroPassageProperty().set(1);
        vm.importer();

        assertThat(vm.etatProperty().get()).isEqualTo(EtatImport.TERMINE);
        var passage = new PassageDao(source)
                .findById(vm.resultatProperty().get().passage().id())
                .orElseThrow();
        assertThat(passage.statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
    }

    @Test
    @DisplayName("P2 : réimporter le même quadruplet est bloqué en amont (pré-contrôle R5, #108)")
    void reimport_du_meme_quadruplet_est_refuse_R5() {
        // 1) Premier import : réussi (le quadruplet n'existe pas encore).
        ImportationViewModel premier = importerNuit(injector.getInstance(ImportationViewModel.class));
        assertThat(premier.etatProperty().get()).isEqualTo(EtatImport.TERMINE);
        Long idPassage = premier.resultatProperty().get().passage().id();

        // 2) Second assistant, MÊME quadruplet : le pré-contrôle R5 (#108) détecte le doublon dès le
        // rattachement et désactive l'import AVANT toute copie/transformation (l'utilisateur est prévenu).
        ImportationViewModel second = injector.getInstance(ImportationViewModel.class);
        second.inspection().dossierSourceProperty().set(dossierSource);
        second.inspecter();
        second.chargerSites();
        second.rattachement().siteSelectionneProperty().set(site);
        second.rattachement().pointSelectionneProperty().set(point);
        second.rattachement().anneeProperty().set(ANNEE);
        second.rattachement().numeroPassageProperty().set(1);

        assertThat(second.avertissementNumeroPassageProperty().get())
                .as("doublon détecté dès le rattachement")
                .containsIgnoringCase("existe déjà");
        assertThat(second.peutImporter().get())
                .as("le n° de passage déjà pris désactive l'import (R5 anticipé)")
                .isFalse();

        // Forcer l'appel n'a aucun effet : rien ne s'exécute, l'état reste PRET et aucun passage n'est créé.
        second.importer();
        assertThat(second.etatProperty().get()).isEqualTo(EtatImport.PRET);
        assertThat(second.resultatProperty().get()).isNull();

        // 3) La base reste intacte : un seul passage pour ce quadruplet, inchangé.
        var passagePersiste = new PassageDao(source).findById(idPassage).orElseThrow();
        assertThat(passagePersiste.statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(new PassageDao(source).findByPoint(point.id())).hasSize(1);
    }

    /// Rejoue les étapes 2 à 4 du parcours (inspecter → rattacher au site/point seedés → importer) sur
    /// le `vm` fourni, avec le quadruplet de référence `(CARRE/CODE_POINT, ANNEE, n° 1)`.
    private ImportationViewModel importerNuit(ImportationViewModel vm) {
        vm.inspection().dossierSourceProperty().set(dossierSource);
        vm.inspecter();
        vm.chargerSites();
        vm.rattachement().siteSelectionneProperty().set(site);
        vm.rattachement().pointSelectionneProperty().set(point);
        vm.rattachement().anneeProperty().set(ANNEE);
        vm.rattachement().numeroPassageProperty().set(1);
        vm.importer();
        return vm;
    }

    /// Crée un dossier SD minimal (journal LogPR + relevé climatique THLog + un WAV PCM valide à 2 kHz,
    /// nom brut non préfixé) que l'inspection puis l'import peuvent traiter.
    private static Path creerNuitSynthetique(Path sd) throws Exception {
        Files.createDirectories(sd);
        Files.writeString(sd.resolve("LogPR" + SERIE + ".txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(sd.resolve("PaRecPR" + SERIE + "_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
        ecrireWav(sd.resolve("PaRecPR" + SERIE + "_20260422_203922.wav"));
        return sd;
    }

    /// Compresse récursivement le contenu du dossier `racine` dans l'archive `zip` (entrées en chemins
    /// relatifs), pour rejouer le cas #139 d'une nuit livrée zippée.
    private static void compresser(Path racine, Path zip) throws Exception {
        Files.createDirectories(zip.getParent());
        try (ZipOutputStream sortie = new ZipOutputStream(Files.newOutputStream(zip));
                var fichiers = Files.walk(racine)) {
            for (Path fichier : (Iterable<Path>) fichiers.filter(Files::isRegularFile)::iterator) {
                sortie.putNextEntry(new ZipEntry(racine.relativize(fichier).toString()));
                Files.copy(fichier, sortie);
                sortie.closeEntry();
            }
        }
    }

    /// Comme [#compresser], mais imbrique tout le contenu sous un **dossier racine** `prefixeDossier/`,
    /// reproduisant une archive « clic droit → Compresser le dossier ».
    private static void compresserSousDossier(Path racineDir, String prefixeDossier, Path zip) throws Exception {
        Files.createDirectories(zip.getParent());
        try (ZipOutputStream sortie = new ZipOutputStream(Files.newOutputStream(zip));
                var fichiers = Files.walk(racineDir)) {
            for (Path fichier : (Iterable<Path>) fichiers.filter(Files::isRegularFile)::iterator) {
                sortie.putNextEntry(new ZipEntry(prefixeDossier + "/" + racineDir.relativize(fichier)));
                Files.copy(fichier, sortie);
                sortie.closeEntry();
            }
        }
    }

    private static void ecrireWav(Path fichier) throws Exception {
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
}

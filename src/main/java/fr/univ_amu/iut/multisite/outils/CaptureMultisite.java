package fr.univ_amu.iut.multisite.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureNavigationAudio;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.multisite.di.MultisiteModule;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.view.ModaleVuesController;
import fr.univ_amu.iut.multisite.view.MultisiteController;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'écran M-Multisite en PNG pour le comparer à la maquette du brief, en **deux vues** afin
/// d'en montrer les particularités :
///
/// - `apercu-multisite.png` : la **vue agrégée**, tableau de tous les passages (deux sites, statuts
///   et verdicts variés), barre de filtres et de tri, export ;
/// - `apercu-multisite-filtre.png` : le tableau **filtré** par verdict, résumé recalculé ;
/// - `apercu-multisite-edition.png` : le **mode édition des positions** (#154) — le toggle « ✎ »
///   superposé à la carte est actif (ambré), le bouton « Enregistrer les positions » apparaît ;
/// - `apercu-multisite-vues.png` : la **modale des vues sauvegardées** (deux vues seedées, la
///   première sélectionnée → son nom pré-rempli).
///
/// On seede une base SQLite temporaire via les **DAO réels** (la feature `multisite` dépend déjà de
/// `sites` et `passage`, dépendance autorisée) : utilisateur, deux sites/points et cinq passages,
/// plus deux vues sauvegardées via [ServiceMultisite]. L'utilisateur seedé devient l'utilisateur
/// courant (premier en base), donc le tableau liste ses passages. Les vues sont chargées via une
/// `controllerFactory` Guice (socle + sites + passage + multisite) et rendues hors-écran par
/// [ApercuFx].
///
/// **Déterminisme** : le tableau n'affiche que des métadonnées de passage (carré, point, date,
/// statut, verdict) — aucun chemin de fichier, donc aucune dépendance au dossier temporaire.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureMultisite {

    private static final String ID_UTILISATEUR = "demo-enseignant";
    private static final String ENREGISTREUR = "1925492";
    private static final String FXML = "Multisite.fxml";

    private CaptureMultisite() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException | IOException probleme) {
                erreur.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        Platform.exit();
        if (erreur.get() != null) {
            erreur.get().printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void capturer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-multisite");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        seeder(injecteur, source);

        rendreEcran(injecteur, sortie.resolve("apercu-multisite.png"));
        rendreEcranFiltre(injecteur, sortie.resolve("apercu-multisite-filtre.png"));
        rendreEcranEdition(injecteur, sortie.resolve("apercu-multisite-edition.png"));
        rendreModale(injecteur, sortie.resolve("apercu-multisite-vues.png"));
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage
    /// (test).
    public static Injector creerInjecteur() {
        return Guice.createInjector(
                new CommunModule(),
                new PersistenceModule(),
                new SitesModule(),
                new PassageModule(),
                new MultisiteModule(),
                new ModuleCaptureNavigationAudio());
    }

    /// Rend le tableau **filtré** par verdict « OK » (sélection dans le ComboBox de filtre), pour
    /// montrer la restriction du tableau et le résumé recalculé.
    private static void rendreEcranFiltre(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource(FXML));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        // Items du filtre verdict : [Tous(null), A_VERIFIER, OK, DOUTEUX, A_JETER] → index 2 = OK.
        if (vue.lookup("#choixVerdict") instanceof ComboBox<?> choixVerdict) {
            choixVerdict.getSelectionModel().select(2);
        }
        // Même fond de carte OSM que la capture principale (la carte n'est pas filtrée).
        capturerCarte(new Scene(vue, 1100, 620), fichier);
    }

    /// Rend l'écran en **mode édition des positions** (#154) : on active le toggle « ✎ » superposé à la
    /// carte (qui passe en ambré) ; le bouton « Enregistrer les positions » apparaît alors dans la barre.
    /// Illustre la correction des positions de points directement sur la carte.
    private static void rendreEcranEdition(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource(FXML));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        Scene scene = new Scene(vue, 1100, 620);
        // 1) applyCss/layout AVANT pour que `scene.lookup` trouve le toggle (ajouté en code à la carte) ;
        //    sans cela la recherche par id renvoie null (cache CSS non initialisé).
        vue.applyCss();
        vue.layout();
        // 2) on **fixe** l'état sélectionné (déterministe, sans le timing d'un fire()) : la pince passe en
        //    ambré (CSS :selected) et « 💾 » devient visible (binding sur l'état du toggle).
        if (scene.lookup("#boutonEditerPositions") instanceof ToggleButton editer) {
            editer.setSelected(true);
        }
        // 3) re-layout APRÈS pour que « 💾 » (devenu managed) soit pris en compte dans la capture.
        vue.applyCss();
        vue.layout();
        capturerCarte(scene, fichier);
    }

    /// Délai d'attente des tuiles OpenStreetMap (#152), en millisecondes, avant la capture principale :
    /// les tuiles se téléchargent en arrière-plan (réseau) puis se peignent sur le fil JavaFX ; on laisse
    /// ce temps au fond de carte d'apparaître. **Best-effort** : hors-ligne, la capture reste lisible
    /// (carrés/points sur fond clair), seul le fond photographique manque.
    private static final long DELAI_TUILES_MS = 6000;

    /// Charge `Multisite.fxml` (le controller auto-charge le tableau en `initialize()`) et le rend, après
    /// avoir laissé les tuiles OSM se charger (la carte est l'élément vedette de cette capture).
    private static void rendreEcran(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource(FXML));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        capturerCarte(new Scene(vue, 1100, 620), fichier);
    }

    /// Laisse tourner le fil JavaFX (boucle d'évènements imbriquée) le temps que les tuiles arrivées en
    /// fond soient peintes, puis rend la main. Un minuteur de fond déclenche la sortie de boucle.
    private static void attendreTuiles() {
        Object cle = new Object();
        Thread minuteur = new Thread(() -> {
            try {
                Thread.sleep(DELAI_TUILES_MS);
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
            }
            Platform.runLater(() -> Platform.exitNestedEventLoop(cle, null));
        });
        minuteur.setDaemon(true);
        minuteur.start();
        Platform.enterNestedEventLoop(cle);
    }

    /// Charge `ModaleVues.fxml`, la branche sur un ViewModel (qui charge les vues seedées),
    /// sélectionne la première vue (nom pré-rempli) puis rend la modale.
    private static void rendreModale(Injector injecteur, Path fichier) throws IOException {
        MultisiteViewModel viewModel = injecteur.getInstance(MultisiteViewModel.class);
        FXMLLoader loader = new FXMLLoader(ModaleVuesController.class.getResource("ModaleVues.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ModaleVuesController controleur = loader.getController();
        controleur.demarrer(viewModel);
        if (vue.lookup("#listeVues") instanceof ListView<?> liste
                && !liste.getItems().isEmpty()) {
            liste.getSelectionModel().select(0);
        }
        ecrire(new Scene(vue, 440, 440), fichier);
    }

    /// Rend `scene` hors-écran en PNG et journalise (helper factorisé).
    private static void ecrire(Scene scene, Path fichier) {
        ApercuFx.enregistrerPng(scene, fichier);
        journaliser(fichier);
    }

    /// Rend `scene` **après attente des tuiles OSM** (#152) et journalise — pour les captures à carte.
    private static void capturerCarte(Scene scene, Path fichier) {
        ApercuFx.capturerApresPreparation(scene, CaptureMultisite::attendreTuiles, fichier);
        journaliser(fichier);
    }

    /// Journalise l'écriture d'une capture (un seul endroit où vit le libellé, cf. PMD
    /// `AvoidDuplicateLiterals`).
    private static void journaliser(Path fichier) {
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Seede l'utilisateur courant, deux sites avec un point chacun, cinq passages aux statuts et
    /// verdicts variés, et deux vues sauvegardées.
    private static void seeder(Injector injecteur, SourceDeDonnees source) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        new EnregistreurDao(source).insert(new Enregistreur(ENREGISTREUR, "V1.01", null));
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        PassageDao passageDao = new PassageDao(source);

        Site tuiliere = siteDao.insert(new Site(
                null, "640380", "Étang de la Tuilière", Protocole.STANDARD, null, "2026-01-01", ID_UTILISATEUR));
        Site chenes = siteDao.insert(
                new Site(null, "640381", "Bois des Chênes", Protocole.STANDARD, null, "2026-01-01", ID_UTILISATEUR));
        // Points calés DANS leur carré national réel (centroïdes carrenat dépt 64, cf. carrenat.csv) :
        // 640380 ≈ (43.4031, -1.5708) et 640381 ≈ (43.4040, -1.5462), deux mailles 2 km adjacentes.
        Long pointA = pointDao.insert(new PointDEcoute(null, "A1", 43.4010, -1.5740, null, tuiliere.id()))
                .id();
        Long pointB = pointDao.insert(new PointDEcoute(null, "B2", 43.4040, -1.5470, null, chenes.id()))
                .id();

        passage(passageDao, 2, 2026, "2026-06-22", StatutWorkflow.DEPOSE, Verdict.OK, pointA);
        passage(passageDao, 1, 2026, "2026-06-08", StatutWorkflow.VERIFIE, Verdict.DOUTEUX, pointA);
        passage(passageDao, 3, 2025, "2025-07-19", StatutWorkflow.TRANSFORME, Verdict.A_VERIFIER, pointA);
        passage(passageDao, 1, 2026, "2026-06-15", StatutWorkflow.PRET_A_DEPOSER, Verdict.OK, pointB);
        passage(passageDao, 2, 2026, "2026-06-29", StatutWorkflow.IMPORTE, Verdict.A_VERIFIER, pointB);

        ServiceMultisite service = injecteur.getInstance(ServiceMultisite.class);
        service.enregistrerVue("Déposés 2026", new FiltresMultisite(null, StatutWorkflow.DEPOSE, null, 2026));
        service.enregistrerVue("À revérifier", new FiltresMultisite(null, null, Verdict.DOUTEUX, null));
    }

    private static void passage(
            PassageDao dao, int numero, int annee, String date, StatutWorkflow statut, Verdict verdict, Long idPoint) {
        dao.insert(new Passage(
                null,
                numero,
                annee,
                date,
                "20:25:00",
                "07:47:00",
                null,
                statut,
                verdict,
                null,
                null,
                null,
                idPoint,
                ENREGISTREUR));
    }
}

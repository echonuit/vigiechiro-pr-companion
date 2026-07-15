package fr.univ_amu.iut.multisite.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.outils.ModuleCaptureNavigationAudio;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.multisite.di.MultisiteModule;
import fr.univ_amu.iut.multisite.view.MultisiteController;
import fr.univ_amu.iut.multisite.view.ReconstructionModaleController;
import fr.univ_amu.iut.multisite.viewmodel.ReconstructionViewModel;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
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
///   superposé à la carte est actif (ambré), le bouton « Enregistrer les positions » apparaît.
///
/// On seede une base SQLite temporaire via les **DAO réels** (la feature `multisite` dépend déjà de
/// `sites` et `passage`, dépendance autorisée) : utilisateur, deux sites/points et cinq passages.
/// L'utilisateur seedé devient l'utilisateur courant (premier en base), donc le tableau liste ses
/// passages. Les vues sont chargées via une
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

    private static final String FXML_RECONSTRUCTION = "ReconstructionModale.fxml";

    /// Carré de démonstration : celui du site seedé, du filtre de recherche et de la nuit manquante.
    private static final String CARRE_DEMO = "640380";

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
        rendreEcranCartePleine(injecteur, sortie.resolve("apercu-multisite-carte-pleine.png"));
        rendreModaleReconstruction(injecteur, sortie.resolve("apercu-multisite-reconstruction.png"));
    }

    /// Rend la **modale « Reconstruire un passage manquant »** (#1396) : les nuits déposées sur
    /// VigieChiro et absentes de cette machine, dont l'une au **point d'écoute inconnu ici** (la ligne
    /// le dit, et le bouton la refusera).
    ///
    /// Le ViewModel est **alimenté à la main** plutôt que par un appel réseau : la capture ne parle à
    /// aucune plateforme (et `ClientVigieChiro` est `final`, donc pas de double). Ce que l'on montre
    /// reste le rendu **réel** de la vue sur des données réalistes.
    private static void rendreModaleReconstruction(Injector injecteur, Path fichier) throws IOException {
        ReconstructionViewModel viewModel = new ReconstructionViewModel(Optional.empty());
        ExecuteurTache executeur = injecteur.getInstance(ExecuteurTache.class);
        FXMLLoader loader = new FXMLLoader(ReconstructionModaleController.class.getResource(FXML_RECONSTRUCTION));
        loader.setControllerFactory(type -> type == ReconstructionModaleController.class
                ? new ReconstructionModaleController(viewModel, executeur)
                : injecteur.getInstance(type));
        Parent vue = loader.load();
        Scene scene = new Scene(vue);
        // `initialize()` a tenté de lire la plateforme (absente ici) : on publie ensuite la liste, qui
        // remplace le message. C'est l'état où arrive l'utilisateur connecté.
        ApercuFx.capturerApresPreparation(
                scene,
                () -> viewModel.appliquer(List.of(
                        new ParticipationOrpheline(
                                "6a53f5faae21902a597394d3", CARRE_DEMO, "A1", "2026-06-18T21:42:00+02:00", true),
                        new ParticipationOrpheline(
                                "6a53f5faae21902a597394e7", "130711", "Z41", "2026-07-03T22:00:00+02:00", false))),
                fichier);
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage
    /// (test).
    ///
    /// `ResultatsIdentificationDao` (#1338) est fourni **ici** plutôt que via `ValidationModule` : depuis
    /// que `ServiceMultisite` lit les résultats déjà importés, il en a besoin, mais tirer toute la feature
    /// `validation` dans un injecteur de capture du multisite y ajouterait bien plus que ce DAO. C'est un
    /// simple objet sur la [SourceDeDonnees] déjà liée par [PersistenceModule].
    public static Injector creerInjecteur() {
        return Guice.createInjector(
                ModuleCaptureCommun.communSynchrone(),
                new PersistenceModule(),
                new SitesModule(),
                new PassageModule(),
                new MultisiteModule(),
                new ModuleCaptureNavigationAudio(),
                new AbstractModule() {
                    @Provides
                    ResultatsIdentificationDao fournirResultatsIdentificationDao(SourceDeDonnees source) {
                        return new ResultatsIdentificationDao(source);
                    }
                });
    }

    /// Rend le tableau **filtré** via la recherche de la barre à puces (#537 étape 6b), pour montrer la
    /// restriction du tableau et le résumé recalculé.
    private static void rendreEcranFiltre(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource(FXML));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        // Recherche sur un carré : le tableau ne montre plus que ses passages (le résumé se recalcule).
        if (vue.lookup("#champRecherche") instanceof TextField recherche) {
            recherche.setText(CARRE_DEMO);
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

    /// Rend l'écran **tableau replié / carte plein écran** (#347) : on actionne la poignée « Tableau ▶ »
    /// (`#basculerTableau`) pour retirer le tableau du `SplitPane` et donner toute la largeur à la carte —
    /// l'état où arrive « Voir sur la carte ». Le `applyCss/layout` préalable garantit que la poignée est
    /// trouvable par `lookup` (comme pour le mode édition).
    private static void rendreEcranCartePleine(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource(FXML));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        Scene scene = new Scene(vue, 1100, 620);
        vue.applyCss();
        vue.layout();
        if (scene.lookup("#boutonReplierTableau") instanceof Button replierTableau) {
            replierTableau.fire(); // replie le tableau → carte plein écran
        }
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

    /// Seede l'utilisateur courant, deux sites avec un point chacun, et cinq passages aux statuts et
    /// verdicts variés.
    private static void seeder(Injector injecteur, SourceDeDonnees source) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        new EnregistreurDao(source).insert(new Enregistreur(ENREGISTREUR, "V1.01", null));
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        PassageDao passageDao = new PassageDao(source);

        Site tuiliere = siteDao.insert(new Site(
                null, CARRE_DEMO, "Étang de la Tuilière", Protocole.STANDARD, null, "2026-01-01", ID_UTILISATEUR));
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

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

/// OUTIL FOURNI : conservé dans la version étudiante (capture/mesure, utilisable tel quel).
///
/// Capture l'écran M-Multisite en PNG pour le comparer à la maquette du brief, en **deux vues** afin
/// d'en montrer les particularités :
///
/// - `apercu-multisite.png` : la **vue agrégée**, tableau de tous les passages (deux sites, statuts
///   et verdicts variés), barre de filtres et de tri, export ;
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

        Injector injecteur = Guice.createInjector(
                new CommunModule(),
                new PersistenceModule(),
                new SitesModule(),
                new PassageModule(),
                new MultisiteModule());
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        seeder(injecteur, source);

        rendreEcran(injecteur, sortie.resolve("apercu-multisite.png"));
        rendreEcranFiltre(injecteur, sortie.resolve("apercu-multisite-filtre.png"));
        rendreModale(injecteur, sortie.resolve("apercu-multisite-vues.png"));
    }

    /// Rend le tableau **filtré** par verdict « OK » (sélection dans le ComboBox de filtre), pour
    /// montrer la restriction du tableau et le résumé recalculé.
    private static void rendreEcranFiltre(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource("Multisite.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        // Items du filtre verdict : [Tous(null), A_VERIFIER, OK, DOUTEUX, A_JETER] → index 2 = OK.
        if (vue.lookup("#choixVerdict") instanceof ComboBox<?> choixVerdict) {
            choixVerdict.getSelectionModel().select(2);
        }
        ecrire(new Scene(vue, 1100, 620), fichier);
    }

    /// Charge `Multisite.fxml` (le controller auto-charge le tableau en `initialize()`) et le rend.
    private static void rendreEcran(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource("Multisite.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ecrire(new Scene(vue, 1100, 620), fichier);
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

    /// Rend `scene` hors-écran en PNG et journalise (helper factorisé : évite la répétition du libellé
    /// de log, ce que PMD `AvoidDuplicateLiterals` interdit au-delà de 3 occurrences).
    private static void ecrire(Scene scene, Path fichier) {
        ApercuFx.enregistrerPng(scene, fichier);
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
        Long pointA = pointDao.insert(new PointDEcoute(null, "A1", 43.5298, 5.4474, null, tuiliere.id()))
                .id();
        Long pointB = pointDao.insert(new PointDEcoute(null, "B2", 43.5510, 5.4602, null, chenes.id()))
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

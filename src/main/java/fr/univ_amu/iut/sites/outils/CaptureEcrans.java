package fr.univ_amu.iut.sites.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirImportation;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.view.ModalePointController;
import fr.univ_amu.iut.sites.view.NavigationSites;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture les ecrans de la feature « sites » (M-Sites, M-Site-detail, modale point) en PNG, pour
/// les comparer visuellement aux maquettes du brief. Demarche :
///
/// 1. base SQLite temporaire seedee avec des donnees d'exemple realistes (3 sites, points GPS,
///    passages aux statuts/verdicts varies) ;
/// 2. injecteur Guice du **chrome complet** ([RacineInjecteur#modules()] : toutes les features, car
///    `MainController` en dépend) avec une [HorlogeFigee] pour un rendu deterministe (fraicheur,
///    « il y a N j », annee courante figes) et un `OuvrirImportation` no-op ;
/// 3. chaque vue est chargee via la `controllerFactory` Guice du `FXMLLoader`, puis rendue
///    hors-ecran par [ApercuFx] (snapshot + SwingFXUtils) dans `.github/assets/`.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26,
/// `glass.platform=Headless`, sans xvfb). Patron reutilisable : chaque future feature ajoute son
/// propre `<feature>.outils.CaptureEcrans`.
public final class CaptureEcrans {

    /// Identifiant de l'unique utilisateur local seede (l'app est mono-utilisateur).
    private static final String ID_UTILISATEUR = "demo-enseignant";

    /// Date figee de reference (« aujourd'hui ») pour un rendu deterministe.
    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 20);

    /// Annee de campagne des passages seedes.
    private static final int ANNEE = 2026;

    /// N° de serie des deux enregistreurs seedes (cle naturelle, cf. [Enregistreur]).
    private static final String SERIE_PR1 = "1925492";

    private static final String SERIE_PR2 = "1648011";

    private static final String CHROME = "/fr/univ_amu/iut/commun/view/MainView.fxml";
    private static final String MODALE = "/fr/univ_amu/iut/sites/view/ModalePoint.fxml";

    private CaptureEcrans() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturerTout();
            } catch (RuntimeException | IOException probleme) {
                erreur.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        Platform.exit();
        Throwable probleme = erreur.get();
        if (probleme != null) {
            probleme.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void capturerTout() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-sites");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        // Migration + seed une seule fois ; chaque ecran reconstruit ensuite un injecteur neuf
        // (singletons frais) pointant sur la meme base, pour eviter qu'un meme noeud JavaFX soit
        // partage entre deux scenes.
        Injector amorce = creerInjecteur();
        amorce.getInstance(MigrationSchema.class).migrer();
        Seed seed = seeder(amorce);

        capturerMesSites(creerInjecteur(), sortie.resolve("apercu-sites-mes-sites.png"));
        capturerDetail(creerInjecteur(), seed.site(), sortie.resolve("apercu-sites-detail.png"));
        // Détail d'un site sans passage : montre l'état « aucun passage » du tableau.
        capturerDetail(
                creerInjecteur(), seed.siteSansPassage(), sortie.resolve("apercu-sites-detail-sans-passage.png"));
        // Modale point : édition (champs pré-remplis) puis création (formulaire vierge).
        capturerModaleEdition(
                creerInjecteur(), seed.site(), seed.point(), sortie.resolve("apercu-sites-modale-point.png"));
        capturerModaleCreation(creerInjecteur(), seed.site(), sortie.resolve("apercu-sites-modale-point-creation.png"));

        // État vide : base neuve (juste un utilisateur, aucun site) → accueil M-Sites en état initial.
        Path workspaceVide = Files.createTempDirectory("vc-capture-sites-vide");
        System.setProperty("vigiechiro.workspace", workspaceVide.toString());
        Injector amorceVide = creerInjecteur();
        amorceVide.getInstance(MigrationSchema.class).migrer();
        new UtilisateurDao(amorceVide.getInstance(SourceDeDonnees.class))
                .insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        capturerMesSites(creerInjecteur(), sortie.resolve("apercu-sites-mes-sites-vide.png"));

        System.out.println("Apercus ecrits dans " + sortie.toAbsolutePath());
    }

    /// Ecran d'accueil M-Sites, rendu dans le chrome principal (barre + zone centrale).
    private static void capturerMesSites(Injector injecteur, Path fichier) throws IOException {
        Parent chrome = chargerFxml(injecteur, CHROME);
        injecteur.getInstance(NavigationSites.class).ouvrirAccueil();
        ApercuFx.enregistrerPng(new Scene(chrome, 1100, 720), fichier);
    }

    /// Ecran de detail d'un site, rendu dans le chrome (fiche + points + tableau des passages).
    /// On empile d'abord la liste M-Sites puis le detail, pour que le **fil d'Ariane** du chrome
    /// montre le parcours complet (`Accueil › Mes sites › Carré N`) et le bouton ← Retour (#140).
    private static void capturerDetail(Injector injecteur, Site site, Path fichier) throws IOException {
        Parent chrome = chargerFxml(injecteur, CHROME);
        NavigationSites navigation = injecteur.getInstance(NavigationSites.class);
        navigation.ouvrirAccueil();
        navigation.ouvrirDetail(site);
        ApercuFx.enregistrerPng(new Scene(chrome, 1180, 920), fichier);
    }

    /// Modale d'edition d'un point d'ecoute (champs pre-remplis), rendue seule (fenetre modale).
    private static void capturerModaleEdition(Injector injecteur, Site site, PointDEcoute point, Path fichier)
            throws IOException {
        FXMLLoader loader = new FXMLLoader(CaptureEcrans.class.getResource(MODALE));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ((ModalePointController) loader.getController()).demarrerEdition(site, point, () -> {});
        ApercuFx.enregistrerPng(new Scene(vue), fichier);
    }

    /// Modale de creation d'un point d'ecoute (formulaire vierge), rendue seule (fenetre modale).
    private static void capturerModaleCreation(Injector injecteur, Site site, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(CaptureEcrans.class.getResource(MODALE));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ((ModalePointController) loader.getController()).demarrerCreation(site, () -> {});
        ApercuFx.enregistrerPng(new Scene(vue), fichier);
    }

    private static Parent chargerFxml(Injector injecteur, String chemin) throws IOException {
        FXMLLoader loader = new FXMLLoader(CaptureEcrans.class.getResource(chemin));
        loader.setControllerFactory(injecteur::getInstance);
        return loader.load();
    }

    /// Injecteur du **chrome complet** (toutes les features, comme l'application réelle), car cet
    /// outil rend `MesSites` à l'intérieur de `MainView` — dont le `MainController` dépend de tout le
    /// graphe (recherche globale, etc.). On part de [RacineInjecteur#modules()] et on **surcharge**
    /// l'horloge (rendu reproductible) et `OuvrirImportation` (no-op : la capture ne déclenche pas
    /// d'import).
    private static Injector creerInjecteur() {
        return Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(liaison -> {
            liaison.bind(Horloge.class).toInstance(new HorlogeFigee(REFERENCE));
            liaison.bind(OuvrirImportation.class).toInstance(idSite -> {});
        }));
    }

    /// Insere les donnees d'exemple et renvoie le site + point captures en detail et en modale.
    private static Seed seeder(Injector injecteur) {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        ServiceSites service = injecteur.getInstance(ServiceSites.class);
        EnregistreurDao enregistreurs = injecteur.getInstance(EnregistreurDao.class);
        PassageDao passages = injecteur.getInstance(PassageDao.class);
        enregistreurs.insert(new Enregistreur(SERIE_PR1, "V1.01, T4.1", null));
        enregistreurs.insert(new Enregistreur(SERIE_PR2, "V1.01, T4.1", null));

        // Site 1 (tiede) : riche, c'est lui qui est capture en detail et dont A1 alimente la modale.
        Site etang = service.creerSite(
                "640380", "Etang de la Tuiliere", Protocole.STANDARD, "Aix-en-Provence", ID_UTILISATEUR);
        PointDEcoute a1 =
                service.ajouterPoint(etang.id(), "A1", 43.4010, -1.5740, "Pres du grand chene, a 30 m du chemin");
        PointDEcoute b2 = service.ajouterPoint(etang.id(), "B2", 43.4055, -1.5680, "Lisiere de roseliere");
        service.ajouterPoint(etang.id(), "C3", null, null, "Bord de l'etang - GPS a relever");
        passages.insert(passage(2, "2026-08-22", a1.id(), SERIE_PR1, StatutWorkflow.DEPOSE, Verdict.OK, "2026-08-25"));
        passages.insert(passage(1, "2026-06-18", a1.id(), SERIE_PR1, StatutWorkflow.VERIFIE, Verdict.OK, null));
        passages.insert(passage(2, "2026-08-24", b2.id(), SERIE_PR2, StatutWorkflow.TRANSFORME, null, null));
        passages.insert(
                passage(1, "2026-06-20", b2.id(), SERIE_PR2, StatutWorkflow.DEPOSE, Verdict.DOUTEUX, "2026-06-23"));

        // Site 2 (frais) : un passage tout recent, pas encore verifie.
        Site zac = service.creerSite("752204", "ZAC Nord", Protocole.STANDARD, "Marseille", ID_UTILISATEUR);
        PointDEcoute zacA1 = service.ajouterPoint(zac.id(), "A1", 43.3400, 5.3600, null);
        passages.insert(passage(1, "2026-09-15", zacA1.id(), SERIE_PR1, StatutWorkflow.IMPORTE, null, null));

        // Site 3 (froid) : aucun passage, protocole recherche. Capture en detail « sans passage ».
        Site calanques = service.creerSite("130010", "Calanques", Protocole.RECHERCHE, null, ID_UTILISATEUR);
        service.ajouterPoint(calanques.id(), "A1", 43.2100, 5.4400, "Crete sud");

        return new Seed(etang, a1, calanques);
    }

    private static Passage passage(
            int numero,
            String date,
            Long idPoint,
            String enregistreur,
            StatutWorkflow statut,
            Verdict verdict,
            String deposeLe) {
        return new Passage(
                null,
                numero,
                ANNEE,
                date,
                "21:34:00",
                "05:12:00",
                null,
                statut,
                verdict,
                null,
                null,
                deposeLe,
                idPoint,
                enregistreur);
    }

    /// Donnees seedees reutilisees par les ecrans detail et modale.
    private record Seed(Site site, PointDEcoute point, Site siteSansPassage) {}
}

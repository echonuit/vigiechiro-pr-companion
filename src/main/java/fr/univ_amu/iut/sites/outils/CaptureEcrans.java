package fr.univ_amu.iut.sites.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.EchelleProgression;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.AttenteTuiles;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.DialogueProgression;
import fr.univ_amu.iut.commun.view.OuvrirImportation;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.ImportSiteDistant;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.RapatriementCarre;
import fr.univ_amu.iut.sites.model.RechercheCarreExistant;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.SouhaitDeclaration;
import fr.univ_amu.iut.sites.view.ModalePointController;
import fr.univ_amu.iut.sites.view.ModaleSiteController;
import fr.univ_amu.iut.sites.view.NavigationSites;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture les écrans de la feature « sites » (M-Sites, M-Site-detail, modale point) en PNG, pour
/// les comparer visuellement aux maquettes du brief. Démarche :
///
/// 1. base SQLite temporaire semée avec des données d'exemple réalistes (3 sites, points GPS,
///    passages aux statuts/verdicts variés) ;
/// 2. injecteur Guice du **chrome complet** ([RacineInjecteur#modules()] : toutes les features, car
///    `MainController` en dépend) avec une [HorlogeFigee] pour un rendu déterministe (fraîcheur,
///    « il y a N j », année courante figées) et un `OuvrirImportation` no-op ;
/// 3. chaque vue est chargée via la `controllerFactory` Guice du `FXMLLoader`, puis rendue
///    hors-écran par [ApercuFx] (snapshot + SwingFXUtils) dans `.github/assets/`.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26,
/// `glass.platform=Headless`, sans xvfb). Patron réutilisable : chaque future feature ajoute son
/// propre `<feature>.outils.CaptureEcrans`.
public final class CaptureEcrans {

    /// Identifiant de l'unique utilisateur local semé (l'app est mono-utilisateur).
    private static final String ID_UTILISATEUR = "demo-enseignant";

    /// Date figée de référence (« aujourd'hui ») pour un rendu déterministe.
    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 20);

    /// Année de campagne des passages semés.
    private static final int ANNEE = 2026;

    /// N° de série des deux enregistreurs semés (clé naturelle, cf. [Enregistreur]).
    private static final String SERIE_PR1 = "1925492";

    private static final String SERIE_PR2 = "1648011";

    private static final String CHROME = "/fr/univ_amu/iut/commun/view/MainView.fxml";
    private static final String MODALE = "/fr/univ_amu/iut/sites/view/ModalePoint.fxml";

    /// Modale de déclaration / édition d'un site (#1431) : une VRAIE vue, rendue telle quelle. La
    /// capture précédente (apercu-sites-modale-edition.png) était une RÉPLIQUE reconstruite à la main
    /// dans CaptureDialogues, parce que le dialogue n'avait pas de .fxml, elle pouvait donc dériver du
    /// vrai écran sans que rien ne le signale.
    private static final String MODALE_SITE = "/fr/univ_amu/iut/sites/view/ModaleSite.fxml";

    /// Carré de l'aperçu « ce carré existe déjà » (#3458) : une maille réelle, comme les autres
    /// données d'exemple, pour que l'image ne montre pas un numéro impossible.
    private static final String CARRE_DEJA_DECLARE = "130711";

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

        // Migration + semis une seule fois ; chaque écran reconstruit ensuite un injecteur neuf
        // (singletons frais) pointant sur la même base, pour éviter qu'un même nœud JavaFX soit
        // partagé entre deux scènes.
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
        capturerModaleSiteEdition(creerInjecteur(), seed.site(), sortie.resolve("apercu-sites-modale-site.png"));
        capturerModaleSiteCreation(creerInjecteur(), sortie.resolve("apercu-sites-modale-site-creation.png"));
        capturerModaleSiteCarreExistant(sortie.resolve("apercu-sites-modale-site-carre-existant.png"));
        capturerModaleSiteAutreProtocole(sortie.resolve("apercu-sites-modale-site-autre-protocole.png"));
        capturerCompteRenduRapatriement(
                creerInjecteur(), seed.site(), sortie.resolve("apercu-sites-carre-recupere.png"));
        capturerSynchroEnCours(sortie.resolve("apercu-sites-synchro-progression.png"));

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

    /// Écran d'accueil M-Sites, rendu dans le chrome principal (barre + zone centrale).
    private static void capturerMesSites(Injector injecteur, Path fichier) throws IOException {
        Parent chrome = chargerFxml(injecteur, CHROME);
        injecteur.getInstance(NavigationSites.class).ouvrirAccueil();
        ApercuFx.enregistrerPng(new Scene(chrome, 1100, 720), fichier);
    }

    /// Écran de détail d'un site, rendu dans le chrome (fiche + points + tableau des passages).
    /// On empile d'abord la liste M-Sites puis le détail, pour que le **fil d'Ariane** du chrome
    /// montre le parcours complet (`Accueil › Mes sites › Carré N`) et le bouton ← Retour (#140).
    private static void capturerDetail(Injector injecteur, Site site, Path fichier) throws IOException {
        Parent chrome = chargerFxml(injecteur, CHROME);
        NavigationSites navigation = injecteur.getInstance(NavigationSites.class);
        navigation.ouvrirAccueil();
        navigation.ouvrirDetail(site);
        ApercuFx.enregistrerPng(new Scene(chrome, 1180, 920), fichier);
    }

    /// Modale d'édition d'un point d'écoute (champs pré-remplis), rendue seule (fenêtre modale). La
    /// modale embarque désormais une carte-outil (#153) : on attend les tuiles OSM avant le snapshot.
    private static void capturerModaleEdition(Injector injecteur, Site site, PointDEcoute point, Path fichier)
            throws IOException {
        FXMLLoader loader = new FXMLLoader(CaptureEcrans.class.getResource(MODALE));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ((ModalePointController) loader.getController()).demarrerEdition(site, point, () -> {});
        ApercuFx.capturerApresPreparation(new Scene(vue), AttenteTuiles::attendre, fichier);
    }

    /// Modale de création d'un point d'écoute (formulaire vierge), rendue seule (fenêtre modale). Comme
    /// l'édition, on laisse les tuiles OSM de la carte-outil se charger avant le snapshot (#153).
    private static void capturerModaleCreation(Injector injecteur, Site site, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(CaptureEcrans.class.getResource(MODALE));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        // L'injecteur de capture n'a pas la connexion : la case « publier » y est donc absente, et
        // l'aperçu montre la modale telle qu'elle est hors ligne (#3458).
        ((ModalePointController) loader.getController()).demarrerCreation(site, () -> {}, identifiant -> {});
        ApercuFx.capturerApresPreparation(new Scene(vue), AttenteTuiles::attendre, fichier);
    }

    /// Modale d'édition d'un site (champs pré-remplis), rendue seule. Pas de carte-outil ici : aucun
    /// chargement de tuiles à attendre.
    private static void capturerModaleSiteEdition(Injector injecteur, Site site, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(CaptureEcrans.class.getResource(MODALE_SITE));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ((ModaleSiteController) loader.getController()).demarrerEdition(site, () -> {});
        ApercuFx.enregistrerPng(new Scene(vue), fichier);
    }

    /// Modale de déclaration d'un site (formulaire vierge), rendue seule.
    private static void capturerModaleSiteCreation(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(CaptureEcrans.class.getResource(MODALE_SITE));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ((ModaleSiteController) loader.getController()).demarrerCreation(() -> {});
        ApercuFx.enregistrerPng(new Scene(vue), fichier);
    }

    /// Injecteur de capture dont la recherche rend un **verdict figé** : la capture ne dépend ni du
    /// réseau ni d'un jeton, et l'écran reste rendu par sa vraie vue.
    private static Injector injecteurAvecVerdict(RechercheCarreExistant.Verdict verdict) {
        return injecteurAvecVerdict(verdict, null);
    }

    /// Variante qui fige **aussi** l'issue du rapatriement, pour les états qui n'apparaissent qu'après
    /// avoir cliqué « Récupérer ce carré ».
    private static Injector injecteurAvecVerdict(
            RechercheCarreExistant.Verdict verdict, RapatriementCarre.Resultat issueRapatriement) {
        return Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(ModuleCaptureCommun.executeursSynchrones(), liaison -> {
                    liaison.bind(Horloge.class).toInstance(new HorlogeFigee(REFERENCE));
                    OptionalBinder.newOptionalBinder(liaison, OuvrirImportation.class)
                            .setBinding()
                            .toInstance(idSite -> {});
                    OptionalBinder.newOptionalBinder(liaison, RechercheCarreExistant.class)
                            .setBinding()
                            .toInstance(new RechercheCarreExistant(new ClientVigieChiro(Optional::empty)) {
                                @Override
                                public Verdict chercher(String numeroCarre) {
                                    return verdict;
                                }
                            });
                    if (issueRapatriement != null) {
                        // Le rapatriement réel est conservé pour ses dépendances : seule son ISSUE est
                        // figée. Le fabriquer de toutes pièces demanderait des DAO factices, que les
                        // gardes de construction refusent - à raison.
                        Provider<ImportSiteDistant> imports = liaison.getProvider(ImportSiteDistant.class);
                        OptionalBinder.newOptionalBinder(liaison, RapatriementCarre.class)
                                .setBinding()
                                .toProvider(() ->
                                        new RapatriementCarre(new ClientVigieChiro(Optional::empty), imports.get()) {
                                            @Override
                                            public Resultat rapatrier(SouhaitDeclaration souhait) {
                                                return issueRapatriement;
                                            }
                                        });
                    }
                }));
    }

    /// Modale de déclaration **après** un « Vérifier sur Vigie-Chiro » qui trouve le carré (#3458).
    ///
    /// C'est l'état qui **évite la panne** : redéclarer un carré déjà présent est ce qui a produit le
    /// dépôt manqué à l'origine de l'issue. Les deux autres aperçus de cette modale montrent le geste
    /// disponible, aucun ne montrait sa réponse.
    ///
    /// Le geste est joué **par l'IHM** - on saisit le carré et on tire le bouton -, si bien que le
    /// message rendu est celui que le produit compose. Seule la **réponse de la plateforme** est
    /// bouchonnée : la capture ne doit pas dépendre du réseau ni d'un jeton, et un fac-similé du message
    /// écrit ici n'engagerait personne (c'est ainsi qu'un dialogue documenté a dérivé du produit, #1468).
    private static void capturerModaleSiteCarreExistant(Path fichier) throws IOException {
        Injector injecteur = injecteurAvecVerdict(new RechercheCarreExistant.Verdict.DejaDeclare(
                List.of("Vigiechiro - Point Fixe-" + CARRE_DEJA_DECLARE)));
        FXMLLoader loader = new FXMLLoader(CaptureEcrans.class.getResource(MODALE_SITE));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ((ModaleSiteController) loader.getController()).demarrerCreation(() -> {});
        Scene scene = new Scene(vue);
        ((TextField) exiger(scene, "#champCarre")).setText(CARRE_DEJA_DECLARE);
        ((Button) exiger(scene, "#btnVerifierCarre")).fire();
        ApercuFx.enregistrerPng(scene, fichier);
    }

    /// Modale de déclaration quand le carré existe **sous un autre protocole** (#3806).
    ///
    /// L'état est trompeur sans image : le numéro « existe » et n'est pourtant pas récupérable, parce
    /// que Companion ne traite que le Point Fixe. Ni « inexistant », ni silence : un refus qui dit
    /// pourquoi.
    private static void capturerModaleSiteAutreProtocole(Path fichier) throws IOException {
        Injector injecteur = injecteurAvecVerdict(
                new RechercheCarreExistant.Verdict.DejaDeclare(List.of("Vigie-chiro - Routier-" + CARRE_DEJA_DECLARE)),
                new RapatriementCarre.Resultat.AutreProtocole(List.of("Vigie-chiro - Routier-" + CARRE_DEJA_DECLARE)));
        FXMLLoader loader = new FXMLLoader(CaptureEcrans.class.getResource(MODALE_SITE));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ((ModaleSiteController) loader.getController()).demarrerCreation(() -> {});
        Scene scene = new Scene(vue);
        ((TextField) exiger(scene, "#champCarre")).setText(CARRE_DEJA_DECLARE);
        ((Button) exiger(scene, "#btnVerifierCarre")).fire();
        // L'état ne se voit qu'APRÈS le geste : le verdict dit « il existe », c'est la récupération qui
        // découvre que le protocole ne suit pas.
        ((Button) exiger(scene, "#btnRecupererCarre")).fire();
        ApercuFx.enregistrerPng(scene, fichier);
    }

    /// Le **compte rendu** que l'utilisateur lit après avoir récupéré un carré (#3806).
    ///
    /// La fiche d'un carré **qui vient d'être récupéré** : son bandeau de retour porte le compte rendu,
    /// là où une fenêtre s'ouvrait par-dessus la fiche avant #4091 (ADR 0023).
    ///
    /// L'aperçu emprunte [NavigationSites#ouvrirDetailRapatrie], c'est-à-dire le **chemin de
    /// production**. Il construisait auparavant son `Alert` lui-même : la fenêtre montrée n'était donc
    /// pas celle que l'utilisateur voyait, et l'aperçu serait resté identique quoi qu'il arrive au
    /// produit. C'est la raison pour laquelle il n'a rien signalé pendant que le défaut vivait.
    ///
    /// Le nombre de points annoncé est **compté sur le site rendu**, et non posé en dur : une légende
    /// qui promet quarante et un points au-dessus d'une fiche qui en montre trois est un aperçu faux.
    private static void capturerCompteRenduRapatriement(Injector injecteur, Site site, Path fichier)
            throws IOException {
        Parent chrome = chargerFxml(injecteur, CHROME);
        int points = injecteur
                .getInstance(ServiceSites.class)
                .listerPoints(site.id())
                .size();
        NavigationSites navigation = injecteur.getInstance(NavigationSites.class);
        navigation.ouvrirAccueil();
        navigation.ouvrirDetailRapatrie(new RapatriementCarre.Resultat.Rapatrie(site, points));
        ApercuFx.enregistrerPng(new Scene(chrome, 1180, 920), fichier);
    }

    /// Le nœud `selecteur`, ou une **erreur** qui le nomme.
    ///
    /// Un `lookup` muet rendrait `null`, et la capture s'écrirait quand même - montrant une modale sans
    /// verdict sous une légende qui en annonce un. C'est la panne que `ApercuFx.exigerParLibelle` ferme
    /// pour les libellés ; ici c'est un `fx:id` qu'on exige.
    private static Node exiger(Scene scene, String selecteur) {
        Node noeud = scene.lookup(selecteur);
        if (noeud == null) {
            throw new IllegalStateException("Aucun noeud " + selecteur + " dans la modale de site");
        }
        return noeud;
    }

    private static Parent chargerFxml(Injector injecteur, String chemin) throws IOException {
        FXMLLoader loader = new FXMLLoader(CaptureEcrans.class.getResource(chemin));
        loader.setControllerFactory(injecteur::getInstance);
        return loader.load();
    }

    /// Injecteur du **chrome complet** (toutes les features, comme l'application réelle), car cet
    /// outil rend `MesSites` à l'intérieur de `MainView` : dont le `MainController` dépend de tout le
    /// graphe (recherche globale, etc.). On part de [RacineInjecteur#modules()] et on **surcharge**
    /// l'horloge (rendu reproductible), `OuvrirImportation` (no-op : la capture ne déclenche pas
    /// d'import) et les exécuteurs hors fil (synchrones : le snapshot doit voir le contenu chargé,
    /// cf. [ModuleCaptureCommun]). Exposé pour le garde-fou de câblage (test).
    public static Injector creerInjecteur() {
        return Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(ModuleCaptureCommun.executeursSynchrones(), liaison -> {
                    liaison.bind(Horloge.class).toInstance(new HorlogeFigee(REFERENCE));
                    OptionalBinder.newOptionalBinder(liaison, OuvrirImportation.class)
                            .setBinding()
                            .toInstance(idSite -> {});
                }));
    }

    /// Insère les données d'exemple et renvoie le site + point capturés en détail et en modale.
    private static Seed seeder(Injector injecteur) {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        ServiceSites service = injecteur.getInstance(ServiceSites.class);
        EnregistreurDao enregistreurs = injecteur.getInstance(EnregistreurDao.class);
        PassageDao passages = injecteur.getInstance(PassageDao.class);
        enregistreurs.insert(new Enregistreur(SERIE_PR1, "V1.01, T4.1", null));
        enregistreurs.insert(new Enregistreur(SERIE_PR2, "V1.01, T4.1", null));

        // Site 1 (tiède) : riche, c'est lui qui est capturé en détail et dont A1 alimente la modale.
        Site etang = service.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, "Ahetze", ID_UTILISATEUR);
        PointDEcoute a1 =
                service.ajouterPoint(etang.id(), "A1", 43.4010, -1.5740, "Près du grand chêne, à 30 m du chemin");
        PointDEcoute b2 = service.ajouterPoint(etang.id(), "B2", 43.4055, -1.5680, "Lisière de roselière");
        service.ajouterPoint(etang.id(), "C3", null, null, "Bord de l'étang - GPS à relever");
        // Points RAPATRIÉS de la plateforme mais jamais utilisés (#1738) : masqués par défaut de la fiche
        // (un lien « Afficher N point(s) rapatrié(s) non utilisé(s) » les révèle) et résumés sur la carte
        // M-Sites (« (+ N rapatriés) »), pour ne pas noyer les points qui servent sous la grille rapatriée.
        service.ajouterPointSynchronise(etang.id(), "Z10", 43.4020, -1.5730, null);
        service.ajouterPointSynchronise(etang.id(), "Z11", 43.4030, -1.5720, null);
        service.ajouterPointSynchronise(etang.id(), "Z12", 43.4040, -1.5710, null);
        service.ajouterPointSynchronise(etang.id(), "Z13", 43.4050, -1.5700, null);
        service.ajouterPointSynchronise(etang.id(), "Z14", 43.4060, -1.5690, null);
        passages.insert(passage(2, "2026-08-22", a1.id(), SERIE_PR1, StatutWorkflow.DEPOSE, Verdict.OK, "2026-08-25"));
        passages.insert(passage(1, "2026-06-18", a1.id(), SERIE_PR1, StatutWorkflow.VERIFIE, Verdict.OK, null));
        passages.insert(passage(2, "2026-08-24", b2.id(), SERIE_PR2, StatutWorkflow.TRANSFORME, null, null));
        passages.insert(
                passage(1, "2026-06-20", b2.id(), SERIE_PR2, StatutWorkflow.DEPOSE, Verdict.DOUTEUX, "2026-06-23"));

        // Site 2 (frais) : un passage tout récent, pas encore vérifié. Carré 131165 = maille réelle de
        // Marseille (centroïde 43.342, 5.355), cohérente avec les coordonnées du point.
        Site zac = service.creerSite("131165", "ZAC Nord", Protocole.STANDARD, "Marseille", ID_UTILISATEUR);
        PointDEcoute zacA1 = service.ajouterPoint(zac.id(), "A1", 43.3400, 5.3600, null);
        passages.insert(passage(1, "2026-09-15", zacA1.id(), SERIE_PR1, StatutWorkflow.IMPORTE, null, null));

        // Site 3 (froid) : aucun passage, protocole recherche. Capturé en détail « sans passage ».
        // Carré 131275 = maille réelle des Calanques (centroïde 43.213, 5.447), cohérente avec le point.
        Site calanques = service.creerSite("131275", "Calanques", Protocole.RECHERCHE, null, ID_UTILISATEUR);
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
                enregistreur,
                null);
    }

    /// Données semées réutilisées par les écrans détail et modale.
    private record Seed(Site site, PointDEcoute point, Site siteSansPassage) {}

    /// La **modale de progression de la synchronisation** (#2558), figée sur une étape.
    ///
    /// « Synchroniser depuis VigieChiro » posait un simple voile opaque : ça travaillait, sans dire où on
    /// en était ni laisser renoncer. Depuis #2554 la synchro rapatrie aussi le CONTENU de chaque nuit, donc
    /// elle dure - une opération qui dure doit s'annoncer et se laisser interrompre.
    ///
    /// Le libellé est celui qu'émet réellement le balayage (`ExecutionParallele` : « Nuits k/N ») et la
    /// fraction vient de la vraie échelle, pour que l'image dise ce que l'utilisateur lit.
    private static void capturerSynchroEnCours(Path fichier) throws IOException {
        VBox contenu = DialogueProgression.apercu(
                "Synchronisation Vigie-Chiro",
                new Progression("Nuits 7/12", EchelleProgression.autonome(12).fraction(7)));
        ApercuFx.enregistrerPng(new Scene(contenu), fichier);
        System.out.println("Apercu ecrit dans " + fichier);
    }
}

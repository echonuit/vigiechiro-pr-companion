package fr.univ_amu.iut.multisite.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.ActionGroupee;
import fr.univ_amu.iut.commun.model.CiblePassage;
import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.AttenteTuiles;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.outils.ModuleCaptureNavigationAudio;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.multisite.view.MultisiteController;
import fr.univ_amu.iut.multisite.view.ReconstructionModaleController;
import fr.univ_amu.iut.multisite.viewmodel.ReconstructionViewModel;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import fr.univ_amu.iut.passage.model.dao.CampagneDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
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
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Pane;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'écran M-Multisite en PNG pour le comparer à la maquette du brief, en **deux vues** afin
/// d'en montrer les particularités :
///
/// - `apercu-multisite.png` : la **vue agrégée**, tableau de tous les passages (deux sites, statuts
///   et verdicts variés), barre de filtres et de tri, export ;
/// - `apercu-multisite-filtre.png` : le tableau **filtré** par verdict, résumé recalculé ;
/// - `apercu-multisite-edition.png` : le **mode édition des positions** (#154), le toggle « ✎ »
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
/// statut, verdict) : aucun chemin de fichier, donc aucune dépendance au dossier temporaire.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureMultisite {

    private static final String ID_UTILISATEUR = "demo-enseignant";
    private static final String ENREGISTREUR = "1925492";
    private static final String FXML = "Multisite.fxml";

    private static final String FXML_RECONSTRUCTION = "ReconstructionModale.fxml";

    /// Carré de démonstration : celui du site seedé, du filtre de recherche et de la nuit manquante.
    private static final String CARRE_DEMO = "640380";

    /// Nombre de lignes cochées pour la capture du menu en sélection (#2357) : assez pour que le pluriel
    /// des libellés se voie, assez peu pour rester lisible.
    private static final int LIGNES_COCHEES = 3;

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

    /// Workspace temporaire, injecteur, schema migre et graine semee : tout ce qu'il faut avant de rendre
    /// un ecran multisite.
    ///
    /// Extrait de [#capturer] pour que [CaptureValeurHorsJeu] parte du **meme** jeu (#3169) : sans la
    /// graine, l'ecran se charge sur une base vide et le critere Lieu n'offre que les lieux du
    /// rapprochement VigieChiro - pas ceux dont la capture parle.
    static Injector preparer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-multisite");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injecteur = creerInjecteur();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        seeder(injecteur, source);
        return injecteur;
    }

    private static void capturer() throws IOException {
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));
        Injector injecteur = preparer();

        rendreEcran(injecteur, sortie.resolve("apercu-multisite.png"));
        rendreEcranFiltre(injecteur, sortie.resolve("apercu-multisite-filtre.png"));
        rendreAnneeInvalide(injecteur, sortie.resolve("apercu-multisite-annee-invalide.png"));
        rendreEcranEdition(injecteur, sortie.resolve("apercu-multisite-edition.png"));
        rendreEcranCartePleine(injecteur, sortie.resolve("apercu-multisite-carte-pleine.png"));
        rendreEcranTableauPlein(injecteur, sortie.resolve("apercu-multisite-tableau-plein.png"));
        rendreModaleReconstruction(injecteur, sortie.resolve("apercu-multisite-reconstruction.png"));
        rendreImportGroupe(injecteur, sortie.resolve("apercu-multisite-reconstruction-groupe.png"));
        rendreImportGroupeInterrompu(injecteur, sortie.resolve("apercu-multisite-reconstruction-interrompu.png"));
        rendreMenuActions(injecteur, sortie.resolve("apercu-multisite-menu-actions.png"));
        rendreMenuActionsSelection(injecteur, sortie.resolve("apercu-multisite-menu-selection.png"));
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
        // `initialize()` a tenté de lire la plateforme (absente ici) : on publie ensuite la liste, qui
        // remplace le message. C'est l'état où arrive l'utilisateur connecté.
        //
        // La publication a lieu AVANT la scène, qui se dimensionne alors sur le contenu réellement affiché
        // (#2049). Publier après - ce que faisait `capturerApresPreparation` ici - laissait la scène à la
        // taille du contenu d'AVANT : le paragraphe d'explication et le bandeau de retour, apparus ensuite,
        // se rabattaient sur une ligne terminée par une ellipse. `charger()` étant lancé sur un
        // [ExecuteurTache] **synchrone** dans les captures, tout est retombé quand `load()` rend la main :
        // rien ne reste à attendre après coup.
        viewModel.appliquer(List.of(
                new ParticipationOrpheline(
                        "6a53f5faae21902a597394d3", CARRE_DEMO, "A1", "2026-06-18T21:42:00+02:00", true),
                new ParticipationOrpheline(
                        "6a53f5faae21902a597394e7", "130711", "Z41", "2026-07-03T22:00:00+02:00", false)));
        ApercuFx.enregistrerPng(new Scene(vue), fichier);
    }

    /// Rend la modale en **import groupé en cours** (#1708) : les **deux** barres de progression - le **lot**
    /// (« Nuit 2 / 3 ») et la **nuit courante** (« Import des observations… ») - et le bouton **Annuler**.
    /// L'état est posé par le crochet de capture du controller
    /// ([ReconstructionModaleController#apercuImportGroupeEnCours]), sans lancer de vrai lot : le rendu reste
    /// celui de la vue réelle.
    private static void rendreImportGroupe(Injector injecteur, Path fichier) throws IOException {
        ReconstructionViewModel viewModel = new ReconstructionViewModel(Optional.empty());
        ExecuteurTache executeur = injecteur.getInstance(ExecuteurTache.class);
        FXMLLoader loader = new FXMLLoader(ReconstructionModaleController.class.getResource(FXML_RECONSTRUCTION));
        loader.setControllerFactory(type -> type == ReconstructionModaleController.class
                ? new ReconstructionModaleController(viewModel, executeur)
                : injecteur.getInstance(type));
        Parent vue = loader.load();
        ReconstructionModaleController controleur = loader.getController();
        // L'état est posé AVANT la scène, qui se dimensionne alors sur le contenu réellement affiché, les
        // deux barres comprises. Une taille explicite (760 x 500) tenait ce rôle jusqu'ici, parce que les
        // barres paraissaient APRÈS le dimensionnement et rognaient le paragraphe d'explication en tête.
        // Mais une taille en dur doit être ré-ajustée à chaque changement de mise en page : les marges de la
        // modale l'ont fait déborder d'un coup, et le paragraphe s'est retrouvé rogné à nouveau.
        viewModel.appliquer(List.of(
                new ParticipationOrpheline(
                        "6a53f5faae21902a597394d3", CARRE_DEMO, "A1", "2026-06-18T21:42:00+02:00", true),
                new ParticipationOrpheline(
                        "6a53f5faae21902a597394d4", CARRE_DEMO, "B2", "2026-06-19T21:40:00+02:00", true),
                new ParticipationOrpheline(
                        "6a53f5faae21902a597394d5", CARRE_DEMO, "C3", "2026-06-20T21:38:00+02:00", true)));
        controleur.apercuImportGroupeEnCours("Nuit 2 / 3…", 2.0 / 3.0, "Import des observations…", 0.96);
        ApercuFx.enregistrerPng(new Scene(vue), fichier);
    }

    /// Rend la modale **après un import groupé interrompu** (revue visuelle, clôture du lot 3).
    ///
    /// C'est l'état qui **mentait** : le lot levait à l'annulation, la modale affichait « aucune nuit n'a
    /// été complétée » alors que deux l'étaient, et ne rechargeait pas sa liste - les deux nuits déjà
    /// complétées restaient offertes à la complétion. Aucune capture ne montrait cet état, donc rien ne
    /// le contredisait.
    ///
    /// Ce qui est rendu ici est ce que le produit fait maintenant : le bilan dit ce qui a été fait, dit
    /// que le reste est intact, et la liste ne garde que la nuit non commencée.
    private static void rendreImportGroupeInterrompu(Injector injecteur, Path fichier) throws IOException {
        ReconstructionViewModel viewModel = new ReconstructionViewModel(Optional.empty());
        ExecuteurTache executeur = injecteur.getInstance(ExecuteurTache.class);
        FXMLLoader loader = new FXMLLoader(ReconstructionModaleController.class.getResource(FXML_RECONSTRUCTION));
        loader.setControllerFactory(type -> type == ReconstructionModaleController.class
                ? new ReconstructionModaleController(viewModel, executeur)
                : injecteur.getInstance(type));
        Parent vue = loader.load();
        viewModel.appliquer(List.of(new ParticipationOrpheline(
                "6a53f5faae21902a597394d5", CARRE_DEMO, "C3", "2026-06-20T21:38:00+02:00", true)));
        // La barre « Nuit en cours » reste affichée après un lot (elle suit `reconstruit`) : on la laisse
        // à l'état où la dernière nuit l'a laissée, sinon l'aperçu montre une barre vide que le produit
        // n'affiche jamais.
        viewModel.progression().demarrer("Terminé.");
        viewModel.progression().appliquer(new Progression("Terminé.", 1.0));
        viewModel.restituerLot(new ServiceReconstructionPassages.BilanReconstructionGroupe(2, 0, 20, 41, true));
        ApercuFx.enregistrerPng(new Scene(vue), fichier);
        journaliser(fichier);
    }

    /// Photographie le **menu ☰ ouvert** de « Carte & passages » (#2065). Il n'était ouvert par aucune
    /// capture : ses cinq entrées - dont « Exporter… » et « Écouter la sélection filtrée », converties en
    /// icônes par #1564 - n'apparaissaient **nulle part**, et leur rendu n'était donc vérifiable par
    /// personne.
    ///
    /// La mécanique est celle d'[ApercuFx#enregistrerMenuOuvert] : le vrai menu, jamais reconstruit.
    private static void rendreMenuActions(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource(FXML));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        if (!(vue.lookup("#menuActions") instanceof MenuButton menuActions)) {
            System.out.println("[capture-multisite-menu] menu ☰ introuvable : capture ignorée.");
            return;
        }
        if (!ApercuFx.enregistrerMenuOuvert(menuActions, fichier)) {
            System.out.println("[capture-multisite-menu] popup non rendu (headless) : " + fichier + " ignoré.");
            return;
        }
        journaliser(fichier);
    }

    /// Photographie le même menu ☰ **avec trois lignes cochées** (#2357, clôture du lot 3).
    ///
    /// La capture précédente montre les quatre actions groupées **grisées**, ce qui est leur état par
    /// défaut mais le moins informatif : on y voit quatre entrées inertes, et rien ne dit qu'elles
    /// s'allument. Ici, chaque entrée est active et **dit combien de lignes sont cochées** - c'est
    /// exactement ce que la documentation affirme, et il n'y avait aucune image pour le montrer.
    private static void rendreMenuActionsSelection(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource(FXML));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        // Le menu se peuple par liaison sur le NOMBRE de lignes cochées : il faut donc une scène et une
        // mise en page avant de cocher, sinon les libellés restent sur leur valeur initiale.
        Scene scene = new Scene(vue, 1100, 620);
        vue.applyCss();
        vue.layout();
        if (scene.lookup("#tableLignes") instanceof TableView<?> table) {
            table.getSelectionModel().clearSelection();
            table.getSelectionModel()
                    .selectRange(0, Math.min(LIGNES_COCHEES, table.getItems().size()));
        }
        vue.applyCss();
        vue.layout();
        if (!(vue.lookup("#menuActions") instanceof MenuButton menuActions)) {
            System.out.println("[capture-multisite-menu-selection] menu ☰ introuvable : capture ignorée.");
            return;
        }
        if (!ApercuFx.enregistrerMenuOuvert(menuActions, fichier)) {
            System.out.println("[capture-multisite-menu-selection] popup non rendu (headless) : " + fichier);
            return;
        }
        journaliser(fichier);
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage
    /// (test).
    ///
    /// `ResultatsIdentificationDao` (#1338) est fourni **ici** plutôt que via `ValidationModule` : depuis
    /// que `ServiceMultisite` lit les résultats déjà importés, il en a besoin, mais tirer toute la feature
    /// `validation` dans un injecteur de capture du multisite y ajouterait bien plus que ce DAO. C'est un
    /// simple objet sur la [SourceDeDonnees] déjà liée par [PersistenceModule].
    public static Injector creerInjecteur() {
        // La colonne « Campagne » (#2355) se rendait vide faute de `CampagneModule` : composer depuis la
        // racine rend l'oubli impossible.
        return Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(
                        ModuleCaptureCommun.executeursSynchrones(),
                        new ModuleCaptureNavigationAudio(),
                        new AbstractModule() {
                            // Action groupée « Préparer le dépôt » (#2357) : REMPLAÇANT assumé, et il le reste.
                            //
                            // L'argument d'origine - « charger LotModule entraînerait toute la chaîne de dépôt » -
                            // ne tient plus : la chaîne est là, l'injecteur est complet. Ce qui subsiste est le
                            // DÉTERMINISME : l'action réelle consulte l'état du dépôt, la substituée non, et rien
                            // de visible n'en dépend (le libellé et l'état grisé viennent du NOMBRE de lignes
                            // cochées). Le jour où une capture montrerait le lot en cours, il faudra le vrai.
                            @Provides
                            @Named("action.preparerDepot")
                            ActionGroupee fournirActionPreparer() {
                                return actionDeCapture("Préparer le dépôt");
                            }

                            @Provides
                            @Named("action.televerser")
                            ActionGroupee fournirActionTeleverser() {
                                return actionDeCapture("Téléverser vers Vigie-Chiro");
                            }

                            @Provides
                            @Named("action.importerResultats")
                            ActionGroupee fournirActionImporter() {
                                return actionDeCapture("Importer les résultats");
                            }

                            @Provides
                            @Named("action.declencherCalcul")
                            ActionGroupee fournirActionCalcul() {
                                return actionDeCapture("Déclencher le calcul");
                            }

                            /// Remplaçant d'action : le libellé suffit, rien d'autre n'est rendu.
                            private ActionGroupee actionDeCapture(String libelle) {
                                return new ActionGroupee() {
                                    @Override
                                    public String libelle() {
                                        return libelle;
                                    }

                                    @Override
                                    public Optional<String> motifNonEligible(CiblePassage cible) {
                                        return Optional.empty();
                                    }

                                    @Override
                                    public void executer(CiblePassage cible, JetonAnnulation jeton) {
                                        throw new UnsupportedOperationException("capture : aucune action n'est jouée");
                                    }
                                };
                            }
                        }));
    }

    /// Rend le tableau **filtré** via la recherche de la barre à puces (#537 étape 6b), pour montrer la
    /// restriction du tableau et le résumé recalculé.
    /// La puce **« Année » remplie d'une saisie illisible** (#3094).
    ///
    /// L'état que cette capture doit établir : la puce est **posée et remplie**, le champ est **marqué**
    /// (bordure rouge, classe `champ-invalide`), et le tableau reste **entier**.
    ///
    /// C'est tout l'objet du correctif : une année illisible n'écarte rien, comme avant, mais elle le
    /// **dit**. Sans cette capture, rien ne montre la différence entre « la table n'est pas filtrée
    /// parce que la saisie est fausse » et « la table n'est pas filtrée du tout » - et c'est
    /// exactement la confusion qui coûtait le plus cher en confiance.
    private static void rendreAnneeInvalide(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource(FXML));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        if (!(vue.lookup("#menuAjoutFiltre") instanceof MenuButton menuAjout)) {
            throw new IllegalStateException("Menu « + Filtre » introuvable : la puce Année ne peut pas être posée.");
        }
        ApercuFx.exigerParLibelle("le menu « + Filtre »", menuAjout.getItems(), MenuItem::getText, "Année")
                .fire();
        if (!(vue.lookup("#pucesFiltres") instanceof Pane puces)
                || !(puces.lookup(".text-field") instanceof TextField champ)) {
            throw new IllegalStateException("Champ de la puce Année introuvable après son ajout.");
        }
        // Le O majuscule à la place du zéro : la faute de frappe qui motivait #3094.
        champ.setText("202O");
        capturerCarte(new Scene(vue, 1100, 620), fichier);
    }

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
    /// (`#basculerTableau`) pour retirer le tableau du `SplitPane` et donner toute la largeur à la carte :
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

    /// Rend l'écran **carte repliée / tableau plein écran** : symétrique de [#rendreEcranCartePleine],
    /// et la **seule** vue où le tableau montre toutes ses colonnes. À 1100 px partagés avec la carte,
    /// « Verdict », « Analyse » et « Campagne » sortent du cadre : la colonne Campagne livrée par #2355
    /// était documentée sans être visible nulle part.
    private static void rendreEcranTableauPlein(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource(FXML));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        Scene scene = new Scene(vue, 1100, 620);
        vue.applyCss();
        vue.layout();
        if (scene.lookup("#boutonReplierCarte") instanceof Button replierCarte) {
            replierCarte.fire(); // replie la carte → tableau plein écran
        }
        vue.applyCss();
        vue.layout();
        ecrire(scene, fichier);
    }

    /// Charge `Multisite.fxml` (le controller auto-charge le tableau en `initialize()`) et le rend, après
    /// avoir laissé les tuiles OSM se charger (la carte est l'élément vedette de cette capture).
    private static void rendreEcran(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource(FXML));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        capturerCarte(new Scene(vue, 1100, 620), fichier);
    }

    /// Rend `scene` hors-écran en PNG et journalise (helper factorisé).
    private static void ecrire(Scene scene, Path fichier) {
        ApercuFx.enregistrerPng(scene, fichier);
        journaliser(fichier);
    }

    /// Rend `scene` **après attente des tuiles OSM** (#152) et journalise : pour les captures à carte.
    private static void capturerCarte(Scene scene, Path fichier) {
        ApercuFx.capturerApresPreparation(scene, AttenteTuiles::attendre, fichier);
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

        // Commune du point (#3163) : résolue sur le premier carré, pas sur le second. Même raison que
        // pour la campagne juste dessous - une capture où toutes les lignes la porteraient laisserait
        // croire qu'elle est toujours là, et une capture où aucune ne l'aurait montrerait une colonne
        // vide, ce qui documenterait l'état dégradé plutôt que la fonctionnalité.
        new PointCommuneDao(source).definir(pointA, new Commune("Ahetze", "64014"));

        // Campagnes (#2355) : deux suivis distincts, et une nuit volontairement NON rattachée. Le
        // rattachement est facultatif ; une capture où toutes les lignes seraient rattachées laisserait
        // croire l'inverse, et une capture où aucune ne le serait montrerait une colonne vide.
        CampagneDao campagneDao = new CampagneDao(source);
        Long ens = campagneDao
                .insert(new Campagne(null, "Suivi ENS 2026", 2026, null))
                .id();
        Long these = campagneDao
                .insert(new Campagne(null, "Thèse Samuel", 2025, null))
                .id();

        passage(passageDao, 2, 2026, "2026-06-22", StatutWorkflow.DEPOSE, Verdict.OK, pointA, ens);
        passage(passageDao, 1, 2026, "2026-06-08", StatutWorkflow.VERIFIE, Verdict.DOUTEUX, pointA, ens);
        passage(passageDao, 3, 2025, "2025-07-19", StatutWorkflow.TRANSFORME, Verdict.A_VERIFIER, pointA, these);
        passage(passageDao, 1, 2026, "2026-06-15", StatutWorkflow.PRET_A_DEPOSER, Verdict.OK, pointB, ens);
        passage(passageDao, 2, 2026, "2026-06-29", StatutWorkflow.IMPORTE, Verdict.A_VERIFIER, pointB, null);
    }

    private static void passage(
            PassageDao dao,
            int numero,
            int annee,
            String date,
            StatutWorkflow statut,
            Verdict verdict,
            Long idPoint,
            Long idCampagne) {
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
                ENREGISTREUR,
                idCampagne));
    }
}

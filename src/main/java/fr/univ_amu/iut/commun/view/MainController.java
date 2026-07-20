package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RechercheGlobale;
import fr.univ_amu.iut.commun.model.ResultatRecherche;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.animation.FadeTransition;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

/// Controller du chrome principal (`MainView.fxml`).
///
/// Instancié par Guice via la `controllerFactory` du `FXMLLoader` (cf. [fr.univ_amu.iut.App]) :
/// il reçoit par injection le [NavigationViewModel] (état observable du chrome), le [Navigateur]
/// (service de swap de la zone centrale) et l'ensemble des [ActiviteAccueil] publiées par les
/// features. Son rôle se limite au **câblage** : lier les labels du chrome aux propriétés du
/// ViewModel, bâtir les cartes d'accueil à partir des activités, et lier le centre du `BorderPane`
/// à la vue centrale publiée par le [Navigateur]. Aucune logique métier ici.
public class MainController {

    private final NavigationViewModel navigation;
    private final Navigateur navigateur;
    private final Set<ActiviteAccueil> activites;
    private final Set<IndicateurAccueil> indicateurs;
    private final Optional<RechercheGlobale> recherche;
    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;
    private final Set<ActionMenu> actionsMenu;
    private final BandeauAnnonce bandeau;
    private final OccupationChrome occupationChrome;

    /// Racine `StackPane` de la fenêtre : hôte du voile d'occupation du chrome (#1215).
    @FXML
    private StackPane hoteOccupation;

    @FXML
    private BorderPane racine;

    @FXML
    private Label titreApplication;

    @FXML
    private Button boutonRetour;

    /// Enveloppe du ← Retour : porte le tooltip expliquant le grisage (un Button désactivé n'en affiche
    /// pas) et suit la visibilité du bouton. Cf. [IndicateurBlocage] (#789).
    @FXML
    private StackPane enveloppeRetour;

    @FXML
    private HBox filAriane;

    @FXML
    private BorderPane barreStatut;

    @FXML
    private Label piedGauche;

    @FXML
    private Label piedCentre;

    @FXML
    private Label piedDroite;

    @FXML
    private StackPane hero;

    @FXML
    private FlowPane bandeauIndicateurs;

    /// Conteneur des **sections de prismes** : une section (en-tête + grille de cartes) par [Prisme].
    /// `FlowPane` pour poser les sections **côte à côte** quand la largeur le permet, **empilées** sinon.
    @FXML
    private FlowPane cartesActivites;

    @FXML
    private TextField champRecherche;

    /// Menu ☰ (outils) : peuplé en code depuis `Set<ActionMenu>` (#930) ; libellés dynamiques
    /// réévalués à chaque ouverture.
    @FXML
    private MenuButton menuOutils;

    @FXML
    private HBox bandeauAnnonce;

    @FXML
    private Label texteAnnonce;

    @FXML
    private Hyperlink lienAnnonce;

    @FXML
    private Button fermerAnnonce;

    @FXML
    private VBox panneauResultats;

    @FXML
    private ListView<ResultatRecherche> listeResultats;

    /// Hôte de défilement permanent de la zone centrale : une barre verticale apparaît quand la vue
    /// courante dépasse la hauteur disponible (écrans de résolution normale), sans jamais masquer la
    /// barre de navigation ni le pied (qui restent dans le chrome, hors de cette zone).
    private final ScrollPane defilementCentral = new ScrollPane();

    /// Recherche globale du chrome (#144), câblée à l'initialisation. Reste `null` si la feature
    /// `recherche` est désactivée (#1087) : la barre de recherche est alors masquée.
    private RechercheChrome rechercheChrome;

    @Inject
    public MainController(
            NavigationViewModel navigation,
            Navigateur navigateur,
            Set<ActiviteAccueil> activites,
            Set<IndicateurAccueil> indicateurs,
            Optional<RechercheGlobale> recherche,
            OuvrirSite ouvrirSite,
            OuvrirPassage ouvrirPassage,
            Set<ActionMenu> actionsMenu,
            OccupationChrome occupationChrome,
            BandeauAnnonce bandeau) {
        this.navigation = navigation;
        this.navigateur = navigateur;
        this.activites = activites;
        this.indicateurs = indicateurs;
        this.recherche = recherche;
        this.ouvrirSite = ouvrirSite;
        this.ouvrirPassage = ouvrirPassage;
        this.actionsMenu = actionsMenu;
        this.bandeau = bandeau;
        this.occupationChrome = occupationChrome;
    }

    /// Appelée par le `FXMLLoader` une fois les `@FXML` injectés. Câble les bindings.
    @FXML
    private void initialize() {
        titreApplication.textProperty().bind(navigation.titreApplicationProperty());

        // Barre de statut à 3 zones (#495), câblée à part (BarreStatut) pour garder ce controller compact :
        // les libellés suivent NavigationViewModel.zonesStatut et la barre se masque quand tout est vide.
        BarreStatut.lier(barreStatut, piedGauche, piedCentre, piedDroite, navigation);

        // Menu « ☰ » (outils) : bâti depuis les ActionMenu contribuées (#930) — sauvegarde /
        // restauration, purge, préférences, réglages, connexion — sans que ce controller connaisse
        // chaque entrée. `this::fenetre` fournit la fenêtre propriétaire des dialogues au clic.
        ConstructeurMenuOutils.peupler(menuOutils, actionsMenu, this::fenetre);

        // Bandeau d'annonce (#2109) : ce que l'application a à dire au démarrage, cherché hors du
        // fil JavaFX. Le socle ne connaît aucune feature - il affiche ce que les AnnonceChrome
        // contribuées lui donnent, et reste invisible quand elles n'ont rien à dire.
        bandeau.installer(bandeauAnnonce, texteAnnonce, lienAnnonce, fermerAnnonce);

        // Voile d'occupation du chrome (#1215) : les traitements longs du menu ☰ (sauvegarde,
        // restauration, purge) voilent toute la fenêtre pendant leur travail hors du fil JavaFX.
        occupationChrome.installer(hoteOccupation);

        // ← Retour (historique) : reste actif même pendant une opération critique — l'utilisateur est
        // averti à la sortie (cf. Navigateur#peutQuitter, #906) plutôt que bloqué en silence. Sa visibilité
        // (présent hors accueil) est gérée par rafraichirNavigation() et suivie par l'enveloppe. Le tooltip
        // rappelle l'action et les raccourcis (#796).
        IndicateurBlocage.expliquer(enveloppeRetour, "Revenir à l'écran précédent (Alt+←, Alt+Début pour l'accueil).");
        enveloppeRetour.visibleProperty().bind(boutonRetour.visibleProperty());
        enveloppeRetour.managedProperty().bind(boutonRetour.managedProperty());

        peuplerCartes();
        peuplerIndicateurs();

        // Le filigrane nocturne du hero (icône surdimensionnée en bord de bannière) déborde
        // volontairement : on clippe la bannière à ses propres bornes pour qu'il ne morde pas sur
        // la zone des cartes en dessous.
        Rectangle horizon = new Rectangle();
        horizon.widthProperty().bind(hero.widthProperty());
        horizon.heightProperty().bind(hero.heightProperty());
        hero.setClip(horizon);

        // Tableau de bord : les compteurs se rafraîchissent à chaque retour sur l'accueil (après un
        // import, une déclaration de site…), pour refléter l'état courant de la base.
        navigation.vueCouranteProperty().addListener((obs, ancien, nouveau) -> {
            if ("accueil".equals(nouveau)) {
                peuplerIndicateurs();
            }
        });

        // La zone d'accueil déclarée dans le FXML devient la base de l'historique (Navigateur) ; le
        // centre du BorderPane suit ensuite le sommet de l'historique (toute navigation passe par lui).
        Parent accueil = (Parent) racine.getCenter();
        navigateur.memoriserAccueil(accueil);
        // Le centre du BorderPane est un ScrollPane permanent : on échange son CONTENU à chaque
        // navigation (jamais le centre lui-même). Une barre verticale apparaît dès que la vue dépasse
        // la hauteur disponible. fitToWidth/fitToHeight conservent le comportement actuel : la vue
        // occupe toute la largeur et remplit la hauteur tant qu'il y a la place (tables `vgrow`). La
        // barre horizontale n'est jamais affichée (les vues s'adaptent à la largeur). Fond transparent
        // (base.css) pour ne pas introduire de cadre gris autour des écrans.
        defilementCentral.setFitToWidth(true);
        defilementCentral.setFitToHeight(true);
        defilementCentral.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        defilementCentral.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        defilementCentral.getStyleClass().add("defilement-central");
        // Sommet initial sans animation ; chaque changement d'écran ensuite arrive en fondu (confort).
        defilementCentral.setContent(navigateur.getVueCentrale());
        racine.setCenter(defilementCentral);
        navigateur.vueCentraleProperty().addListener((obs, ancienne, nouvelle) -> afficherAvecFondu(nouvelle));

        // Barre de navigation (← Retour + fil d'Ariane) : reconstruite à chaque changement d'historique.
        navigateur.historique().addListener((ListChangeListener<EtapeNavigation>) changement -> rafraichirNavigation());
        rafraichirNavigation();

        if (recherche.isPresent()) {
            rechercheChrome = new RechercheChrome(
                    champRecherche, panneauResultats, listeResultats, recherche.get(), ouvrirSite, ouvrirPassage);
            rechercheChrome.configurer();
        } else {
            // Feature `recherche` désactivée (#1087) : la barre de recherche du chrome disparaît.
            champRecherche.setVisible(false);
            champRecherche.setManaged(false);
            panneauResultats.setVisible(false);
            panneauResultats.setManaged(false);
        }

        // Raccourcis clavier de navigation, posés dès que la scène est disponible :
        //  - Alt+← : ← Retour (écran précédent réel) ;
        //  - Alt+Début : retour direct à l'accueil (saut en tête du fil).
        // Pas de Backspace (conflit avec la saisie texte) ni d'Échap (réservé aux modales). Les deux
        // raccourcis passent par le Navigateur, qui respecte la garde de saisie et le verrou (#54).
        racine.sceneProperty().addListener((obs, ancienne, scene) -> {
            if (scene != null) {
                scene.getAccelerators()
                        .put(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN), navigateur::revenir);
                scene.getAccelerators()
                        .put(
                                new KeyCodeCombination(KeyCode.HOME, KeyCombination.ALT_DOWN),
                                navigateur::afficherAccueil);
                // Ctrl+F : place le focus dans le champ de recherche globale (#144) et présélectionne
                // son contenu, pour rechercher de n'importe quel écran. Absent si la feature `recherche`
                // est désactivée (#1087) : rechercheChrome reste alors `null`.
                if (rechercheChrome != null) {
                    scene.getAccelerators()
                            .put(
                                    new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                                    rechercheChrome::activer);
                }
            }
        });
    }

    /// Fenêtre propriétaire des sélecteurs/alertes (ou `null` tant que la scène n'est pas attachée).
    private Window fenetre() {
        return racine.getScene() == null ? null : racine.getScene().getWindow();
    }

    /// ← Retour : revient à l'écran précédent réel (historique), via le socle [Navigateur#revenir].
    @FXML
    private void revenir() {
        navigateur.revenir();
    }

    /// Affiche la nouvelle vue centrale avec un léger fondu d'entrée (confort de transition entre
    /// écrans). Le changement d'écran reste instantané côté graphe de scène ; seule l'opacité est
    /// animée, sans incidence sur l'interaction (un nœud en cours de fondu reste cliquable et trouvable).
    private void afficherAvecFondu(Parent vue) {
        defilementCentral.setContent(vue);
        if (vue == null) {
            return;
        }
        defilementCentral.setVvalue(0.0); // nouvelle vue : repartir du haut, pas de la position héritée
        FadeTransition fondu = new FadeTransition(Duration.millis(160), vue);
        fondu.setFromValue(0.0);
        fondu.setToValue(1.0);
        fondu.playFromStart();
    }

    /// Reconstruit la barre de navigation : visibilité du ← Retour (présent hors accueil) et segments
    /// du fil d'Ariane (emplacement de l'écran courant, sinon historique — cf. [Navigateur#filActuel]).
    private void rafraichirNavigation() {
        boolean peutRevenir = navigateur.peutRevenir();
        boutonRetour.setVisible(peutRevenir);
        boutonRetour.setManaged(peutRevenir);
        // Le bouton dit OÙ il ramène (historique) : « ← Vue multi-sites » lève l'ambiguïté quand le
        // fil d'Ariane montre l'emplacement (« Mes sites › Carré N ») plutôt que la route suivie.
        if (peutRevenir) {
            String destination = navigateur.libelleRetour();
            boutonRetour.setText(destination != null ? destination : "Retour");
        }

        filAriane.getChildren().clear();
        var segments = navigateur.filActuel();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                Label separateur = new Label("›");
                separateur.getStyleClass().add("fil-ariane-separateur");
                filAriane.getChildren().add(separateur);
            }
            Lieu lieu = segments.get(i);
            if (lieu.estCliquable()) {
                Hyperlink lien = new Hyperlink(lieu.libelle());
                lien.getStyleClass().add("fil-ariane-segment");
                lien.setOnAction(evenement -> lieu.ouvrir().run());
                filAriane.getChildren().add(lien);
            } else {
                Label courant = new Label(lieu.libelle());
                courant.getStyleClass().add("fil-ariane-courant");
                filAriane.getChildren().add(courant);
            }
        }
    }

    /// Bâtit l'accueil en **deux sections de prismes** (« Collecte & passages » / « Espèces &
    /// biodiversité »), dans l'ordre de l'énum [Prisme]. Chaque section porte un en-tête puis les cartes
    /// de son prisme, triées par `ordre()`. Cartes et sections sont créées en code car elles dépendent
    /// des features installées (point d'extension `Set<ActiviteAccueil>`). Un prisme sans aucune activité
    /// n'affiche pas de section.
    private void peuplerCartes() {
        Map<Prisme, List<ActiviteAccueil>> parPrisme =
                activites.stream().collect(Collectors.groupingBy(ActiviteAccueil::prisme));
        for (Prisme prisme : Prisme.values()) {
            List<ActiviteAccueil> duPrisme = parPrisme.get(prisme);
            if (duPrisme != null && !duPrisme.isEmpty()) {
                cartesActivites.getChildren().add(construireSection(prisme, duPrisme));
            }
        }
    }

    /// Construit une section d'accueil : un en-tête (icône + intitulé du prisme) au-dessus d'une grille
    /// (`FlowPane`) des cartes du prisme, triées par `ordre()`.
    private Node construireSection(Prisme prisme, List<ActiviteAccueil> activitesDuPrisme) {
        FontIcon icone = new FontIcon(prisme.iconeLiteral());
        icone.getStyleClass().add("section-prisme-icone");
        Label libelle = new Label(prisme.libelle());
        libelle.getStyleClass().add("section-prisme-titre");
        HBox entete = new HBox(icone, libelle);
        entete.getStyleClass().add("section-prisme-entete");

        FlowPane cartes = new FlowPane();
        cartes.getStyleClass().add("cartes-activites");
        cartes.setHgap(16.0);
        cartes.setVgap(16.0);
        // Largeur de repli = deux cartes (2 × 200 + l'écart) : chaque section reste compacte (≈ 2 colonnes)
        // pour que les deux prismes tiennent côte à côte tant que la fenêtre est assez large.
        cartes.setPrefWrapLength(416.0);
        activitesDuPrisme.stream()
                .sorted(Comparator.comparingInt(ActiviteAccueil::ordre))
                .map(CartesAccueil::carte)
                .forEach(cartes.getChildren()::add);

        VBox section = new VBox(entete, cartes);
        section.getStyleClass().add("section-prisme");
        return section;
    }

    /// Bâtit le bandeau de compteurs (tableau de bord) à partir des [IndicateurAccueil] des
    /// features, triés par `ordre()` et recalculés à la volée. Le bandeau reste **masqué** tant
    /// que la base est vide (premier lancement) : l'accueil reste épuré plutôt que d'afficher une
    /// rangée de « 0 ».
    private void peuplerIndicateurs() {
        // Instantané unique des compteurs : chaque valeur() déclenche un COUNT(*) et tout se passe
        // sur le fil JavaFX. On lit donc chaque indicateur UNE seule fois (pour décider de la
        // visibilité ET pour l'affichage), au lieu de rappeler valeur() à la construction de chaque
        // pastille.
        record Compteur(IndicateurAccueil indicateur, long valeur) {}
        var compteurs = indicateurs.stream()
                .sorted(Comparator.comparingInt(IndicateurAccueil::ordre))
                .map(i -> new Compteur(i, i.valeur()))
                .toList();
        boolean aDesDonnees = compteurs.stream().mapToLong(Compteur::valeur).sum() > 0;
        bandeauIndicateurs.setVisible(aDesDonnees);
        bandeauIndicateurs.setManaged(aDesDonnees);
        bandeauIndicateurs.getChildren().clear();
        if (aDesDonnees) {
            compteurs.stream()
                    .map(c -> CartesAccueil.pastille(c.indicateur(), c.valeur()))
                    .forEach(bandeauIndicateurs.getChildren()::add);
        }
    }
}

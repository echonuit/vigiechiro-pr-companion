package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RechercheGlobale;
import fr.univ_amu.iut.commun.model.ResultatRecherche;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
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
    private final RechercheGlobale recherche;
    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;
    private final ServiceSauvegarde serviceSauvegarde;
    private final ServicePurgeOriginaux servicePurge;

    /// Actions de sauvegarde/restauration de la base (menu « ☰ »), initialisées dans `initialize()`.
    private ActionsSauvegarde actionsSauvegarde;

    /// Action de purge globale des originaux (menu « ☰ »), initialisée dans `initialize()`.
    private ActionsPurge actionsPurge;

    @FXML
    private BorderPane racine;

    @FXML
    private Label titreApplication;

    @FXML
    private Button boutonRetour;

    @FXML
    private HBox filAriane;

    @FXML
    private Label pied;

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

    @FXML
    private VBox panneauResultats;

    @FXML
    private ListView<ResultatRecherche> listeResultats;

    /// Hôte de défilement permanent de la zone centrale : une barre verticale apparaît quand la vue
    /// courante dépasse la hauteur disponible (écrans de résolution normale), sans jamais masquer la
    /// barre de navigation ni le pied (qui restent dans le chrome, hors de cette zone).
    private final ScrollPane defilementCentral = new ScrollPane();

    /// Recherche globale du chrome (#144), câblée à l'initialisation.
    private RechercheChrome rechercheChrome;

    @Inject
    public MainController(
            NavigationViewModel navigation,
            Navigateur navigateur,
            Set<ActiviteAccueil> activites,
            Set<IndicateurAccueil> indicateurs,
            RechercheGlobale recherche,
            OuvrirSite ouvrirSite,
            OuvrirPassage ouvrirPassage,
            ServiceSauvegarde serviceSauvegarde,
            ServicePurgeOriginaux servicePurge) {
        this.navigation = navigation;
        this.navigateur = navigateur;
        this.activites = activites;
        this.indicateurs = indicateurs;
        this.recherche = recherche;
        this.ouvrirSite = ouvrirSite;
        this.ouvrirPassage = ouvrirPassage;
        this.serviceSauvegarde = serviceSauvegarde;
        this.servicePurge = servicePurge;
    }

    /// Appelée par le `FXMLLoader` une fois les `@FXML` injectés. Câble les bindings.
    @FXML
    private void initialize() {
        titreApplication.textProperty().bind(navigation.titreApplicationProperty());
        pied.textProperty().bind(navigation.piedDePageProperty());

        // Menu « ☰ » : sauvegarde/restauration de la base (#148). Après une restauration réussie, on
        // revient à l'accueil pour relire la base restaurée (les écrans ouverts liraient un état périmé).
        actionsSauvegarde = new ActionsSauvegarde(serviceSauvegarde, this::fenetre, navigateur::afficherAccueil);
        // Menu « ☰ » : purge globale des originaux (bruts/) pour récupérer de l'espace disque. Après une
        // purge, retour à l'accueil pour rafraîchir les volumes affichés.
        actionsPurge = new ActionsPurge(servicePurge, this::fenetre, navigateur::afficherAccueil);

        // ← Retour (historique) : grisé pendant une opération longue (import en cours, #54) qu'on ne
        // doit pas quitter. Sa visibilité (présent hors accueil) est gérée par rafraichirNavigation().
        boutonRetour.disableProperty().bind(navigation.navigationVerrouilleeProperty());

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

        rechercheChrome = new RechercheChrome(
                champRecherche, panneauResultats, listeResultats, recherche, ouvrirSite, ouvrirPassage);
        rechercheChrome.configurer();

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
                // son contenu, pour rechercher de n'importe quel écran.
                scene.getAccelerators()
                        .put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN), rechercheChrome::activer);
            }
        });
    }

    /// Menu « ☰ » → Sauvegarder la base : délègue à [ActionsSauvegarde] (#148).
    @FXML
    private void sauvegarderBase() {
        actionsSauvegarde.sauvegarder();
    }

    /// Menu « ☰ » → Restaurer une sauvegarde : délègue à [ActionsSauvegarde] (#148).
    @FXML
    private void restaurerBase() {
        actionsSauvegarde.restaurer();
    }

    /// Menu « ☰ » → Purger les originaux importés : délègue à [ActionsPurge].
    @FXML
    private void purgerOriginaux() {
        actionsPurge.purger();
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
            boutonRetour.setText(destination != null ? "← " + destination : "← Retour");
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
                .map(this::construireCarte)
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
                    .map(c -> construirePastille(c.indicateur(), c.valeur()))
                    .forEach(bandeauIndicateurs.getChildren()::add);
        }
    }

    /// Bâtit une pastille de compteur : icône colorée de la feature + valeur + libellé, posée sur le
    /// hero nocturne. Un compteur **à zéro** est atténué (classe `indicateur-vide`) pour que l'œil
    /// se porte sur les rubriques réellement renseignées.
    private Node construirePastille(IndicateurAccueil indicateur, long valeur) {
        boolean vide = valeur == 0;

        FontIcon icone = new FontIcon(indicateur.iconeLiteral());
        icone.setIconSize(22);
        // Sur le hero sombre, l'accent plein de la feature serait ton sur ton (le bleu « Sites »
        // surtout). On éclaircit la teinte vers le blanc pour qu'elle ressorte tout en gardant son
        // identité ; un compteur à zéro reste en blanc atténué.
        icone.setIconColor(
                vide
                        ? Color.web("#ffffff", 0.55)
                        : Color.web(indicateur.couleur()).interpolate(Color.WHITE, 0.45));

        Label valeurLabel = new Label(Long.toString(valeur));
        valeurLabel.getStyleClass().add("indicateur-valeur");
        Label libelle = new Label(indicateur.libelle());
        libelle.getStyleClass().add("indicateur-libelle");
        VBox texte = new VBox(valeurLabel, libelle);
        texte.getStyleClass().add("indicateur-texte");

        HBox pastille = new HBox(icone, texte);
        pastille.getStyleClass().add("indicateur");
        if (vide) {
            pastille.getStyleClass().add("indicateur-vide");
        }
        return pastille;
    }

    private Node construireCarte(ActiviteAccueil activite) {
        String couleur = activite.couleur();

        // Icône blanche dans une pastille ronde teintée à la couleur de la feature.
        FontIcon icone = new FontIcon(activite.iconeLiteral());
        icone.setIconSize(22);
        icone.setIconColor(Color.WHITE);
        StackPane chip = new StackPane(icone);
        chip.getStyleClass().add("carte-chip");
        chip.setStyle("-fx-background-color: " + couleur + ";");

        Label titre = new Label(activite.titre());
        titre.getStyleClass().add("carte-activite-titre");
        titre.setStyle("-fx-text-fill: " + couleur + ";");
        // Description en nœud `Text` (et non `Label`) : `wrappingWidth` enroule de façon fiable, là
        // où le `wrapText` d'un `Label` posé dans une VBox de largeur fixe se contente d'une ligne
        // tronquée (« … ») selon le calcul de hauteur préférée.
        Text description = new Text(activite.description());
        description.getStyleClass().add("carte-activite-desc");
        description.setWrappingWidth(164);

        // Chevron d'invite, masqué au repos et révélé au survol/focus (cf. base.css).
        FontIcon chevron = new FontIcon("fas-chevron-right");
        chevron.setIconSize(13);
        chevron.setIconColor(Color.web(couleur));
        chevron.getStyleClass().add("carte-chevron");
        HBox pied = new HBox(chevron);
        pied.getStyleClass().add("carte-pied");

        // Espace extensible : il pousse le chevron en bas de carte sans rogner la hauteur de la
        // description (un Vgrow posé sur le pied lui-même affamerait la description, qui tronquerait
        // alors sa seconde ligne).
        Region espace = new Region();
        VBox.setVgrow(espace, Priority.ALWAYS);

        VBox carte = new VBox(chip, titre, description, espace, pied);
        carte.getStyleClass().add("carte-activite");
        carte.setOnMouseClicked(evenement -> activite.ouvrir());

        // Survol/focus : léger soulèvement de la carte (effet « lift » réactif).
        TranslateTransition lift = new TranslateTransition(Duration.millis(120), carte);
        Runnable monter = () -> {
            lift.stop();
            lift.setToY(-4);
            lift.play();
        };
        Runnable redescendre = () -> {
            lift.stop();
            lift.setToY(0);
            lift.play();
        };
        carte.setOnMouseEntered(evenement -> monter.run());
        carte.setOnMouseExited(evenement -> redescendre.run());

        // Accessibilité clavier : la carte (VBox, pas un Control) doit être atteignable au Tab et
        // activable à Entrée/Espace, comme un bouton (opérabilité ISO 25010). On soulève aussi la
        // carte au focus pour que l'utilisateur au clavier ait le même retour visuel qu'à la souris.
        carte.setFocusTraversable(true);
        carte.focusedProperty().addListener((obs, ancien, aLeFocus) -> {
            if (aLeFocus) {
                monter.run();
            } else {
                redescendre.run();
            }
        });
        carte.setOnKeyPressed(evenement -> {
            if (evenement.getCode() == KeyCode.ENTER || evenement.getCode() == KeyCode.SPACE) {
                activite.ouvrir();
            }
        });
        return carte;
    }
}

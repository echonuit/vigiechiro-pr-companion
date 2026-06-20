package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.util.Comparator;
import java.util.Set;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
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

    @FXML
    private BorderPane racine;

    @FXML
    private Label titreApplication;

    @FXML
    private Hyperlink lienAccueil;

    @FXML
    private Label filAriane;

    @FXML
    private Label pied;

    @FXML
    private StackPane hero;

    @FXML
    private FlowPane bandeauIndicateurs;

    @FXML
    private FlowPane cartesActivites;

    @Inject
    public MainController(
            NavigationViewModel navigation,
            Navigateur navigateur,
            Set<ActiviteAccueil> activites,
            Set<IndicateurAccueil> indicateurs) {
        this.navigation = navigation;
        this.navigateur = navigateur;
        this.activites = activites;
        this.indicateurs = indicateurs;
    }

    /// Appelée par le `FXMLLoader` une fois les `@FXML` injectés. Câble les bindings.
    @FXML
    private void initialize() {
        titreApplication.textProperty().bind(navigation.titreApplicationProperty());
        filAriane.textProperty().bind(navigation.filArianeProperty());
        pied.textProperty().bind(navigation.piedDePageProperty());

        // « 🏠 Accueil » : visible seulement hors de l'accueil (sur l'accueil, on y est déjà).
        var horsAccueil = navigation.vueCouranteProperty().isNotEqualTo("accueil");
        lienAccueil.visibleProperty().bind(horsAccueil);
        lienAccueil.managedProperty().bind(horsAccueil);
        // Verrou de navigation (#54) : grisé pendant une opération longue (import en cours) qu'on ne
        // doit pas quitter, pour ne pas perdre le résultat de l'opération en détachant son écran.
        lienAccueil.disableProperty().bind(navigation.navigationVerrouilleeProperty());

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

        // La zone d'accueil déclarée dans le FXML devient la vue centrale initiale, puis le centre
        // du BorderPane suit la propriété du Navigateur : toute navigation passe par afficher(...).
        // On la mémorise pour que les features puissent y revenir (Navigateur.afficherAccueil()).
        Parent accueil = (Parent) racine.getCenter();
        navigateur.memoriserAccueil(accueil);
        navigateur.afficher(accueil);
        racine.centerProperty().bind(navigateur.vueCentraleProperty());
    }

    /// Affordance « 🏠 Accueil » du chrome : ramène à la vue d'accueil (cartes des features) depuis
    /// n'importe quel écran de feature, via le socle [Navigateur#afficherAccueil].
    @FXML
    private void retourAccueil() {
        navigateur.afficherAccueil();
    }

    /// Bâtit une carte cliquable par activité (triées par `ordre()`). Les cartes sont créées en code
    /// car leur nombre dépend des features installées (point d'extension `Set<ActiviteAccueil>`).
    private void peuplerCartes() {
        activites.stream()
                .sorted(Comparator.comparingInt(ActiviteAccueil::ordre))
                .map(this::construireCarte)
                .forEach(cartesActivites.getChildren()::add);
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

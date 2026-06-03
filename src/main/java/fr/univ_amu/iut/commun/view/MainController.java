package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.util.Comparator;
import java.util.Set;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

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
    private FlowPane cartesActivites;

    @Inject
    public MainController(NavigationViewModel navigation, Navigateur navigateur, Set<ActiviteAccueil> activites) {
        this.navigation = navigation;
        this.navigateur = navigateur;
        this.activites = activites;
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

    private Node construireCarte(ActiviteAccueil activite) {
        Label icone = new Label(activite.icone());
        icone.getStyleClass().add("carte-activite-icone");
        Label titre = new Label(activite.titre());
        titre.getStyleClass().add("carte-activite-titre");
        Label description = new Label(activite.description());
        description.getStyleClass().add("carte-activite-desc");
        description.setWrapText(true);
        // Largeur d'enroulement (≈ largeur interne de la carte : 260 - 2×24 de padding) : le texte
        // passe à la ligne au lieu de déborder.
        description.setMaxWidth(210);

        VBox carte = new VBox(icone, titre, description);
        carte.getStyleClass().add("carte-activite");
        carte.setOnMouseClicked(evenement -> activite.ouvrir());
        // Accessibilité clavier : la carte (VBox, pas un Control) doit être atteignable au Tab et
        // activable à Entrée/Espace, comme un bouton (opérabilité ISO 25010).
        carte.setFocusTraversable(true);
        carte.setOnKeyPressed(evenement -> {
            if (evenement.getCode() == KeyCode.ENTER || evenement.getCode() == KeyCode.SPACE) {
                activite.ouvrir();
            }
        });
        return carte;
    }
}

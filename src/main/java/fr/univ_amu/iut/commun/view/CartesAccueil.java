package fr.univ_amu.iut.commun.view;

import javafx.animation.TranslateTransition;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

/// Fabriques de nœuds du **tableau de bord d'accueil** : la pastille d'un indicateur (compteur) et la
/// carte d'une activité (feature). Construction pure, sans état du chrome — extraite de
/// [MainController] pour garder ce dernier centré sur navigation/menu et sous le seuil de God Class
/// (NcssCount) (#789).
final class CartesAccueil {

    private CartesAccueil() {}

    /// Pastille d'un indicateur d'accueil (icône teintée + compteur + libellé) posée sur le hero
    /// nocturne. Un compteur **à zéro** est atténué (classe `indicateur-vide`) pour que l'œil se porte
    /// sur les rubriques réellement renseignées.
    static Node pastille(IndicateurAccueil indicateur, long valeur) {
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

    /// Carte d'une activité d'accueil (chip coloré + titre + description + chevron d'invite), focalisable
    /// au clavier et activable Entrée/Espace comme un bouton, avec effet de soulèvement au survol/focus.
    /// Le clic (ou Entrée/Espace) délègue à [ActiviteAccueil#ouvrir()].
    static Node carte(ActiviteAccueil activite) {
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
        // Un lecteur d'écran doit l'annoncer comme un bouton (pas un conteneur générique) et lire son
        // intitulé : rôle + texte accessibles (#799).
        carte.setAccessibleRole(AccessibleRole.BUTTON);
        carte.setAccessibleText(activite.titre() + " : " + activite.description());
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

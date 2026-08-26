package fr.univ_amu.iut.multisite.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/// Les **contrôles posés par-dessus la carte** de « Carte & passages » : légende en bas à gauche,
/// recadrage en haut à droite, édition des positions en haut à gauche.
///
/// Tenu hors de `MultisiteController` (#3300), et la coupure n'est pas arbitraire : ce bloc ne sait
/// rien de l'écran ni de sa table, seulement de la carte et de ses commandes - le même découpage
/// que [FocalisationCarte] et [ConstructeurDonneesCarte] dans ce paquet.
///
/// Les deux boutons d'édition sont **rendus à l'appelant** : il les active et les désactive selon qu'on
/// est en train d'éditer ou non, ce qui reste une décision d'écran.
final class OverlaysCarteMultisite {

    private OverlaysCarteMultisite() {}

    /// Les deux boutons d'édition, une fois posés.
    ///
    /// @param editer bascule « éditer les positions »
    /// @param enregistrer bouton « enregistrer les positions déplacées »
    record ControlesEdition(ToggleButton editer, Button enregistrer) {}

    /// Pose les trois groupes de contrôles sur `zoneCarte`, et rend ceux que l'appelant doit piloter.
    static ControlesEdition poser(
            StackPane zoneCarte, Runnable recadrerCarte, Runnable basculerEdition, Runnable enregistrerPositions) {
        poserLegende(zoneCarte);
        poserRecadrage(zoneCarte, recadrerCarte);
        return poserEdition(zoneCarte, basculerEdition, enregistrerPositions);
    }

    private static void poserLegende(StackPane zoneCarte) {
        Node legende = LegendeCarte.creer();
        StackPane.setAlignment(legende, Pos.BOTTOM_LEFT);
        StackPane.setMargin(legende, new Insets(8));
        zoneCarte.getChildren().add(legende);
    }

    private static void poserRecadrage(StackPane zoneCarte, Runnable recadrerCarte) {
        Button recadrer = new Button();
        StyleControlesCarte.overlay(
                recadrer, "bouton-recadrer", "fas-expand", "Recadrer la carte sur les éléments visibles");
        recadrer.setOnAction(evenement -> recadrerCarte.run());
        StackPane.setAlignment(recadrer, Pos.TOP_RIGHT);
        StackPane.setMargin(recadrer, new Insets(8));
        zoneCarte.getChildren().add(recadrer);
    }

    private static ControlesEdition poserEdition(
            StackPane zoneCarte, Runnable basculerEdition, Runnable enregistrerPositions) {
        ToggleButton editer = new ToggleButton();
        editer.setId("boutonEditerPositions");
        StyleControlesCarte.overlay(editer, "bouton-editer-positions", "fas-pen", "Éditer les positions des points");
        editer.setOnAction(evenement -> basculerEdition.run());

        Button enregistrer = new Button();
        enregistrer.setId("boutonEnregistrerPositions");
        StyleControlesCarte.overlay(
                enregistrer, "bouton-editer-positions", "fas-save", "Enregistrer les positions déplacées");
        enregistrer.setOnAction(evenement -> enregistrerPositions.run());

        VBox controles = new VBox(6, editer, enregistrer);
        controles.setPickOnBounds(false);
        StackPane.setAlignment(controles, Pos.TOP_LEFT);
        StackPane.setMargin(controles, new Insets(8));
        zoneCarte.getChildren().add(controles);
        return new ControlesEdition(editer, enregistrer);
    }
}

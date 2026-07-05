package fr.univ_amu.iut.audio.view;

import java.util.function.Consumer;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

/// Petit **éditeur de commentaire en popup**, ancré à une cellule de la table audio (#477). Ouvert au clic
/// sur la case commentaire, il présente une zone de texte pré-remplie avec le commentaire courant et un
/// bouton « Enregistrer » ; à la validation, il transmet le nouveau texte à `enregistrer` (qui appellera
/// `AudioViewModel.commenter`). Le popup se referme automatiquement en cliquant en dehors (`autoHide`).
///
/// Sorti du rendu de cellule ([CellulesAudio]) pour rester testable en isolation (un ancre + un
/// [Consumer]) et garder la cellule concentrée sur son affichage.
final class EditeurCommentaire {

    private EditeurCommentaire() {}

    /// Ouvre le popup sous `ancre`, pré-rempli avec `commentaireActuel` (peut être `null`). À l'appui sur
    /// « Enregistrer », appelle `enregistrer` avec le texte saisi (l'effacement se gère côté service : un
    /// texte vide/blanc efface le commentaire) puis referme. « Annuler » referme sans rien transmettre.
    static void ouvrir(Node ancre, String commentaireActuel, Consumer<String> enregistrer) {
        TextArea zone = new TextArea(commentaireActuel == null ? "" : commentaireActuel);
        zone.setPrefRowCount(3);
        zone.setPrefColumnCount(28);
        zone.setWrapText(true);

        Popup popup = new Popup();
        popup.setAutoHide(true);

        Button enregistrerBouton = new Button("Enregistrer");
        enregistrerBouton.getStyleClass().add("bouton-enregistrer-commentaire");
        enregistrerBouton.setOnAction(evenement -> {
            enregistrer.accept(zone.getText());
            popup.hide();
        });
        Button annulerBouton = new Button("Annuler");
        annulerBouton.setOnAction(evenement -> popup.hide());

        HBox actions = new HBox(8.0, enregistrerBouton, annulerBouton);
        VBox contenu = new VBox(8.0, new Label("Commentaire de l'observation"), zone, actions);
        contenu.getStyleClass().add("popup-commentaire");
        popup.getContent().add(contenu);

        Bounds ecran = ancre.localToScreen(ancre.getBoundsInLocal());
        popup.show(ancre, ecran.getMinX(), ecran.getMaxY());
        zone.requestFocus();
        zone.positionCaret(zone.getText().length());
    }
}

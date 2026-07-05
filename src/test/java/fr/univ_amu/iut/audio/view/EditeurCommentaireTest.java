package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Éditeur de commentaire en popup (#477) : pré-remplissage, enregistrement (transmet le texte saisi) et
/// annulation (ne transmet rien). Ancré à un bouton témoin dans une scène minimale.
@ExtendWith(ApplicationExtension.class)
class EditeurCommentaireTest {

    private Button ancre;

    @Start
    void start(Stage stage) {
        ancre = new Button("ancre");
        stage.setScene(new Scene(new VBox(ancre), 240, 120));
        stage.show();
    }

    @Test
    @DisplayName("Le popup pré-remplit le commentaire actuel")
    void prefille_le_commentaire_actuel(FxRobot robot) {
        AtomicReference<String> sauvegarde = new AtomicReference<>();
        robot.interact(() -> EditeurCommentaire.ouvrir(ancre, "déjà là", sauvegarde::set));

        TextArea zone = robot.lookup(".popup-commentaire .text-area").query();
        assertThat(zone.getText()).isEqualTo("déjà là");
    }

    @Test
    @DisplayName("Enregistrer transmet le texte saisi")
    void enregistrer_transmet_le_texte(FxRobot robot) {
        AtomicReference<String> sauvegarde = new AtomicReference<>();
        robot.interact(() -> EditeurCommentaire.ouvrir(ancre, "avant", sauvegarde::set));

        TextArea zone = robot.lookup(".popup-commentaire .text-area").query();
        robot.interact(() -> zone.setText("nouveau commentaire"));
        robot.clickOn(".bouton-enregistrer-commentaire");

        assertThat(sauvegarde.get()).isEqualTo("nouveau commentaire");
    }

    @Test
    @DisplayName("Annuler ne transmet rien")
    void annuler_ne_transmet_rien(FxRobot robot) {
        AtomicReference<String> sauvegarde = new AtomicReference<>("intact");
        robot.interact(() -> EditeurCommentaire.ouvrir(ancre, "avant", sauvegarde::set));

        robot.clickOn("Annuler");

        assertThat(sauvegarde.get()).isEqualTo("intact");
    }
}

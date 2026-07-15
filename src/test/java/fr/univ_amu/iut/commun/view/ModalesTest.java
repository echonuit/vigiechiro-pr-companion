package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Garde-fou du patron commun de fermeture des modales par Échap (#1505). On équipe un Stage réel via
/// [Modales#fermerParEchap] puis on vérifie qu'Échap le ferme et qu'une autre touche ne fait rien.
@ExtendWith(ApplicationExtension.class)
class ModalesTest {

    private Stage modale;

    @Start
    void demarrer(Stage stage) {
        modale = stage;
        modale.setScene(new Scene(new StackPane(new Button("OK")), 240, 140));
        Modales.fermerParEchap(modale);
        modale.show();
    }

    private void frapper(KeyCode code) {
        Event.fireEvent(
                modale.getScene().getRoot(),
                new KeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, "", code, false, false, false, false));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("#1505 : Échap ferme la modale")
    void echap_ferme_la_modale(FxRobot robot) {
        assertThat(modale.isShowing()).isTrue();

        robot.interact(() -> frapper(KeyCode.ESCAPE));

        assertThat(modale.isShowing()).as("Échap ferme la fenêtre modale").isFalse();
    }

    @Test
    @DisplayName("#1505 : une autre touche ne ferme pas la modale")
    void autre_touche_ne_ferme_pas(FxRobot robot) {
        robot.interact(() -> frapper(KeyCode.A));

        assertThat(modale.isShowing()).as("seule Échap ferme").isTrue();
    }
}

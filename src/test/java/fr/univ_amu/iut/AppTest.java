package fr.univ_amu.iut;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Smoke test JavaFX : vérifie que la scène est rendue et que le label "JavaFX fonctionne !" est
/// effectivement affiché par TestFX.
@ExtendWith(ApplicationExtension.class)
class AppTest {

  @Start
  void start(Stage stage) {
    stage.setScene(null); // évite la fuite de Scene entre tests (TestFX réutilise le Stage)
    new App().start(stage);
  }

  @Test
  void le_label_est_affiche(FxRobot robot) {
    Label label = robot.lookup("JavaFX fonctionne !").queryAs(Label.class);
    assertThat(label).isNotNull();
    assertThat(label.getText()).isEqualTo("JavaFX fonctionne !");
  }
}

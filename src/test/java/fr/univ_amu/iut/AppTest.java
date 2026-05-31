package fr.univ_amu.iut;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Smoke test JavaFX du bootstrap : vérifie que le chrome principal (`MainView`) est chargé via
/// le `FXMLLoader` + la `controllerFactory` Guice, et que la barre de navigation affiche bien le
/// titre de l'application. Tourne en headless via la Headless Platform JavaFX 26
/// (glass.platform=Headless), sans fenêtre ni serveur d'affichage, localement comme en CI.
@ExtendWith(ApplicationExtension.class)
class AppTest {

  @Start
  void start(Stage stage) throws Exception {
    stage.setScene(null); // évite la fuite de Scene entre tests (TestFX réutilise le Stage)
    new App().start(stage);
  }

  @Test
  void le_chrome_principal_est_affiche(FxRobot robot) {
    Label titre = robot.lookup("#titreApplication").queryAs(Label.class);
    assertThat(titre).isNotNull();
    assertThat(titre.getText()).isEqualTo("VigieChiro PR Companion");
  }
}

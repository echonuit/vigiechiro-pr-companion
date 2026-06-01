package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.viewmodel.ContexteSite;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de la **façade** [NavigationPassage] : on boote le vrai injecteur
/// applicatif ([RacineInjecteur]) sur une base SQLite jetable, on charge le chrome, puis on appelle
/// `ouvrir(idPassage, contexteSite)`. On vérifie que l'écran M-Passage se charge et s'affiche via
/// Guice + le `Navigateur` du socle (ressource FXML, controllerFactory, publication), comme
/// `NavigationQualificationViewTest`.
///
/// Le passage demandé est absent : le ViewModel neutralise l'erreur dans son message, ce qui suffit
/// à exercer toute la chaîne de navigation sans seeding d'un passage complet.
@ExtendWith(ApplicationExtension.class)
class NavigationPassageViewTest {

  @Start
  void start(Stage stage) throws Exception {
    Path workspace = Files.createTempDirectory("vc-passage");
    System.setProperty("vigiechiro.workspace", workspace.toString());
    Injector injector = RacineInjecteur.creer();
    SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
    new MigrationSchema(source).migrer();
    FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
    loader.setControllerFactory(injector::getInstance);
    Parent racine = loader.load();
    stage.setScene(new Scene(racine, 1100, 760));
    injector
        .getInstance(NavigationPassage.class)
        .ouvrir(999L, new ContexteSite("640380", "A1", "Étang"));
    stage.show();
  }

  @AfterEach
  void nettoyerWorkspace() {
    System.clearProperty("vigiechiro.workspace");
  }

  @Test
  @DisplayName("ouvrir(idPassage, contexte) charge l'écran M-Passage via Guice")
  void ouvrir_affiche_l_ecran(FxRobot robot) {
    Label message = robot.lookup("#lblMessage").queryAs(Label.class);

    assertThat(message.getText()).contains("introuvable");
  }
}

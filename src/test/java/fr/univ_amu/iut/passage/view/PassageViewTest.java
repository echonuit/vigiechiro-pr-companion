package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de la coquille de l'écran **M-Passage** : chargement du FXML via Guice
/// (avec un [ServicePassage] mocké), ouverture sur un passage + contexte site, et vérification du
/// câblage (titre/bandeau d'identité, stepper de statut). Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class PassageViewTest {

  private static final long ID_PASSAGE = 42L;

  @Start
  void start(Stage stage) throws Exception {
    ServicePassage service = mock(ServicePassage.class);
    when(service.detailPassage(anyLong()))
        .thenReturn(
            new DetailPassage(
                2,
                2026,
                "2026-06-22",
                "20:25:00",
                "07:47:00",
                "1925492",
                StatutWorkflow.VERIFIE,
                Verdict.OK,
                null,
                4096L,
                1024L,
                30,
                150.0));
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Provides
              PassageViewModel viewModel() {
                return new PassageViewModel(service);
              }
            });
    FXMLLoader loader = new FXMLLoader(PassageController.class.getResource("Passage.fxml"));
    loader.setControllerFactory(injector::getInstance);
    Parent vue = loader.load();
    PassageController controleur = loader.getController();
    controleur.ouvrirSur(ID_PASSAGE, new ContexteSite("640380", "A1", "Étang de la Tuilière"));
    stage.setScene(new Scene(vue, 1100, 700));
    stage.show();
  }

  @Test
  @DisplayName("Le bandeau affiche l'identité du passage (carré, point, statut)")
  void affiche_l_identite(FxRobot robot) {
    Label titre = robot.lookup("#lblTitre").queryAs(Label.class);
    Label statut = robot.lookup("#lblStatut").queryAs(Label.class);

    assertThat(titre.getText()).contains("640380").contains("A1").contains("N° 2");
    assertThat(statut.getText()).isEqualTo("Vérifié");
  }

  @Test
  @DisplayName("Le stepper affiche les 5 étapes du workflow")
  void stepper_affiche_les_etapes(FxRobot robot) {
    HBox stepper = robot.lookup("#stepper").queryAs(HBox.class);

    assertThat(stepper.getChildren()).hasSize(5);
  }
}

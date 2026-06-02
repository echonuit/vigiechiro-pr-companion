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
import fr.univ_amu.iut.passage.viewmodel.RattachementViewModel;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de la modale **« Modifier le rattachement »** : chargement du FXML via
/// Guice (avec un [ServicePassage] mocké), `demarrer` sur un passage, vérification du câblage
/// (Spinners pré-remplis en bidirectionnel + récapitulatif réactif). Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class RattachementModaleViewTest {

  @Start
  void start(Stage stage) throws Exception {
    ServicePassage service = mock(ServicePassage.class);
    when(service.detailPassage(anyLong()))
        .thenReturn(
            new DetailPassage(
                1,
                2026,
                "2026-06-20",
                "21:00:00",
                "05:00:00",
                "1925492",
                StatutWorkflow.TRANSFORME,
                Verdict.OK,
                null,
                0L,
                0L,
                30,
                0.0));
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Provides
              RattachementViewModel viewModel() {
                return new RattachementViewModel(service);
              }
            });
    FXMLLoader loader =
        new FXMLLoader(RattachementModaleController.class.getResource("RattachementModale.fxml"));
    loader.setControllerFactory(injector::getInstance);
    Parent vue = loader.load();
    RattachementModaleController controleur = loader.getController();
    controleur.demarrer(7L, "040962", "A1", () -> {});
    stage.setScene(new Scene(vue));
    stage.show();
  }

  @Test
  @DisplayName("Les spinners sont pré-remplis et le récap est neutre tant que rien ne change")
  void prerempli_et_recap_neutre(FxRobot robot) {
    Spinner<?> annee = robot.lookup("#spinnerAnnee").queryAs(Spinner.class);
    Spinner<?> numero = robot.lookup("#spinnerNumero").queryAs(Spinner.class);
    Label recap = robot.lookup("#labelRecap").queryAs(Label.class);

    assertThat(annee.getValue()).isEqualTo(2026);
    assertThat(numero.getValue()).isEqualTo(1);
    assertThat(recap.getText()).contains("Aucun changement");
  }

  @Test
  @DisplayName("Changer le n° dans le spinner met à jour le récap (quadruplet X → Y)")
  void changer_numero_met_a_jour_le_recap(FxRobot robot) {
    @SuppressWarnings("unchecked")
    Spinner<Integer> numero = robot.lookup("#spinnerNumero").queryAs(Spinner.class);
    Label recap = robot.lookup("#labelRecap").queryAs(Label.class);

    robot.interact(() -> numero.getValueFactory().setValue(2));

    assertThat(recap.getText())
        .contains("Car040962-2026-Pass1-A1")
        .contains("Car040962-2026-Pass2-A1");
  }

  @Test
  @DisplayName("Le spinner n° n'a pas de borne haute arbitraire (pas d'écrêtage sous le domaine)")
  void spinner_numero_sans_borne_haute(FxRobot robot) {
    @SuppressWarnings("unchecked")
    Spinner<Integer> numero = robot.lookup("#spinnerNumero").queryAs(Spinner.class);

    robot.interact(() -> numero.getValueFactory().setValue(100000));

    assertThat(numero.getValue()).isEqualTo(100000);
  }
}

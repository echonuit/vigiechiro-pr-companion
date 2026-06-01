package fr.univ_amu.iut.diagnostic.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.diagnostic.model.AnalyseAnomalies;
import fr.univ_amu.iut.diagnostic.model.Diagnostic;
import fr.univ_amu.iut.diagnostic.model.MesureClimatique;
import fr.univ_amu.iut.diagnostic.model.SerieClimatique;
import fr.univ_amu.iut.diagnostic.model.ServiceDiagnostic;
import fr.univ_amu.iut.diagnostic.viewmodel.DiagnosticViewModel;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de l'écran **M-Diagnostic** : chargement du FXML via Guice (avec un
/// [ServiceDiagnostic] mocké), ouverture sur un passage, vérification du câblage (graphe à deux
/// séries T°/hygrométrie, listes d'anomalies/évènements, enregistreur). Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class DiagnosticViewTest {

  @Start
  void start(Stage stage) throws Exception {
    ServiceDiagnostic service = mock(ServiceDiagnostic.class);
    when(service.diagnostiquer(anyLong()))
        .thenReturn(
            new Diagnostic(
                42L,
                7L,
                "1925492",
                new AnalyseAnomalies(List.of("Réveil non programmé à 03:12"), List.of("Démarrage")),
                SerieClimatique.presente(
                    List.of(
                        new MesureClimatique(
                            LocalDate.of(2026, 6, 22), LocalTime.of(22, 0), 18.5, 72),
                        new MesureClimatique(
                            LocalDate.of(2026, 6, 23), LocalTime.of(2, 0), 14.0, 88))),
                43.5,
                5.4,
                LocalDateTime.of(2026, 6, 23, 8, 0)));
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Provides
              DiagnosticViewModel viewModel() {
                return new DiagnosticViewModel(service);
              }
            });
    FXMLLoader loader = new FXMLLoader(DiagnosticController.class.getResource("Diagnostic.fxml"));
    loader.setControllerFactory(injector::getInstance);
    Parent vue = loader.load();
    DiagnosticController controleur = loader.getController();
    controleur.ouvrirSur(42L);
    stage.setScene(new Scene(vue, 1000, 760));
    stage.show();
  }

  @Test
  @DisplayName("Le graphe affiche deux séries (température + humidité) et les listes sont peuplées")
  void affiche_graphe_et_listes(FxRobot robot) {
    LineChart<?, ?> graphe = robot.lookup("#grapheClimat").queryAs(LineChart.class);
    ListView<?> anomalies = robot.lookup("#listeAnomalies").queryAs(ListView.class);
    Label enregistreur = robot.lookup("#lblEnregistreur").queryAs(Label.class);

    assertThat(graphe.getData()).hasSize(2);
    assertThat(graphe.getData().get(0).getData()).hasSize(2);
    assertThat(anomalies.getItems()).hasSize(1);
    assertThat(enregistreur.getText()).isEqualTo("PR 1925492");
  }
}

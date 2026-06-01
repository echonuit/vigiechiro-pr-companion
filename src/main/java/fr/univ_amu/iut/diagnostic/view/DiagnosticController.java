package fr.univ_amu.iut.diagnostic.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.diagnostic.model.MesureClimatique;
import fr.univ_amu.iut.diagnostic.viewmodel.DiagnosticViewModel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

/// Controller de l'écran **M-Diagnostic** (`Diagnostic.fxml`).
///
/// Pur câblage (patron CM4) : lie les contrôles au [DiagnosticViewModel] — graphe T°/hygrométrie
/// (reconstruit depuis la série du VM), listes d'anomalies et d'évènements (R19), signalement
/// d'absence de relevé (R20) et disponibilité GPS. Aucun accès base de données ni logique métier
/// ici (règle ArchUnit `view_sans_jdbc`).
public class DiagnosticController {

  private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

  private final DiagnosticViewModel viewModel;

  @FXML private Label lblEnregistreur;
  @FXML private Label lblReleveAbsent;
  @FXML private Label lblResumeClimat;
  @FXML private LineChart<String, Number> grapheClimat;
  @FXML private ListView<String> listeAnomalies;
  @FXML private ListView<String> listeEvenements;
  @FXML private Label lblGps;
  @FXML private Label lblMessage;

  @Inject
  public DiagnosticController(DiagnosticViewModel viewModel) {
    this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
  }

  @FXML
  private void initialize() {
    lblEnregistreur.textProperty().bind(viewModel.enregistreurProperty());
    lblResumeClimat.textProperty().bind(viewModel.resumeClimatProperty());
    lblReleveAbsent.visibleProperty().bind(viewModel.releveClimatiqueAbsentProperty());
    lblReleveAbsent.managedProperty().bind(viewModel.releveClimatiqueAbsentProperty());

    listeAnomalies.setItems(viewModel.anomalies());
    listeEvenements.setItems(viewModel.evenements());

    viewModel
        .mesures()
        .addListener((ListChangeListener<MesureClimatique>) changement -> majGraphe());
    majGraphe();

    lblGps
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () ->
                    viewModel.gpsDisponibleProperty().get()
                        ? "📍 GPS du point disponible (cohérence horaires possible)."
                        : "📍 GPS du point non renseigné : cohérence horaires indisponible.",
                viewModel.gpsDisponibleProperty()));
    // La note GPS n'a de sens qu'une fois un diagnostic chargé : masquée à l'erreur / au démarrage.
    var diagnosticCharge = viewModel.enregistreurProperty().isNotEmpty();
    lblGps.visibleProperty().bind(diagnosticCharge);
    lblGps.managedProperty().bind(diagnosticCharge);

    lblMessage.textProperty().bind(viewModel.messageProperty());
    var messagePresent = viewModel.messageProperty().isNotEmpty();
    lblMessage.visibleProperty().bind(messagePresent);
    lblMessage.managedProperty().bind(messagePresent);
  }

  /// Ouvre le diagnostic du passage `idPassage`. Appelée par [NavigationDiagnostic] après le
  /// chargement du FXML.
  public void ouvrirSur(Long idPassage) {
    viewModel.ouvrirSur(idPassage);
  }

  private void majGraphe() {
    XYChart.Series<String, Number> temperature = new XYChart.Series<>();
    temperature.setName("T° (°C)");
    XYChart.Series<String, Number> humidite = new XYChart.Series<>();
    humidite.setName("Humidité (%)");
    for (MesureClimatique mesure : viewModel.mesures()) {
      String moment = LocalDateTime.of(mesure.date(), mesure.heure()).format(MOMENT);
      temperature.getData().add(new XYChart.Data<>(moment, mesure.temperatureCelsius()));
      humidite.getData().add(new XYChart.Data<>(moment, mesure.humiditePourcent()));
    }
    grapheClimat.getData().setAll(List.of(temperature, humidite));
  }
}

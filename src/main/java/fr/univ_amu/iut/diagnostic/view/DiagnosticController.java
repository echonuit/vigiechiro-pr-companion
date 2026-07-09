package fr.univ_amu.iut.diagnostic.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.diagnostic.model.MesureClimatique;
import fr.univ_amu.iut.diagnostic.viewmodel.DiagnosticViewModel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
///
/// Implémente [ResumeStatut] (#693) : l'enregistreur diagnostiqué, jusqu'ici en sous-titre d'en-tête,
/// est déporté en barre de statut (le titre « Diagnostic matériel » étant redondant avec le fil d'Ariane).
public class DiagnosticController implements EmplacementNavigation, ResumeStatut {

    //   Le graphe T°/hygrométrie se reconstruit depuis viewModel.mesures().
    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final DiagnosticViewModel viewModel;
    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;

    /// Contexte de navigation (passage + site), mémorisé pour reconstruire le fil d'Ariane du chrome.
    private ContextePassage contexte;

    /// Enregistreur diagnostiqué, déporté en zone centre de la barre de statut (#693) au lieu d'un sous-titre.
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    @FXML
    private Label lblReleveAbsent;

    @FXML
    private Label lblResumeClimat;

    @FXML
    private Label lblTemperature;

    @FXML
    private LineChart<String, Number> grapheClimat;

    @FXML
    private ListView<String> listeAnomalies;

    @FXML
    private ListView<String> listeEvenements;

    @FXML
    private Label lblFenetreNuit;

    @FXML
    private Label lblAlerteHorsNuit;

    @FXML
    private Label lblGps;

    @FXML
    private Label lblMessage;

    @Inject
    public DiagnosticController(DiagnosticViewModel viewModel, OuvrirSite ouvrirSite, OuvrirPassage ouvrirPassage) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
    }

    @Override
    public ReadOnlyObjectProperty<ZonesStatut> zonesStatutProperty() {
        return zonesStatut.getReadOnlyProperty();
    }

    @FXML
    private void initialize() {
        // L'enregistreur diagnostiqué est déporté en zone centre de la barre de statut (#693).
        zonesStatut.bind(Bindings.createObjectBinding(
                () -> ZonesStatut.centre(viewModel.enregistreurProperty().get()), viewModel.enregistreurProperty()));
        lblResumeClimat.textProperty().bind(viewModel.resumeClimatProperty());
        lblTemperature
                .textProperty()
                .bind(Bindings.concat("🌡 Température en début de nuit : ", viewModel.temperatureProperty()));
        lblReleveAbsent.visibleProperty().bind(viewModel.releveClimatiqueAbsentProperty());
        lblReleveAbsent.managedProperty().bind(viewModel.releveClimatiqueAbsentProperty());

        listeAnomalies.setItems(viewModel.anomalies());
        listeEvenements.setItems(viewModel.evenements());

        viewModel.mesures().addListener((ListChangeListener<MesureClimatique>) changement -> majGraphe());
        majGraphe();

        // Encart cohérence horaires (#548) : la fenêtre nocturne réelle quand elle est calculable.
        lblFenetreNuit.textProperty().bind(viewModel.fenetreNuitProperty());
        lblFenetreNuit.visibleProperty().bind(viewModel.coherenceHoraireDisponibleProperty());
        lblFenetreNuit.managedProperty().bind(viewModel.coherenceHoraireDisponibleProperty());

        // Alerte « hors nuit » : visible seulement quand un écart est détecté (texte non vide).
        lblAlerteHorsNuit.textProperty().bind(viewModel.alerteHorsNuitProperty());
        var horsNuit = viewModel.alerteHorsNuitProperty().isNotEmpty();
        lblAlerteHorsNuit.visibleProperty().bind(horsNuit);
        lblAlerteHorsNuit.managedProperty().bind(horsNuit);

        lblGps.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> viewModel.gpsDisponibleProperty().get()
                                ? "📍 GPS du point disponible, mais horaires incomplets : cohérence horaires indisponible."
                                : "📍 GPS du point non renseigné : cohérence horaires indisponible.",
                        viewModel.gpsDisponibleProperty()));
        // La note GPS n'explique l'absence d'encart qu'une fois un diagnostic chargé sans fenêtre calculée.
        var diagnosticCharge = viewModel.enregistreurProperty().isNotEmpty();
        var coherenceIndisponible = diagnosticCharge.and(
                viewModel.coherenceHoraireDisponibleProperty().not());
        lblGps.visibleProperty().bind(coherenceIndisponible);
        lblGps.managedProperty().bind(coherenceIndisponible);

        lblMessage.textProperty().bind(viewModel.messageProperty());
        var messagePresent = viewModel.messageProperty().isNotEmpty();
        lblMessage.visibleProperty().bind(messagePresent);
        lblMessage.managedProperty().bind(messagePresent);
    }

    /// Ouvre le diagnostic du passage `passage`. Appelée par [NavigationDiagnostic] après le chargement
    /// du FXML ; mémorise le contexte pour le fil d'Ariane.
    public void ouvrirSur(ContextePassage passage) {
        this.contexte = passage;
        viewModel.ouvrirSur(passage.idPassage());
    }

    /// Emplacement dans le fil d'Ariane : `Mes sites › Carré N › Détails du passage N° X › Diagnostic
    /// matériel` (rendu par le chrome). Le segment passage rouvre M-Passage.
    @Override
    public List<Lieu> emplacement() {
        return EmplacementPassage.emplacementEnfant(contexte, ouvrirSite, ouvrirPassage, "Diagnostic matériel");
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

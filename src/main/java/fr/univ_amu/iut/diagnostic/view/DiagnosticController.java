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
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

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
    private HBox ligneGps;

    @FXML
    private FontIcon iconeGps;

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

    /// Compose les 3 zones de la barre de statut (#1022) : identité du passage (gauche), matériel du
    /// diagnostic (centre), et à droite l'**alerte prioritaire** — hors-nuit puis relevé climatique absent.
    private ZonesStatut calculerZonesStatut() {
        String gauche = contexte == null ? "" : contexte.identiteStatut();
        String releveAbsent = viewModel.releveClimatiqueAbsentProperty().get() ? "⚠ Relevé climatique absent" : "";
        String droite =
                ZonesStatut.premierNonVide(viewModel.alerteHorsNuitProperty().get(), releveAbsent);
        return new ZonesStatut(gauche, viewModel.enregistreurProperty().get(), droite);
    }

    @FXML
    private void initialize() {
        // Barre de statut 3 zones (#1022, EPIC #1016) : contexte du passage à gauche, matériel (enregistreur)
        // au centre, alerte prioritaire à droite (hors-nuit > relevé climatique absent).
        zonesStatut.bind(Bindings.createObjectBinding(
                this::calculerZonesStatut,
                viewModel.enregistreurProperty(),
                viewModel.alerteHorsNuitProperty(),
                viewModel.releveClimatiqueAbsentProperty()));
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

        // Disponibilité GPS du point d'écoute (#1497) : ligne d'état permanente, affichée dès qu'un
        // diagnostic est chargé et découplée de la cohérence horaires (le repère ne disparaît plus
        // sur une nuit complète). L'absence est dite, jamais muette ; la provenance est le point
        // d'écoute (feature sites), pas le capteur.
        lblGps.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> viewModel.gpsDisponibleProperty().get()
                                ? "GPS du point : disponible"
                                : "GPS du point : non renseigné (compléter la fiche site)",
                        viewModel.gpsDisponibleProperty()));
        viewModel.gpsDisponibleProperty().addListener((observable, avant, disponible) -> majEtatGps(disponible));
        majEtatGps(viewModel.gpsDisponibleProperty().get());
        var diagnosticCharge = viewModel.enregistreurProperty().isNotEmpty();
        ligneGps.visibleProperty().bind(diagnosticCharge);
        ligneGps.managedProperty().bind(diagnosticCharge);

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

    /// Bascule l'icône et la classe d'état de la ligne GPS (#1497) : marqueur de localisation vert
    /// « disponible » ou triangle d'avertissement ambre « non renseigné ». Le libellé, lui, est lié.
    private void majEtatGps(boolean disponible) {
        iconeGps.setIconLiteral(disponible ? "fas-map-marker-alt" : "fas-exclamation-triangle");
        ligneGps.getStyleClass().removeAll("gps-disponible", "gps-absent");
        ligneGps.getStyleClass().add(disponible ? "gps-disponible" : "gps-absent");
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

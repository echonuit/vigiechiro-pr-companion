package fr.univ_amu.iut.analyse.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.analyse.model.CourbeEspece;
import fr.univ_amu.iut.analyse.model.LargeurTranche;
import fr.univ_amu.iut.analyse.model.PointActivite;
import fr.univ_amu.iut.analyse.viewmodel.ActiviteViewModel;
import fr.univ_amu.iut.commun.model.Nuit;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.util.StringConverter;

/// Controller de l'écran **M-Activite** (`Activite.fxml`, #2352, lot 2 du chantier #2348).
///
/// Pur câblage (patron CM4, comme [fr.univ_amu.iut.diagnostic.view.DiagnosticController]) : lie les
/// contrôles au [ActiviteViewModel]. La courbe se trace sur un **axe nocturne fixe 18 h → 8 h** (et non
/// 0 h → 24 h, qui couperait la nuit en deux) : l'abscisse est le nombre de minutes depuis 18 h, l'heure
/// du jour est reconstruite en étiquette. Aucun accès base ni logique métier ici (règle ArchUnit
/// `view_sans_jdbc`).
public class ActiviteController implements EmplacementNavigation {

    /// Largeur de la fenêtre nocturne affichée, en minutes : de 18 h à 8 h le lendemain, soit 14 heures.
    private static final int MINUTES_FENETRE = 14 * 60;

    /// Origine de l'axe : 18 h. Chaque contact est placé à sa distance en minutes de ce repère, et les
    /// étiquettes de graduation reconstruisent l'heure du jour à partir de lui.
    private static final LocalTime DEBUT_FENETRE = LocalTime.of(18, 0);

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH");

    private final ActiviteViewModel viewModel;
    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;

    /// Contexte de navigation (passage + site), mémorisé pour reconstruire le fil d'Ariane du chrome.
    private ContextePassage contexte;

    @FXML
    private LineChart<Number, Number> grapheActivite;

    @FXML
    private ChoiceBox<LargeurTranche> choixTranche;

    @FXML
    private FlowPane selecteurEspeces;

    @FXML
    private Label lblEtatVide;

    @Inject
    public ActiviteController(ActiviteViewModel viewModel, OuvrirSite ouvrirSite, OuvrirPassage ouvrirPassage) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
    }

    @FXML
    private void initialize() {
        configurerAxeNocturne();

        choixTranche.setItems(FXCollections.observableArrayList(LargeurTranche.values()));
        choixTranche.setConverter(new StringConverter<>() {
            @Override
            public String toString(LargeurTranche tranche) {
                return tranche == null ? "" : libelleTranche(tranche);
            }

            @Override
            public LargeurTranche fromString(String texte) {
                return null;
            }
        });
        choixTranche.valueProperty().bindBidirectional(viewModel.trancheProperty());

        // Le graphe cède la place à un état vide tant qu'aucune espèce n'a été détectée cette nuit.
        lblEtatVide.visibleProperty().bind(Bindings.isEmpty(viewModel.especes()));
        lblEtatVide.managedProperty().bind(Bindings.isEmpty(viewModel.especes()));
        grapheActivite.visibleProperty().bind(Bindings.isNotEmpty(viewModel.especes()));
        grapheActivite.managedProperty().bind(Bindings.isNotEmpty(viewModel.especes()));

        viewModel.courbesAffichees().addListener((ListChangeListener<CourbeEspece>) changement -> majGraphe());
        viewModel.especes().addListener((ListChangeListener<CourbeEspece>) changement -> construireSelecteur());
        viewModel.especesSelectionnees().addListener((SetChangeListener<String>) changement -> construireSelecteur());
        majGraphe();
        construireSelecteur();
    }

    /// Ouvre l'activité **d'un passage** (entrée depuis M-Passage). Appelée par [NavigationActivite] après
    /// le chargement du FXML ; mémorise le contexte pour le fil d'Ariane.
    public void ouvrirSur(ContextePassage passage) {
        this.contexte = passage;
        viewModel.chargerPassage(passage.idPassage());
    }

    /// Ouvre l'activité de **tous les passages** de l'utilisateur (entrée transverse, écran racine). Sans
    /// contexte de passage : le fil d'Ariane se réduit au segment courant.
    public void ouvrirTout(String idUtilisateur) {
        this.contexte = null;
        viewModel.chargerUtilisateur(idUtilisateur);
    }

    /// Emplacement dans le fil d'Ariane. Depuis un passage : `Mes sites › Carré N › Détails du passage N° X
    /// › Activité de la nuit`. En transverse (racine, sans contexte) : le seul segment courant, le chrome
    /// préfixant « Accueil ».
    @Override
    public List<Lieu> emplacement() {
        if (contexte == null) {
            return List.of(Lieu.courant("Activité de la nuit"));
        }
        return EmplacementPassage.emplacementEnfant(contexte, ouvrirSite, ouvrirPassage, "Activité de la nuit");
    }

    /// Axe des abscisses **fixe** de 0 (18 h) à 840 minutes (8 h le lendemain), une graduation par heure,
    /// étiquetée `HH` en reconstruisant l'heure du jour depuis 18 h. Fixe et non calé sur les données pour
    /// que deux nuits se comparent d'un coup d'œil.
    private void configurerAxeNocturne() {
        NumberAxis axeTemps = (NumberAxis) grapheActivite.getXAxis();
        axeTemps.setAutoRanging(false);
        axeTemps.setLowerBound(0);
        axeTemps.setUpperBound(MINUTES_FENETRE);
        axeTemps.setTickUnit(60);
        axeTemps.setMinorTickCount(0);
        axeTemps.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number minutes) {
                return DEBUT_FENETRE.plusMinutes(minutes.longValue()).format(HEURE);
            }

            @Override
            public Number fromString(String texte) {
                return 0;
            }
        });
    }

    /// Reconstruit une série par espèce affichée : chaque tranche devient un point placé à sa minute
    /// depuis 18 h. Le nom de série (légende) est le nom vernaculaire, ou le code à défaut.
    private void majGraphe() {
        List<XYChart.Series<Number, Number>> series = viewModel.courbesAffichees().stream()
                .map(ActiviteController::versSerie)
                .toList();
        grapheActivite.getData().setAll(series);
    }

    private static XYChart.Series<Number, Number> versSerie(CourbeEspece courbe) {
        XYChart.Series<Number, Number> serie = new XYChart.Series<>();
        serie.setName(nomAffiche(courbe));
        for (PointActivite point : courbe.points()) {
            serie.getData().add(new XYChart.Data<>(minutesDepuis18h(point.debutTranche()), point.nombre()));
        }
        return serie;
    }

    /// (Re)construit le sélecteur d'espèces : une case par espèce de la nuit, cochée si sélectionnée. La
    /// reconstruction reflète toujours l'état du ViewModel (espèces présentes et sélection courante) ;
    /// l'écouteur de chaque case est posé **après** son état initial, pour ne pas se déclencher pendant la
    /// construction.
    private void construireSelecteur() {
        selecteurEspeces.getChildren().clear();
        for (CourbeEspece courbe : viewModel.especes()) {
            String taxon = courbe.taxon();
            CheckBox case_ = new CheckBox(nomAffiche(courbe) + " (" + courbe.total() + ")");
            case_.setSelected(viewModel.especesSelectionnees().contains(taxon));
            case_.selectedProperty().addListener((observable, avant, coche) -> majSelection(taxon, coche));
            selecteurEspeces.getChildren().add(case_);
        }
    }

    private void majSelection(String taxon, boolean coche) {
        if (coche) {
            viewModel.especesSelectionnees().add(taxon);
        } else {
            viewModel.especesSelectionnees().remove(taxon);
        }
    }

    private static String nomAffiche(CourbeEspece courbe) {
        return courbe.nomEspece() != null ? courbe.nomEspece() : courbe.taxon();
    }

    private static String libelleTranche(LargeurTranche tranche) {
        return tranche == LargeurTranche.HEURE ? "1 h" : tranche.minutes() + " min";
    }

    /// Minutes écoulées depuis 18 h le soir de la nuit à laquelle appartient `instant` (bascule à midi,
    /// [Nuit]). Un contact à 23:30 est à 330 min ; un contact à 02:00 le lendemain est à 480 min : l'ordre
    /// à cheval sur minuit est préservé.
    private static long minutesDepuis18h(LocalDateTime instant) {
        LocalDate soir = Nuit.de(instant);
        return Duration.between(soir.atTime(DEBUT_FENETRE), instant).toMinutes();
    }
}

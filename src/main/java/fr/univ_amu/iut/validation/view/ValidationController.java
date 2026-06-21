package fr.univ_amu.iut.validation.view;

import com.google.inject.Inject;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.ObservationStatut;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.viewmodel.FormatObservation;
import fr.univ_amu.iut.validation.viewmodel.ValidationViewModel;
import java.io.File;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/// Controller de l'écran **M-Vision-Tadarida** (`Validation.fxml`).
///
/// Pur câblage (patron CM4) : lie la table des observations, la sélection, le panneau de détail et
/// la progression au [ValidationViewModel]. La colonne « Statut » réutilise le libellé partagé
/// [FormatObservation#libelleStatut] (même source que le détail). La revue (valider / corriger)
/// délègue au VM ; les boutons s'activent selon la sélection (et le taxon choisi pour corriger).
/// Aucun accès base de données ni logique métier ici (règle ArchUnit `view_sans_jdbc`).
public class ValidationController implements EmplacementNavigation {

    private final ValidationViewModel viewModel;
    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;

    /// Contexte de navigation (passage + site), mémorisé pour reconstruire le fil d'Ariane du chrome.
    private ContextePassage contexte;

    // TODO (M-Vision-Tadarida) : déclarez les @FXML correspondant aux fx:id de Validation.fxml
    //   (table des observations, filtre, détail, AudioView, boutons valider/corriger/importer/
    //   exporter, mode, taxon...), câblez-les au ValidationViewModel dans « @FXML private void
    //   initialize() » et ajoutez les handlers @FXML. Patron de référence : feature sites.
    // --solution--
    @FXML
    private Label lblProgression;

    @FXML
    private Button btnImporter;

    @FXML
    private ComboBox<StatutObservation> choixFiltre;

    @FXML
    private TableView<ObservationStatut> tableObservations;

    @FXML
    private TableColumn<ObservationStatut, String> colEspece;

    @FXML
    private TableColumn<ObservationStatut, String> colStatut;

    @FXML
    private Label lblDetail;

    @FXML
    private AudioView audioView;

    @FXML
    private ComboBox<ModeRevue> choixMode;

    @FXML
    private Button btnValider;

    @FXML
    private ComboBox<Taxon> choixTaxon;

    @FXML
    private Button btnCorriger;

    @FXML
    private CheckBox chkInclureMode;

    @FXML
    private Button btnExporter;

    @FXML
    private Label lblMessage;

    // --end-solution--

    @Inject
    public ValidationController(ValidationViewModel viewModel, OuvrirSite ouvrirSite, OuvrirPassage ouvrirPassage) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
    }

    // --solution--
    @FXML
    private void initialize() {
        colEspece.setCellValueFactory(cellule ->
                new ReadOnlyStringWrapper(cellule.getValue().observation().taxonTadarida()));
        colStatut.setCellValueFactory(cellule -> new ReadOnlyStringWrapper(
                FormatObservation.libelleStatut(cellule.getValue().statut())));

        tableObservations.setItems(viewModel.observationsFiltrees());
        // Filtre de statut : la 1re entrée (null) = « Tous », les suivantes filtrent la table.
        choixFiltre.getItems().add(null);
        choixFiltre.getItems().addAll(StatutObservation.values());
        choixFiltre.setConverter(new StringConverter<>() {
            @Override
            public String toString(StatutObservation statut) {
                return statut == null ? "Tous les statuts" : FormatObservation.libelleStatut(statut);
            }

            @Override
            public StatutObservation fromString(String libelle) {
                return null; // ComboBox non éditable : conversion inverse inutile
            }
        });
        choixFiltre.valueProperty().bindBidirectional(viewModel.filtreStatutProperty());

        // La sélection de la table pilote le VM (un listener, pas un bind : selectedItemProperty est en
        // lecture seule, et le VM remet lui-même la sélection à null lors d'une réinitialisation).
        tableObservations
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, ancienne, nouvelle) ->
                        viewModel.selectionProperty().set(nouvelle));

        lblProgression.textProperty().bind(viewModel.progressionProperty());
        lblDetail.textProperty().bind(viewModel.detailProperty());

        // Vue audio (composant fourni, E7.S3) : la source suit l'observation sélectionnée ; le clip
        // est libéré quand la vue quitte la scène (pas de marquage écouté ici, contrairement à R10).
        audioView.audioFileProperty().bind(viewModel.cheminAudioCourantProperty());
        audioView.sceneProperty().addListener((obs, avant, scene) -> {
            if (scene == null) {
                audioView.dispose();
            }
        });

        choixMode.getItems().setAll(ModeRevue.values());
        choixMode.setConverter(new StringConverter<>() {
            @Override
            public String toString(ModeRevue mode) {
                return mode == null ? "" : libelleMode(mode);
            }

            @Override
            public ModeRevue fromString(String libelle) {
                return null; // ComboBox non éditable : conversion inverse inutile
            }
        });
        choixMode.valueProperty().bindBidirectional(viewModel.modeRevueProperty());

        choixTaxon.setItems(viewModel.taxons());
        choixTaxon.setConverter(new StringConverter<>() {
            @Override
            public String toString(Taxon taxon) {
                return taxon == null ? "" : libelleTaxon(taxon);
            }

            @Override
            public Taxon fromString(String libelle) {
                return null; // ComboBox non éditable : conversion inverse inutile
            }
        });

        // Valider exige une sélection ; corriger exige en plus un taxon choisi.
        btnValider.disableProperty().bind(viewModel.selectionPresenteProperty().not());
        btnCorriger
                .disableProperty()
                .bind(viewModel
                        .selectionPresenteProperty()
                        .not()
                        .or(choixTaxon.valueProperty().isNull()));

        // Import = point d'entrée : actif tant qu'aucun résultat n'existe (un seul jeu par passage,
        // passage_id unique) ; export = inverse, actif une fois les résultats chargés.
        btnImporter.disableProperty().bind(viewModel.resultatsDisponiblesProperty());
        chkInclureMode.selectedProperty().bindBidirectional(viewModel.inclureModeProperty());
        btnExporter
                .disableProperty()
                .bind(viewModel.resultatsDisponiblesProperty().not());

        lblMessage.textProperty().bind(viewModel.messageProperty());
        var messagePresent = viewModel.messageProperty().isNotEmpty();
        lblMessage.visibleProperty().bind(messagePresent);
        lblMessage.managedProperty().bind(messagePresent);
    }
    // --end-solution--

    /// Ouvre la validation du passage `passage`. Appelée par [NavigationValidation] après le chargement
    /// du FXML ; mémorise le contexte pour le fil d'Ariane.
    public void ouvrirSur(ContextePassage passage) {
        this.contexte = passage;
        viewModel.ouvrirSur(passage.idPassage());
    }

    /// Emplacement dans le fil d'Ariane : `Mes sites › Carré N › Détails du passage N° X › Validation
    /// Tadarida` (rendu par le chrome). Le segment passage rouvre M-Passage.
    @Override
    public List<Lieu> emplacement() {
        return EmplacementPassage.emplacementEnfant(contexte, ouvrirSite, ouvrirPassage, "Validation Tadarida");
    }

    // --solution--
    /// « Importer un CSV Tadarida » : ouvre le sélecteur de fichier natif (ouverture) puis délègue
    /// l'import au VM. Le dialog vit dans la vue (non testé en TestFX) ; l'import est testé côté VM.
    @FXML
    private void importer() {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Importer un CSV Tadarida (observations ou _Vu)");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File fichier = selecteur.showOpenDialog(btnImporter.getScene().getWindow());
        if (fichier != null) {
            viewModel.importer(fichier.toPath());
        }
    }

    @FXML
    private void valider() {
        viewModel.valider();
    }

    @FXML
    private void corriger() {
        viewModel.corriger(choixTaxon.getValue());
    }

    /// « Exporter _Vu » : ouvre le sélecteur de fichier natif (enregistrement) puis délègue au VM.
    /// Le dialog vit dans la vue (non testé en TestFX) ; l'écriture est testée côté ViewModel.
    @FXML
    private void exporter() {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Exporter le fichier _Vu (réinjectable)");
        selecteur.setInitialFileName("resultats_Vu.csv");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File fichier = selecteur.showSaveDialog(btnExporter.getScene().getWindow());
        if (fichier != null) {
            viewModel.exporter(fichier.toPath());
        }
    }

    private static String libelleTaxon(Taxon taxon) {
        String nom = taxon.nomVernaculaireFr();
        return nom == null || nom.isBlank() ? taxon.code() : taxon.code() + " (" + nom + ")";
    }

    private static String libelleMode(ModeRevue mode) {
        return switch (mode) {
            case ACTIVITE -> "Activité (une par une)";
            case INVENTAIRE -> "Inventaire (propage l'espèce)";
        };
    }
    // --end-solution--
}

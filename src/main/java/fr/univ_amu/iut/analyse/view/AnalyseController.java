package fr.univ_amu.iut.analyse.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.analyse.viewmodel.Regroupement;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.io.File;
import java.util.Objects;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/// Controller de l'écran **« Espèces & observations »** (`Analyse.fxml`). Pur câblage : lie les deux
/// tables (inventaire par espèce / par carré), le sélecteur de regroupement et le filtre de statut à
/// l'[AnalyseViewModel]. La table affichée suit le regroupement ; le chargement initial est déclenché ici
/// (écran sans paramètre). Aucun accès base de données (règle ArchUnit `view_sans_jdbc`).
///
/// Implémente [RafraichirAuRetour] : l'écran reste vivant dans l'historique du [Navigateur] ; quand on y
/// revient après avoir modifié des observations ailleurs (validation d'un passage…), l'inventaire est
/// rechargé pour ne pas afficher des compteurs périmés.
public class AnalyseController implements RafraichirAuRetour {

    private final AnalyseViewModel viewModel;

    @FXML
    private Label lblResume;

    @FXML
    private Label lblMessage;

    @FXML
    private ComboBox<Regroupement> choixRegroupement;

    @FXML
    private ComboBox<StatutObservation> choixStatut;

    @FXML
    private TextField champFiltre;

    @FXML
    private Button boutonExporter;

    @FXML
    private Label lblExport;

    @FXML
    private TableView<EspeceAgregee> tableEspeces;

    @FXML
    private TableColumn<EspeceAgregee, String> colEspece;

    @FXML
    private TableColumn<EspeceAgregee, String> colGroupe;

    @FXML
    private TableColumn<EspeceAgregee, String> colDetections;

    @FXML
    private TableColumn<EspeceAgregee, String> colPassages;

    @FXML
    private TableColumn<EspeceAgregee, String> colCarres;

    @FXML
    private TableColumn<EspeceAgregee, String> colPoints;

    @FXML
    private TableColumn<EspeceAgregee, String> colPeriode;

    @FXML
    private TableView<CarreEspeces> tableCarres;

    @FXML
    private TableColumn<CarreEspeces, String> colCarre;

    @FXML
    private TableColumn<CarreEspeces, String> colSite;

    @FXML
    private TableColumn<CarreEspeces, String> colRichesse;

    @FXML
    private TableColumn<CarreEspeces, String> colDetectionsCarre;

    @FXML
    private TableColumn<CarreEspeces, String> colPeriodeCarre;

    @Inject
    public AnalyseController(AnalyseViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    @FXML
    private void initialize() {
        configurerColonnes();
        tableEspeces.setItems(viewModel.especes());
        tableCarres.setItems(viewModel.carres());

        // Sélecteur de regroupement (pivot espèce ↔ lieu).
        choixRegroupement.getItems().setAll(Regroupement.values());
        choixRegroupement.setConverter(convertisseur(r -> r == null ? "" : r.libelle()));
        choixRegroupement.valueProperty().bindBidirectional(viewModel.regroupementProperty());

        // Filtre de statut de revue : 1re entrée (null) = tous, puis les trois statuts.
        choixStatut.getItems().add(null);
        choixStatut.getItems().addAll(StatutObservation.values());
        choixStatut.setConverter(convertisseur(AnalyseController::libelleStatut));
        choixStatut.valueProperty().bindBidirectional(viewModel.filtreStatutProperty());

        // Filtre texte (en mémoire) et message d'export.
        champFiltre.textProperty().bindBidirectional(viewModel.filtreTexteProperty());
        var exportPresent = viewModel.messageProperty().isNotEmpty();
        lblExport.textProperty().bind(viewModel.messageProperty());
        lblExport.visibleProperty().bind(exportPresent);
        lblExport.managedProperty().bind(exportPresent);

        // La table visible suit le regroupement.
        var parEspece = viewModel.regroupementProperty().isEqualTo(Regroupement.PAR_ESPECE);
        lierVisibilite(tableEspeces, parEspece);
        lierVisibilite(tableCarres, parEspece.not());

        lblResume.textProperty().bind(viewModel.resumeProperty());

        // Message d'état vide : ni espèce ni carré (aucune observation exploitable).
        var vide = Bindings.createBooleanBinding(
                () -> viewModel.especes().isEmpty() && viewModel.carres().isEmpty(),
                viewModel.especes(),
                viewModel.carres());
        lblMessage.setText("Aucune observation à analyser pour le moment. Importez et validez des nuits"
                + " (résultats Tadarida) pour voir apparaître vos espèces ici.");
        lblMessage.visibleProperty().bind(vide);
        lblMessage.managedProperty().bind(vide);

        viewModel.rafraichir();
    }

    /// Rechargé par le [fr.univ_amu.iut.commun.view.Navigateur] au **retour** sur l'écran : des
    /// observations ont pu être validées/corrigées entre-temps, l'inventaire est donc ré-interrogé.
    @Override
    public void rafraichirAuRetour() {
        viewModel.rafraichir();
    }

    /// « 📤 Exporter… » : sélecteur de fichier natif (enregistrement) puis délègue au ViewModel l'écriture
    /// CSV de l'inventaire **affiché** (liste filtrée courante). Le dialog vit dans la vue (non testé en
    /// TestFX) ; l'écriture est testée côté ViewModel/service.
    @FXML
    private void exporter() {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Exporter l'inventaire des espèces en CSV");
        selecteur.setInitialFileName("inventaire-especes.csv");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File fichier = selecteur.showSaveDialog(boutonExporter.getScene().getWindow());
        if (fichier != null) {
            viewModel.exporter(fichier.toPath());
        }
    }

    private void configurerColonnes() {
        colEspece.setCellValueFactory(c -> texte(libelleEspece(c.getValue())));
        colGroupe.setCellValueFactory(c -> texte(ouTiret(c.getValue().groupe())));
        colDetections.setCellValueFactory(c -> texte(c.getValue().nbObservations()));
        colPassages.setCellValueFactory(c -> texte(c.getValue().nbPassages()));
        colCarres.setCellValueFactory(c -> texte(c.getValue().nbCarres()));
        colPoints.setCellValueFactory(c -> texte(c.getValue().nbPoints()));
        colPeriode.setCellValueFactory(
                c -> texte(periode(c.getValue().anneeMin(), c.getValue().anneeMax())));

        colCarre.setCellValueFactory(c -> texte(c.getValue().numeroCarre()));
        colSite.setCellValueFactory(c -> texte(ouTiret(c.getValue().nomSite())));
        colRichesse.setCellValueFactory(c -> texte(c.getValue().richesse()));
        colDetectionsCarre.setCellValueFactory(c -> texte(c.getValue().nbObservations()));
        colPeriodeCarre.setCellValueFactory(
                c -> texte(periode(c.getValue().anneeMin(), c.getValue().anneeMax())));
    }

    /// Libellé d'une espèce : nom vernaculaire (sinon latin, sinon code) suivi du code entre parenthèses.
    private static String libelleEspece(EspeceAgregee espece) {
        String nom = premierNonVide(espece.nomVernaculaireFr(), espece.nomLatin(), espece.code());
        return nom + " (" + espece.code() + ")";
    }

    /// Période d'observation : une seule année (`2026`) ou un intervalle (`2024–2026`).
    private static String periode(int anneeMin, int anneeMax) {
        return anneeMin == anneeMax ? Integer.toString(anneeMin) : anneeMin + "–" + anneeMax;
    }

    private static String libelleStatut(StatutObservation statut) {
        if (statut == null) {
            return "Tous les statuts";
        }
        return switch (statut) {
            case NON_TOUCHEE -> "Non touchée";
            case VALIDEE -> "Validée";
            case CORRIGEE -> "Corrigée";
        };
    }

    private static String premierNonVide(String... candidats) {
        for (String candidat : candidats) {
            if (candidat != null && !candidat.isBlank()) {
                return candidat;
            }
        }
        return "";
    }

    private static String ouTiret(String valeur) {
        return valeur == null || valeur.isBlank() ? "—" : valeur;
    }

    private static ObservableValue<String> texte(Object valeur) {
        return new ReadOnlyStringWrapper(String.valueOf(valeur));
    }

    private static void lierVisibilite(TableView<?> table, javafx.beans.value.ObservableValue<Boolean> visible) {
        table.visibleProperty().bind(visible);
        table.managedProperty().bind(visible);
    }

    private static <T> StringConverter<T> convertisseur(Function<T, String> versTexte) {
        return new StringConverter<>() {
            @Override
            public String toString(T valeur) {
                return versTexte.apply(valeur);
            }

            @Override
            public T fromString(String libelle) {
                return null;
            }
        };
    }
}

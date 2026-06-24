package fr.univ_amu.iut.multisite.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import java.io.File;
import java.util.Objects;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/// Controller de l'écran **M-Multisite** (`Multisite.fxml`).
///
/// Pur câblage (patron CM4) : lie le tableau des passages agrégés, les filtres (carré, statut,
/// verdict, année), le tri et l'export au [MultisiteViewModel]. Le **double-clic** sur une ligne
/// ouvre l'écran M-Passage via le contrat socle [OuvrirPassage] (inversion de dépendance : la
/// feature ne dépend pas de `passage.view`). Le chargement initial est déclenché ici (écran sans
/// paramètre). Aucun accès base de données ni logique métier (règle ArchUnit `view_sans_jdbc`).
public class MultisiteController {

    private final MultisiteViewModel viewModel;
    private final OuvrirPassage ouvrirPassage;
    private final NavigationMultisite navigation;

    @FXML
    private Label lblResume;

    @FXML
    private TextField champCarre;

    @FXML
    private ComboBox<StatutWorkflow> choixStatut;

    @FXML
    private ComboBox<Verdict> choixVerdict;

    @FXML
    private TextField champAnnee;

    @FXML
    private ComboBox<TriMultisite> choixTri;

    @FXML
    private Button boutonExporter;

    @FXML
    private Button boutonGererVues;

    @FXML
    private TableView<LignePassage> tableLignes;

    @FXML
    private TableColumn<LignePassage, String> colCarre;

    @FXML
    private TableColumn<LignePassage, String> colPoint;

    @FXML
    private TableColumn<LignePassage, String> colAnnee;

    @FXML
    private TableColumn<LignePassage, String> colNumero;

    @FXML
    private TableColumn<LignePassage, String> colDate;

    @FXML
    private TableColumn<LignePassage, String> colStatut;

    @FXML
    private TableColumn<LignePassage, String> colVerdict;

    @FXML
    private Label lblMessage;

    @Inject
    public MultisiteController(
            MultisiteViewModel viewModel, OuvrirPassage ouvrirPassage, NavigationMultisite navigation) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    @FXML
    private void initialize() {
        configurerColonnes();
        tableLignes.setItems(viewModel.lignes());
        // Double-clic sur une ligne → ouvre M-Passage (contrat socle, aucune dépendance vers passage.view).
        tableLignes.setRowFactory(tableau -> {
            TableRow<LignePassage> ligne = new TableRow<>();
            ligne.setOnMouseClicked(evenement -> {
                if (evenement.getButton() == MouseButton.PRIMARY
                        && evenement.getClickCount() == 2
                        && !ligne.isEmpty()) {
                    ouvrirPassageDeLaLigne(ligne.getItem());
                }
            });
            return ligne;
        });

        configurerFiltres();
        choixTri.getItems().setAll(TriMultisite.values());
        choixTri.setConverter(convertisseur(MultisiteController::libelleTri));
        choixTri.valueProperty().bindBidirectional(viewModel.triProperty());

        lblResume.textProperty().bind(viewModel.resumeProperty());
        boutonExporter.disableProperty().bind(viewModel.nonVideProperty().not());

        lblMessage.textProperty().bind(viewModel.messageProperty());
        var messagePresent = viewModel.messageProperty().isNotEmpty();
        lblMessage.visibleProperty().bind(messagePresent);
        lblMessage.managedProperty().bind(messagePresent);

        viewModel.rafraichir();
    }

    private void configurerColonnes() {
        colCarre.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().numeroCarre()));
        colPoint.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().codePoint()));
        colAnnee.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().annee())));
        colNumero.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().numeroPassage())));
        colDate.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().dateEnregistrement()));
        colStatut.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(c.getValue().statut().libelle()));
        colVerdict.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().verdict() == null ? "" : c.getValue().verdict().libelle()));
    }

    private void configurerFiltres() {
        // Statut / verdict : la 1re entrée (null) lève le filtre ; les suivantes restreignent.
        choixStatut.getItems().add(null);
        choixStatut.getItems().addAll(StatutWorkflow.values());
        choixStatut.setConverter(convertisseur(s -> s == null ? "Tous les statuts" : s.libelle()));
        choixStatut.valueProperty().bindBidirectional(viewModel.filtreStatutProperty());

        choixVerdict.getItems().add(null);
        choixVerdict.getItems().addAll(Verdict.values());
        choixVerdict.setConverter(convertisseur(v -> v == null ? "Tous les verdicts" : v.libelle()));
        choixVerdict.valueProperty().bindBidirectional(viewModel.filtreVerdictProperty());

        // Champs texte : appliqués à la validation (Entrée) pour ne pas ré-interroger à chaque frappe ;
        // les champs reflètent la propriété (vidés lors de la réinitialisation).
        champCarre.setOnAction(
                evenement -> viewModel.filtreNumeroCarreProperty().set(champCarre.getText()));
        viewModel
                .filtreNumeroCarreProperty()
                .addListener((obs, ancien, nouveau) -> champCarre.setText(nouveau == null ? "" : nouveau));

        champAnnee.setOnAction(evenement -> appliquerAnnee());
        viewModel
                .filtreAnneeProperty()
                .addListener(
                        (obs, ancien, nouveau) -> champAnnee.setText(nouveau == null ? "" : String.valueOf(nouveau)));
    }

    /// Parse l'année saisie : un champ vide lève le filtre, une valeur non numérique est ignorée
    /// (le champ est restauré à la valeur courante du filtre).
    private void appliquerAnnee() {
        String saisie = champAnnee.getText() == null ? "" : champAnnee.getText().trim();
        if (saisie.isEmpty()) {
            viewModel.filtreAnneeProperty().set(null);
            return;
        }
        try {
            viewModel.filtreAnneeProperty().set(Integer.valueOf(saisie));
        } catch (NumberFormatException invalide) {
            Integer courant = viewModel.filtreAnneeProperty().get();
            champAnnee.setText(courant == null ? "" : String.valueOf(courant));
        }
    }

    private void ouvrirPassageDeLaLigne(LignePassage ligne) {
        // Le nom convivial du site n'est pas porté par la vue agrégée : carré + point suffisent au
        // fil d'Ariane de M-Passage (nomSite n'y est pas utilisé).
        ouvrirPassage.ouvrir(ligne.idPassage(), new ContexteSite(ligne.numeroCarre(), ligne.codePoint(), null));
    }

    /// « Vues enregistrées… » : ouvre la modale de gestion, branchée sur ce même ViewModel
    /// (appliquer une vue met donc à jour les filtres et le tableau de cet écran).
    @FXML
    private void gererVues() {
        navigation.ouvrirModaleVues(boutonGererVues.getScene().getWindow(), viewModel);
    }

    @FXML
    private void reinitialiser() {
        // Vide d'abord les champs texte : une saisie non validée (sans Entrée) n'a pas mis à jour le
        // VM, donc reinitialiserFiltres seul laisserait ce texte affiché (P3 revue codex).
        champCarre.clear();
        champAnnee.clear();
        viewModel.reinitialiserFiltres();
    }

    /// « Exporter » : ouvre le sélecteur de fichier natif (enregistrement) puis délègue au VM.
    /// Le dialog vit dans la vue (non testé en TestFX) ; l'écriture est testée côté ViewModel.
    @FXML
    private void exporter() {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Exporter la vue multi-sites en CSV");
        selecteur.setInitialFileName("vue-multisite.csv");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File fichier = selecteur.showSaveDialog(boutonExporter.getScene().getWindow());
        if (fichier != null) {
            viewModel.exporter(fichier.toPath());
        }
    }

    private static <T> StringConverter<T> convertisseur(java.util.function.Function<T, String> versTexte) {
        return new StringConverter<>() {
            @Override
            public String toString(T valeur) {
                return versTexte.apply(valeur);
            }

            @Override
            public T fromString(String libelle) {
                return null; // ComboBox non éditable : conversion inverse inutile
            }
        };
    }

    private static String libelleTri(TriMultisite tri) {
        if (tri == null) {
            return "";
        }
        return switch (tri) {
            case PAR_SITE -> "Par site";
            case PAR_ANNEE -> "Par année";
            case PAR_STATUT -> "Par statut";
            case PAR_VERDICT -> "Par verdict";
        };
    }
}

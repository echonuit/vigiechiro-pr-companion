package fr.univ_amu.iut.validation.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.validation.model.ObservationStatut;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.viewmodel.ValidationViewModel;
import java.util.Objects;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;

/// Controller de l'écran **M-Vision-Tadarida** (`Validation.fxml`).
///
/// Pur câblage (patron CM4) : lie la table des observations, la sélection, le panneau de détail et
/// la progression au [ValidationViewModel]. La colonne « Statut » réutilise le libellé partagé
/// [ValidationViewModel#libelleStatut] (même source que le détail). La revue (valider / corriger)
/// délègue au VM ; les boutons s'activent selon la sélection (et le taxon choisi pour corriger).
/// Aucun accès base de données ni logique métier ici (règle ArchUnit `view_sans_jdbc`).
public class ValidationController {

  private final ValidationViewModel viewModel;

  @FXML private Label lblProgression;
  @FXML private TableView<ObservationStatut> tableObservations;
  @FXML private TableColumn<ObservationStatut, String> colEspece;
  @FXML private TableColumn<ObservationStatut, String> colStatut;
  @FXML private Label lblDetail;
  @FXML private Button btnValider;
  @FXML private ComboBox<Taxon> choixTaxon;
  @FXML private Button btnCorriger;
  @FXML private Label lblMessage;

  @Inject
  public ValidationController(ValidationViewModel viewModel) {
    this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
  }

  @FXML
  private void initialize() {
    colEspece.setCellValueFactory(
        cellule -> new ReadOnlyStringWrapper(cellule.getValue().observation().taxonTadarida()));
    colStatut.setCellValueFactory(
        cellule ->
            new ReadOnlyStringWrapper(
                ValidationViewModel.libelleStatut(cellule.getValue().statut())));

    tableObservations.setItems(viewModel.observations());
    // La sélection de la table pilote le VM (un listener, pas un bind : selectedItemProperty est en
    // lecture seule, et le VM remet lui-même la sélection à null lors d'une réinitialisation).
    tableObservations
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, ancienne, nouvelle) -> viewModel.selectionProperty().set(nouvelle));

    lblProgression.textProperty().bind(viewModel.progressionProperty());
    lblDetail.textProperty().bind(viewModel.detailProperty());

    choixTaxon.setItems(viewModel.taxons());
    choixTaxon.setConverter(
        new StringConverter<>() {
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
        .bind(viewModel.selectionPresenteProperty().not().or(choixTaxon.valueProperty().isNull()));

    lblMessage.textProperty().bind(viewModel.messageProperty());
    var messagePresent = viewModel.messageProperty().isNotEmpty();
    lblMessage.visibleProperty().bind(messagePresent);
    lblMessage.managedProperty().bind(messagePresent);
  }

  /// Ouvre la validation du passage `idPassage`. Appelée par [NavigationValidation] après le
  /// chargement du FXML.
  public void ouvrirSur(Long idPassage) {
    viewModel.ouvrirSur(idPassage);
  }

  @FXML
  private void valider() {
    viewModel.valider();
  }

  @FXML
  private void corriger() {
    viewModel.corriger(choixTaxon.getValue());
  }

  private static String libelleTaxon(Taxon taxon) {
    String nom = taxon.nomVernaculaireFr();
    return nom == null || nom.isBlank() ? taxon.code() : taxon.code() + " (" + nom + ")";
  }
}

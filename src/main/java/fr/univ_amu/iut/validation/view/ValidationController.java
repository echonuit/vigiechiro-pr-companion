package fr.univ_amu.iut.validation.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.validation.model.ObservationStatut;
import fr.univ_amu.iut.validation.viewmodel.ValidationViewModel;
import java.util.Objects;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/// Controller de l'écran **M-Vision-Tadarida** (`Validation.fxml`).
///
/// Pur câblage (patron CM4) : lie la table des observations, la sélection, le panneau de détail et
/// la progression au [ValidationViewModel]. La colonne « Statut » réutilise le libellé partagé
/// [ValidationViewModel#libelleStatut] (même source que le détail). Aucun accès base de données ni
/// logique métier ici (règle ArchUnit `view_sans_jdbc`). La revue (valider / corriger) arrivera en
/// PR-V4.
public class ValidationController {

  private final ValidationViewModel viewModel;

  @FXML private Label lblProgression;
  @FXML private TableView<ObservationStatut> tableObservations;
  @FXML private TableColumn<ObservationStatut, String> colEspece;
  @FXML private TableColumn<ObservationStatut, String> colStatut;
  @FXML private Label lblDetail;
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
}

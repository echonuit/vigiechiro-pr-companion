package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.passage.viewmodel.RattachementViewModel;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/// Controller de la **modale « Modifier le rattachement »** (`RattachementModale.fxml`, E2.S8).
///
/// Lie les deux `Spinner` (année, n° de passage) en bidirectionnel au [RattachementViewModel], et
/// reflète son récapitulatif réactif et son message d'erreur. La modale se ferme elle-même via sa
/// fenêtre ; après une modification réussie, elle exécute le `Runnable` fourni par l'appelant
/// (typiquement le rafraîchissement de M-Passage).
public class RattachementModaleController {

  private final RattachementViewModel viewModel;
  private Runnable apresSucces = () -> {};
  private ObjectProperty<Integer> anneeObjet;
  private ObjectProperty<Integer> numeroObjet;

  @FXML private VBox racine;
  @FXML private Spinner<Integer> spinnerAnnee;
  @FXML private Spinner<Integer> spinnerNumero;
  @FXML private Label labelRecap;
  @FXML private Label messageErreur;

  @Inject
  public RattachementModaleController(RattachementViewModel viewModel) {
    this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
  }

  @FXML
  private void initialize() {
    // Bornes alignées sur la validation du domaine (année à 4 chiffres ; n° de passage >= 1, sans
    // borne haute) pour ne jamais écrêter une valeur valide déjà en base : le ViewModel reste
    // l'autorité (cf. valider()).
    spinnerAnnee.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(1000, 9999, 2026));
    spinnerNumero.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, 1));

    // Réfs de champ sur les wrappers asObject() : sinon ils seraient éligibles au GC et la liaison
    // bidirectionnelle avec les Spinner cesserait silencieusement de fonctionner.
    anneeObjet = viewModel.anneeProperty().asObject();
    numeroObjet = viewModel.numeroPassageProperty().asObject();
    spinnerAnnee.getValueFactory().valueProperty().bindBidirectional(anneeObjet);
    spinnerNumero.getValueFactory().valueProperty().bindBidirectional(numeroObjet);

    labelRecap.textProperty().bind(viewModel.recapProperty());
    labelRecap.setWrapText(true);
    messageErreur.textProperty().bind(viewModel.messageErreurProperty());
    var erreurPresente = viewModel.messageErreurProperty().isNotEmpty();
    messageErreur.visibleProperty().bind(erreurPresente);
    messageErreur.managedProperty().bind(erreurPresente);
  }

  /// Prépare la modale sur le passage `idPassage` (carré/point fournis par M-Passage) et mémorise
  /// l'action de succès (rafraîchir l'écran appelant).
  public void demarrer(Long idPassage, String carre, String codePoint, Runnable apresSucces) {
    this.apresSucces = Objects.requireNonNull(apresSucces, "apresSucces");
    viewModel.ouvrirSur(idPassage, carre, codePoint);
  }

  @FXML
  private void valider() {
    if (viewModel.valider()) {
      apresSucces.run();
      fermer();
    }
  }

  @FXML
  private void annuler() {
    fermer();
  }

  private void fermer() {
    ((Stage) racine.getScene().getWindow()).close();
  }
}

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

    // TODO (M-Passage, modale rattachement) : déclarez les @FXML (spinners année / n° de passage,
    //   récap, message) correspondant aux fx:id de RattachementModale.fxml et câblez-les au
    //   RattachementViewModel dans « @FXML private void initialize() » ; ajoutez les handlers @FXML.
    // --solution--
    private ObjectProperty<Integer> anneeObjet;
    private ObjectProperty<Integer> numeroObjet;

    @FXML
    private VBox racine;

    @FXML
    private Spinner<Integer> spinnerAnnee;

    @FXML
    private Spinner<Integer> spinnerNumero;

    @FXML
    private Label labelRecap;

    @FXML
    private Label messageErreur;

    // --end-solution--

    @Inject
    public RattachementModaleController(RattachementViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    // --solution--
    @FXML
    private void initialize() {
        // Bornes volontairement ouvertes (tout l'entier) : un IntegerSpinnerValueFactory **écrête** la
        // saisie au commit, ce qui masquerait au ViewModel une valeur hors domaine (ex. 0, ou année à 3
        // chiffres) en la normalisant silencieusement. On laisse donc passer toute valeur saisie ;
        // c'est
        // [RattachementViewModel#valider] qui reste l'unique autorité (n° >= 1, année à 4 chiffres).
        spinnerAnnee.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(Integer.MIN_VALUE, Integer.MAX_VALUE, 2026));
        spinnerNumero.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(Integer.MIN_VALUE, Integer.MAX_VALUE, 1));

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
    // --end-solution--

    /// Prépare la modale sur le passage `idPassage` (carré/point fournis par M-Passage) et mémorise
    /// l'action de succès (rafraîchir l'écran appelant).
    public void demarrer(Long idPassage, String carre, String codePoint, Runnable apresSucces) {
        this.apresSucces = Objects.requireNonNull(apresSucces, "apresSucces");
        viewModel.ouvrirSur(idPassage, carre, codePoint);
    }

    // --solution--
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
    // --end-solution--
}

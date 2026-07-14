package fr.univ_amu.iut.qualification.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.qualification.model.GenerateurSelection;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/// Controller de la modale **« Personnaliser la sélection d'écoute »** (R12, #1431) : méthode de
/// constitution et taille, puis régénération.
///
/// Elle remplace un `Dialog<ButtonType>` bâti à la main dans [QualificationController]. Celui-ci se
/// terminait par un `showAndWait` : le geste était **injouable dans un test** - alors qu'il **efface la
/// progression d'écoute** de l'observateur. Et sa capture de documentation était une **réplique**
/// reconstruite à la main (`CaptureDialogues`), qui pouvait dériver du vrai écran sans que rien ne le
/// signale : c'est exactement le trio de défauts que la modale de site (#1454) a déjà corrigé.
///
/// Les contrôles portent un **brouillon** : tant que l'utilisateur n'a pas cliqué « Régénérer », le
/// ViewModel n'est pas touché. Renoncer ne coûte donc rien.
public class ModaleSelectionController {

    private final SelectionEcouteViewModel viewModel;

    private final ToggleGroup methode = new ToggleGroup();

    @FXML
    private VBox racine;

    @FXML
    private RadioButton choixReparti;

    @FXML
    private RadioButton choixAleatoire;

    @FXML
    private Label lblTaille;

    @FXML
    private Slider curseurTaille;

    @FXML
    private Button boutonRegenerer;

    @Inject
    public ModaleSelectionController(SelectionEcouteViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    @FXML
    private void initialize() {
        choixReparti.setToggleGroup(methode);
        choixAleatoire.setToggleGroup(methode);
        curseurTaille.setMin(GenerateurSelection.TAILLE_MIN);
        curseurTaille.setMax(GenerateurSelection.TAILLE_MAX);
        lblTaille.textProperty().bind(curseurTaille.valueProperty().asString("Taille : %.0f séquences"));
    }

    /// Ouvre la modale sur l'état **courant** de la sélection (méthode et taille pré-sélectionnées).
    public void demarrer() {
        boolean aleatoire = viewModel.methodeProperty().get() == MethodeSelection.ALEATOIRE;
        (aleatoire ? choixAleatoire : choixReparti).setSelected(true);
        curseurTaille.setValue(viewModel.tailleProperty().get());
    }

    /// Le ViewModel exposé aux tests.
    SelectionEcouteViewModel viewModel() {
        return viewModel;
    }

    /// « ↺ Régénérer » : applique le brouillon puis reconstruit la sélection. **Efface la progression
    /// d'écoute** - le verdict, lui, est conservé.
    @FXML
    private void regenerer() {
        viewModel
                .methodeProperty()
                .set(
                        choixAleatoire.isSelected()
                                ? MethodeSelection.ALEATOIRE
                                : MethodeSelection.REPARTITION_TEMPORELLE);
        viewModel.tailleProperty().set((int) Math.round(curseurTaille.getValue()));
        viewModel.regenerer();
        fermer();
    }

    /// « Annuler » : le brouillon est jeté, le ViewModel n'a jamais été touché.
    @FXML
    private void annuler() {
        fermer();
    }

    private void fermer() {
        ((Stage) racine.getScene().getWindow()).close();
    }
}

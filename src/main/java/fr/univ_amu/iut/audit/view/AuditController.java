package fr.univ_amu.iut.audit.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.audit.viewmodel.AuditViewModel;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import java.util.Objects;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;

/// Écran **Audit de cohérence** (feature `audit`) : affiche le résultat de l'audit disque / base global
/// (fichiers manquants ou orphelins, préfixes non conformes, unités déposées divergentes) sous forme de
/// table de constats, avec un résumé et un bouton de relance. Pur câblage vers l'[AuditViewModel].
public class AuditController {

    @FXML
    private StackPane hoteOccupation;

    @FXML
    private Label lblResume;

    @FXML
    private TableView<ConstatAudit> tableConstats;

    @FXML
    private TableColumn<ConstatAudit, String> colSeverite;

    @FXML
    private TableColumn<ConstatAudit, String> colCategorie;

    @FXML
    private TableColumn<ConstatAudit, String> colPassage;

    @FXML
    private TableColumn<ConstatAudit, String> colCible;

    @FXML
    private TableColumn<ConstatAudit, String> colDetail;

    @FXML
    private Button boutonVerifierEnLigne;

    private final AuditViewModel viewModel;
    private final ExecuteurTache executeur;
    private IndicateurOccupation occupation;

    @Inject
    public AuditController(AuditViewModel viewModel, ExecuteurTache executeur) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
    }

    @FXML
    private void initialize() {
        occupation = new IndicateurOccupation(hoteOccupation, executeur);
        colSeverite.setCellValueFactory(c -> texte(c.getValue().severite().name()));
        colCategorie.setCellValueFactory(c -> texte(c.getValue().categorie().name()));
        colPassage.setCellValueFactory(c -> texte(
                c.getValue().idPassage() == null
                        ? "-"
                        : String.valueOf(c.getValue().idPassage())));
        colCible.setCellValueFactory(c -> texte(c.getValue().cible()));
        colDetail.setCellValueFactory(c -> texte(c.getValue().detail()));
        tableConstats.setItems(viewModel.constats());
        tableConstats.setPlaceholder(new Label("Aucun écart de cohérence détecté."));
        lblResume.textProperty().bind(viewModel.resumeProperty());
        // Le voile bloque déjà l'écran pendant la vérification ; le grisage du bouton rend l'état
        // « en cours » lisible sans setDisable posé à la main (#1254).
        boutonVerifierEnLigne.disableProperty().bind(occupation.enCoursProperty());
        viewModel.rafraichir();
    }

    @FXML
    private void rafraichir() {
        viewModel.rafraichir();
    }

    /// Vérification **en ligne** (confrontation au serveur) : exécutée **hors du fil JavaFX** (réseau)
    /// sous l'overlay d'occupation (#1254), puis le résultat (ou l'erreur, filet #795) est appliqué sur
    /// le fil JavaFX.
    @FXML
    private void verifierEnLigne() {
        occupation.occuper(
                "Vérification en ligne…",
                viewModel::calculerAvecEnLigne,
                viewModel::appliquer,
                viewModel::signalerErreur);
    }

    private static ReadOnlyStringWrapper texte(String valeur) {
        return new ReadOnlyStringWrapper(valeur);
    }
}

package fr.univ_amu.iut.audit.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.audit.viewmodel.AuditViewModel;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/// Écran **Audit de cohérence** (feature `audit`) : affiche le résultat de l'audit disque / base global
/// (fichiers manquants ou orphelins, préfixes non conformes, unités déposées divergentes) sous forme de
/// table de constats, avec un résumé et un bouton de relance. Pur câblage vers l'[AuditViewModel].
public class AuditController {

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

    @Inject
    public AuditController(AuditViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    @FXML
    private void initialize() {
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
        viewModel.rafraichir();
    }

    @FXML
    private void rafraichir() {
        viewModel.rafraichir();
    }

    /// Vérification **en ligne** (confrontation au serveur) : exécutée hors fil JavaFX (réseau) via un
    /// [Task], puis le résultat est appliqué sur le fil. Le bouton est neutralisé le temps de l'appel.
    @FXML
    private void verifierEnLigne() {
        Task<List<ConstatAudit>> tache = new Task<>() {
            @Override
            protected List<ConstatAudit> call() {
                return viewModel.calculerAvecEnLigne();
            }
        };
        boutonVerifierEnLigne.setDisable(true);
        tache.setOnSucceeded(evenement -> {
            viewModel.appliquer(tache.getValue());
            boutonVerifierEnLigne.setDisable(false);
        });
        tache.setOnFailed(evenement -> boutonVerifierEnLigne.setDisable(false));
        Thread fil = new Thread(tache, "audit-en-ligne");
        fil.setDaemon(true);
        fil.start();
    }

    private static ReadOnlyStringWrapper texte(String valeur) {
        return new ReadOnlyStringWrapper(valeur);
    }
}

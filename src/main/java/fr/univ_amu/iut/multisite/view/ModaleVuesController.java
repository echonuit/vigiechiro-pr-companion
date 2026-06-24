package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.multisite.model.SavedView;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/// Controller de la **modale des vues sauvegardées** (`ModaleVues.fxml`) de M-Multisite.
///
/// Pur câblage : opère sur le **même** [MultisiteViewModel] que l'écran principal (transmis par
/// [#demarrer(MultisiteViewModel)]), de sorte qu'appliquer une vue met à jour les filtres et le
/// tableau de l'écran sous-jacent. La liste, l'enregistrement, l'application, la mise à jour et la
/// suppression délèguent au ViewModel. Aucun accès base de données ni logique métier.
public class ModaleVuesController {

    // TODO (M-Multisite, modale « Vues enregistrées ») : déclarez les @FXML (liste des vues, champ
    //   nom, boutons), câblez-les dans « @FXML private void initialize() », reliez la modale au
    //   MultisiteViewModel partagé dans demarrer(...) et ajoutez les handlers @FXML.
    @FXML
    private VBox racine;

    @FXML
    private ListView<SavedView> listeVues;

    @FXML
    private TextField champNom;

    @FXML
    private Button boutonAppliquer;

    @FXML
    private Button boutonMettreAJour;

    @FXML
    private Button boutonSupprimer;

    @FXML
    private Label lblMessage;

    private MultisiteViewModel viewModel;

    @FXML
    private void initialize() {
        listeVues.setCellFactory(liste -> new ListCell<>() {
            @Override
            protected void updateItem(SavedView vue, boolean vide) {
                super.updateItem(vue, vide);
                setText(vide || vue == null ? null : vue.nom());
            }
        });
        // Les actions sur une vue exigent une sélection.
        var pasDeSelection =
                listeVues.getSelectionModel().selectedItemProperty().isNull();
        boutonAppliquer.disableProperty().bind(pasDeSelection);
        boutonMettreAJour.disableProperty().bind(pasDeSelection);
        boutonSupprimer.disableProperty().bind(pasDeSelection);
        // Sélectionner une vue pré-remplit son nom (pour la renommer via « Mettre à jour »).
        listeVues.getSelectionModel().selectedItemProperty().addListener((obs, ancienne, nouvelle) -> {
            if (nouvelle != null) {
                champNom.setText(nouvelle.nom());
            }
        });
    }

    /// Branche la modale sur le ViewModel de l'écran M-Multisite (état de filtres partagé) et charge
    /// la liste des vues. Appelée par [NavigationMultisite] après le chargement du FXML.
    public void demarrer(MultisiteViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        // TODO (M-Multisite, modale) : reliez la liste des vues + le message au viewModel partagé et
        //   chargez les vues (viewModel.chargerVues()).
        listeVues.setItems(viewModel.vues());
        lblMessage.textProperty().bind(viewModel.messageProperty());
        var messagePresent = viewModel.messageProperty().isNotEmpty();
        lblMessage.visibleProperty().bind(messagePresent);
        lblMessage.managedProperty().bind(messagePresent);
        viewModel.chargerVues();
    }

    @FXML
    private void enregistrer() {
        if (viewModel.enregistrerVue(champNom.getText())) {
            champNom.clear();
        }
    }

    @FXML
    private void appliquer() {
        if (viewModel.appliquerVue(listeVues.getSelectionModel().getSelectedItem())) {
            fermer();
        }
    }

    @FXML
    private void mettreAJour() {
        viewModel.mettreAJourVue(listeVues.getSelectionModel().getSelectedItem(), champNom.getText());
    }

    @FXML
    private void supprimer() {
        viewModel.supprimerVue(listeVues.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void fermer() {
        ((Stage) racine.getScene().getWindow()).close();
    }
}

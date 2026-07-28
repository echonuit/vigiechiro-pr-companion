package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.BandeauRetour;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.viewmodel.GestionCampagnesViewModel;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/// Contrôleur de la modale **« Gérer les campagnes »** (#2630).
///
/// Il fait exister, dans l'application, ce que seule la ligne de commande savait faire : créer,
/// renommer et supprimer une campagne. C'était le dernier critère d'acceptation de #2355 non tenu côté
/// interface, et il rendait la liste déroulante de « Modifier le passage » inutilisable pour qui
/// n'ouvre pas de terminal.
///
/// **Un seul formulaire, deux modes.** Vide, il crée ; une campagne sélectionnée, il la reflète et
/// « Enregistrer » la modifie. Deux fenêtres pour deux verbes auraient demandé de choisir avant de
/// savoir.
///
/// La suppression passe par le [ConfirmateurModifiable] du socle (#1013) et **jamais** par un `Alert`
/// en dur : un dialogue natif fige TestFX headless, et le geste deviendrait intestable. La
/// confirmation annonce le nombre de passages **détachés**, chiffre lu avant l'acte.
public class GestionCampagnesModaleController {

    private final GestionCampagnesViewModel viewModel;

    /// Confirmation de la suppression : porteur partagé injectable (#1013), stub déterministe en test.
    private final ConfirmateurModifiable confirmateur = new ConfirmateurModifiable();

    @FXML
    private VBox racine;

    @FXML
    private ListView<Campagne> listeCampagnes;

    @FXML
    private TextField champNom;

    @FXML
    private Spinner<Integer> champAnnee;

    @FXML
    private TextArea champCommentaire;

    @FXML
    private Button btnEnregistrer;

    @FXML
    private Button btnSupprimer;

    @FXML
    private HBox bandeauRetour;

    @FXML
    private Label lblRetour;

    @FXML
    private Button btnFermerRetour;

    @Inject
    public GestionCampagnesModaleController(GestionCampagnesViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    /// Porteur de confirmation exposé aux tests (#1013) : `confirmateur().definir(stub)`.
    ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }

    @FXML
    private void initialize() {
        // Année par défaut prise de l'horloge du service, jamais de `LocalDate.now()` : une capture doit
        // rendre la même image d'une année sur l'autre.
        champAnnee.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(2000, 2100, viewModel.anneeParDefaut()));

        listeCampagnes.setItems(viewModel.campagnes());
        listeCampagnes.setCellFactory(liste -> new CelluleCampagne());

        // La liste est la source de la sélection, et on la POUSSE dans le ViewModel : la lier
        // (`bind`) rendrait la propriété du ViewModel non assignable, et `supprimer()` casserait en
        // voulant la remettre à null.
        listeCampagnes.getSelectionModel().selectedItemProperty().addListener((obs, ancienne, nouvelle) -> {
            viewModel.selectionProperty().set(nouvelle);
            refleter(nouvelle);
        });

        // « Enregistrer » et « Supprimer » n'ont de sens que sur une campagne choisie : les griser le dit
        // avant le clic, plutôt que de répondre « choisissez d'abord » après (affordance, #790).
        var aucuneSelection =
                listeCampagnes.getSelectionModel().selectedItemProperty().isNull();
        btnEnregistrer.disableProperty().bind(aucuneSelection);
        btnSupprimer.disableProperty().bind(aucuneSelection);

        BandeauRetour.installer(
                bandeauRetour, lblRetour, btnFermerRetour, viewModel.retourProperty(), viewModel::effacerRetour);

        viewModel.charger();
    }

    /// Recopie la campagne sélectionnée dans le formulaire, ou le vide pour repartir sur une création.
    private void refleter(Campagne campagne) {
        if (campagne == null) {
            champNom.clear();
            champCommentaire.clear();
            return;
        }
        champNom.setText(campagne.nom());
        champAnnee.getValueFactory().setValue(campagne.annee());
        champCommentaire.setText(campagne.commentaire() == null ? "" : campagne.commentaire());
    }

    @FXML
    private void creer() {
        viewModel.creer(champNom.getText(), champAnnee.getValue(), commentaireSaisi());
        // Créer laisse le formulaire sur la campagne créée (le ViewModel la sélectionne) : l'action
        // suivante porte presque toujours sur elle.
        selectionner(viewModel.selectionProperty().get());
    }

    @FXML
    private void enregistrer() {
        viewModel.modifier(champNom.getText(), champAnnee.getValue(), commentaireSaisi());
    }

    @FXML
    private void supprimer() {
        Campagne cible = listeCampagnes.getSelectionModel().getSelectedItem();
        if (cible == null) {
            return;
        }
        long rattaches = viewModel.passagesRattaches(cible);
        String question = "Supprimer la campagne « " + cible.nom() + " » ?\n"
                + GestionCampagnesViewModel.phraseDetachement(rattaches);
        if (confirmateur.confirmer(question)) {
            viewModel.supprimer();
            listeCampagnes.getSelectionModel().clearSelection();
        }
    }

    @FXML
    private void fermer() {
        ((Stage) racine.getScene().getWindow()).close();
    }

    /// Aligne la sélection de la liste sur `campagne` (rien si elle n'y est pas).
    private void selectionner(Campagne campagne) {
        if (campagne != null) {
            listeCampagnes.getSelectionModel().select(campagne);
        }
    }

    /// Commentaire saisi, `null` quand le champ est vide : la colonne est nullable, et une chaîne vide
    /// n'est pas la même chose qu'une absence de commentaire.
    private String commentaireSaisi() {
        String saisi = champCommentaire.getText();
        return saisi == null || saisi.isBlank() ? null : saisi.trim();
    }

    /// Cellule d'une campagne : « Nom (année) », plus son commentaire ensuite s'il existe.
    private static final class CelluleCampagne extends ListCell<Campagne> {
        @Override
        protected void updateItem(Campagne campagne, boolean vide) {
            super.updateItem(campagne, vide);
            if (vide || campagne == null) {
                setText(null);
                return;
            }
            String base = campagne.nom() + "  (" + campagne.annee() + ")";
            setText(
                    campagne.commentaire() == null || campagne.commentaire().isBlank()
                            ? base
                            : base + "  -  " + campagne.commentaire());
        }
    }
}

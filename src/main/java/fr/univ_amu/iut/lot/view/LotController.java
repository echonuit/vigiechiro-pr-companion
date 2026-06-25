package fr.univ_amu.iut.lot.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

/// Controller de l'écran **M-Lot** (`Lot.fxml`).
///
/// Pur câblage (patron CM4) : lie le statut, le récapitulatif, le dossier à téléverser, les alertes
/// de cohérence (R14) et les deux actions du dépôt au [LotViewModel]. « Préparer le lot » et
/// « Marquer déposé » ne sont actifs que dans l'état workflow adéquat ; la zone d'alertes
/// n'apparaît qu'en présence d'alertes bloquantes. Aucun accès base de données ni logique métier ici
/// (règle ArchUnit `view_sans_jdbc`).
public class LotController implements EmplacementNavigation {

    private final LotViewModel viewModel;
    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;

    /// Contexte de navigation (passage + site), mémorisé pour reconstruire le fil d'Ariane du chrome.
    private ContextePassage contexte;

    @FXML
    private Label lblStatut;

    @FXML
    private Label lblRecap;

    @FXML
    private Label lblCheminDossier;

    @FXML
    private VBox zoneAlertes;

    @FXML
    private ListView<String> listeAlertes;

    @FXML
    private Button btnPreparer;

    @FXML
    private Button btnDeposer;

    @FXML
    private Label lblTitreArchives;

    @FXML
    private Button btnGenererArchives;

    @FXML
    private ListView<String> listeArchives;

    @FXML
    private Label lblMessage;

    @Inject
    public LotController(LotViewModel viewModel, OuvrirSite ouvrirSite, OuvrirPassage ouvrirPassage) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
    }

    @FXML
    private void initialize() {
        lblStatut.textProperty().bind(viewModel.statutProperty());
        lblRecap.textProperty().bind(viewModel.recapProperty());
        lblCheminDossier.textProperty().bind(viewModel.cheminDossierProperty());

        listeAlertes.setItems(viewModel.alertes());
        // La zone d'alertes n'a de sens qu'en présence d'alertes bloquantes (R14).
        BooleanBinding alertesPresentes = Bindings.isNotEmpty(viewModel.alertes());
        zoneAlertes.visibleProperty().bind(alertesPresentes);
        zoneAlertes.managedProperty().bind(alertesPresentes);

        btnPreparer.disableProperty().bind(viewModel.peutPreparerProperty().not());
        btnDeposer.disableProperty().bind(viewModel.peutDeposerProperty().not());

        // Archives de dépôt (#110) : titre = plafond configuré ; bouton actif une fois le lot préparé ;
        // la liste reflète les ZIP produits.
        lblTitreArchives.textProperty().bind(viewModel.titreArchivesProperty());
        btnGenererArchives
                .disableProperty()
                .bind(viewModel.peutGenererArchivesProperty().not());
        listeArchives.setItems(viewModel.archives());

        lblMessage.textProperty().bind(viewModel.messageProperty());
        var messagePresent = viewModel.messageProperty().isNotEmpty();
        lblMessage.visibleProperty().bind(messagePresent);
        lblMessage.managedProperty().bind(messagePresent);
    }

    /// Ouvre l'écran sur le passage `passage`. Appelée par [NavigationLot] après le chargement FXML ;
    /// mémorise le contexte pour le fil d'Ariane.
    public void ouvrirSur(ContextePassage passage) {
        this.contexte = passage;
        viewModel.ouvrirSur(passage.idPassage());
    }

    /// Emplacement dans le fil d'Ariane : `Mes sites › Carré N › Détails du passage N° X › Préparer le
    /// dépôt` (rendu par le chrome). Le segment passage rouvre M-Passage.
    @Override
    public List<Lieu> emplacement() {
        return EmplacementPassage.emplacementEnfant(contexte, ouvrirSite, ouvrirPassage, "Préparer le dépôt");
    }

    @FXML
    private void preparer() {
        viewModel.preparer();
    }

    @FXML
    private void deposer() {
        viewModel.deposer();
    }

    @FXML
    private void genererArchives() {
        viewModel.genererArchives();
    }
}

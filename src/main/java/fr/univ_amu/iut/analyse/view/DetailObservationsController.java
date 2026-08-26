package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.commun.view.DoubleClicLigne;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;

/// Controller du **panneau de détail** de l'analyse (`DetailObservations.fxml`, #2745) : les
/// observations de l'espèce sélectionnée, à travers les passages.
///
/// Sous-vue de [AnalyseController], à qui elle retire quinze champs `@FXML` et leur câblage. Elle ne
/// connaît que son panneau : titre, placeholder, colonnes, activation des deux boutons.
///
/// Ce qui traverse la frontière reste au parent, qui obtient la table par [#table()] : le sélecteur de
/// colonnes couvre les **trois** tables de l'écran, et les gestes (« Écouter », « Ouvrir le passage »,
/// « Fiche de l'espèce ») ont besoin de la sélection de l'inventaire, qui vit là-haut. Ils arrivent
/// donc ici en fonctions plutôt qu'en dépendances.
///
/// Elle reçoit ses appuis du parent et n'injecte rien : les ViewModel du dépôt sont non-singleton
/// (ADR 2745, gardé par `DecisionsRespecteesTest#une_sous_vue_ne_s_injecte_pas_son_modele`).
public class DetailObservationsController {

    @FXML
    private Label lblDetailTitre;

    /// Enveloppes (non désactivées) des deux boutons : portent le tooltip expliquant le grisage, qu'un
    /// Button désactivé n'afficherait pas (#789).
    @FXML
    private StackPane enveloppeEcouter;

    @FXML
    private Button boutonEcouter;

    @FXML
    private StackPane enveloppeOuvrirPassage;

    @FXML
    private Button boutonOuvrirPassage;

    @FXML
    private TableView<ObservationEspece> tableObservations;

    @FXML
    private TableColumn<ObservationEspece, String> colObsPassage;

    @FXML
    private TableColumn<ObservationEspece, String> colObsCarre;

    @FXML
    private TableColumn<ObservationEspece, String> colObsRichesse;

    @FXML
    private TableColumn<ObservationEspece, String> colObsPoint;

    /// Commune du point (#3165) : sur cette table seulement, les deux autres agrégeant plusieurs points.
    @FXML
    private TableColumn<ObservationEspece, String> colObsCommune;

    @FXML
    private TableColumn<ObservationEspece, String> colObsTadarida;

    @FXML
    private TableColumn<ObservationEspece, String> colObsObservateur;

    @FXML
    private TableColumn<ObservationEspece, String> colObsStatut;

    /// Placeholder tant qu'aucune observation n'est listée (aucune espèce sélectionnée).
    @FXML
    private Label lblDetailVide;

    private Consumer<ObservationEspece> ecouter;
    private Consumer<ObservationEspece> ouvrirPassage;

    /// Câble le panneau sur le modèle **du parent**, appelée depuis son `initialize()`.
    ///
    /// @param viewModel modèle de l'analyse, d'où viennent les observations et le titre du détail
    /// @param richesseDuCarre nombre d'espèces distinctes d'un carré, pour la colonne « Espèces du carré »
    /// @param ecouter ouvre la vue audio sur une observation (le parent connaît la source et le statut)
    /// @param ouvrirPassage ouvre le passage d'une observation, avec son contexte de site
    /// @param ouvrirFicheEspece fiche de l'espèce **sélectionnée dans l'inventaire**, pour le double-clic
    void installer(
            AnalyseViewModel viewModel,
            Function<String, String> richesseDuCarre,
            Consumer<ObservationEspece> ecouter,
            Consumer<ObservationEspece> ouvrirPassage,
            Runnable ouvrirFicheEspece) {
        Objects.requireNonNull(viewModel, "viewModel");
        this.ecouter = Objects.requireNonNull(ecouter, "ecouter");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        Objects.requireNonNull(ouvrirFicheEspece, "ouvrirFicheEspece");

        tableObservations.setItems(viewModel.observations());
        lblDetailTitre.textProperty().bind(viewModel.detailTitreProperty());

        ColonnesAnalyse.observations(
                new ColonnesAnalyse.Observations(
                        colObsPassage,
                        colObsCarre,
                        colObsRichesse,
                        colObsPoint,
                        colObsCommune,
                        colObsTadarida,
                        colObsObservateur,
                        colObsStatut),
                Objects.requireNonNull(richesseDuCarre, "richesseDuCarre"));

        var detailVide = Bindings.isEmpty(viewModel.observations());
        lblDetailVide.visibleProperty().bind(detailVide);
        lblDetailVide.managedProperty().bind(detailVide);

        // Actions du détail actives seulement quand une observation est sélectionnée.
        var selection = tableObservations.getSelectionModel().selectedItemProperty();
        boutonOuvrirPassage.disableProperty().bind(selection.isNull());
        boutonEcouter.disableProperty().bind(selection.isNull());
        // Explique le grisage (#789) sur les enveloppes (un Button désactivé n'affiche pas de tooltip).
        IndicateurBlocage.expliquer(
                enveloppeEcouter,
                Bindings.when(selection.isNull())
                        .then("Sélectionnez une observation dans le tableau pour l'écouter et la valider.")
                        .otherwise("Écouter l'observation sélectionnée et la valider."));
        IndicateurBlocage.expliquer(
                enveloppeOuvrirPassage,
                Bindings.when(selection.isNull())
                        .then("Sélectionnez une observation dans le tableau pour ouvrir son passage.")
                        .otherwise("Ouvrir le passage de l'observation sélectionnée."));

        // Double-clic sur une observation → fiche de l'espèce (#1794). Toutes les observations du détail
        // portent la même espèce ; seule l'agrégée sélectionnée porte le nom latin/vernaculaire, donc c'est
        // elle qu'on ouvre. L'écoute d'une détection reste le bouton « Écouter » et « Ouvrir le passage » le
        // sien.
        DoubleClicLigne.installer(tableObservations, observation -> ouvrirFicheEspece.run());
    }

    /// La table des observations, pour ce qui traverse la frontière : le sélecteur de colonnes de l'écran
    /// couvre les trois tables, et ses items de ligne visent celle-ci.
    TableView<ObservationEspece> table() {
        return tableObservations;
    }

    /// « 🎧 Écouter / valider » : ouvre la vue audio sur la détection sélectionnée. Le parent fournit le
    /// geste, parce que la source (toutes les observations de l'espèce, filtre de statut courant) se
    /// décide en haut de l'écran.
    @FXML
    private void ecouterValider() {
        ecouter.accept(tableObservations.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void ouvrirPassage() {
        ObservationEspece observation = tableObservations.getSelectionModel().getSelectedItem();
        if (observation != null) {
            ouvrirPassage.accept(observation);
        }
    }
}

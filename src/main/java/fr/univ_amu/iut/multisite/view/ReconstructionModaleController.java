package fr.univ_amu.iut.multisite.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.multisite.viewmodel.ReconstructionViewModel;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/// Controller de la modale **« Reconstruire un passage manquant »** (`ReconstructionModale.fxml`, #1396).
///
/// Les deux appels au réseau (lister les participations, en reconstruire une) sont **bloquants** : ils
/// partent sur l'[ExecuteurTache], et la modale se fige le temps du travail plutôt que d'accepter un
/// second clic (`operationEnCours`). Le résultat, succès comme refus, revient **dans la modale** : un
/// point d'écoute inconnu ici ou une analyse non terminée sont des réponses, pas des incidents.
///
/// Après une reconstruction réussie, la fermeture rafraîchit l'écran appelant : la nuit **apparaît** dans
/// la table des passages, ce qui est la preuve visible que la reconstruction a eu lieu.
public class ReconstructionModaleController {

    /// Ce que la colonne « Point d'écoute » dit d'une nuit dont le point n'existe pas encore ici. La
    /// reconstruction est alors refusée : rattacher une nuit au mauvais point serait une donnée fausse.
    private static final String POINT_INCONNU = "Inconnu ici : créez d'abord le site et le point";

    private static final String POINT_CONNU = "Connu";

    private final ReconstructionViewModel viewModel;
    private final ExecuteurTache executeur;

    /// Vrai pendant un appel réseau : neutralise les deux boutons (pas de double reconstruction).
    private final SimpleBooleanProperty operationEnCours = new SimpleBooleanProperty(false);

    /// Rafraîchissement de l'écran appelant, exécuté à la fermeture **si** une nuit a été reconstruite.
    private final SimpleObjectProperty<Runnable> apresSucces = new SimpleObjectProperty<>(() -> {});

    @FXML
    private VBox racine;

    @FXML
    private TableView<ParticipationOrpheline> tableOrphelines;

    @FXML
    private TableColumn<ParticipationOrpheline, String> colCarre;

    @FXML
    private TableColumn<ParticipationOrpheline, String> colPoint;

    @FXML
    private TableColumn<ParticipationOrpheline, String> colNuit;

    @FXML
    private TableColumn<ParticipationOrpheline, String> colPointConnu;

    @FXML
    private Label lblVide;

    @FXML
    private Label lblMessage;

    @FXML
    private Label lblCompteRendu;

    @FXML
    private Button boutonReconstruire;

    @FXML
    private Button boutonFermer;

    @FXML
    private StackPane enveloppeReconstruire;

    @Inject
    public ReconstructionModaleController(ReconstructionViewModel viewModel, ExecuteurTache executeur) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
    }

    @FXML
    private void initialize() {
        TableDonnees.uniformiser(tableOrphelines);
        colCarre.setCellValueFactory(cellule -> texte(cellule.getValue().numeroCarre()));
        colPoint.setCellValueFactory(cellule -> texte(cellule.getValue().codePoint()));
        colNuit.setCellValueFactory(cellule -> texte(cellule.getValue().dateDebut()));
        colPointConnu.setCellValueFactory(
                cellule -> texte(cellule.getValue().pointLocalConnu() ? POINT_CONNU : POINT_INCONNU));
        tableOrphelines.setItems(viewModel.orphelines());

        lblMessage.textProperty().bind(viewModel.messageProperty());
        lblMessage.visibleProperty().bind(viewModel.messageProperty().isNotEmpty());
        lblMessage.managedProperty().bind(viewModel.messageProperty().isNotEmpty());
        lblCompteRendu.textProperty().bind(viewModel.compteRenduProperty());
        lblCompteRendu.visibleProperty().bind(viewModel.compteRenduProperty().isNotEmpty());
        lblCompteRendu.managedProperty().bind(viewModel.compteRenduProperty().isNotEmpty());

        // Le bouton ne s'ouvre que sur une nuit choisie DONT le point est connu ici, et l'enveloppe dit
        // laquelle des deux conditions manque (#789) plutôt que de laisser un bouton gris sans raison.
        BooleanBinding reconstructible = Bindings.createBooleanBinding(
                () -> {
                    ParticipationOrpheline choisie =
                            tableOrphelines.getSelectionModel().getSelectedItem();
                    return choisie != null && choisie.pointLocalConnu();
                },
                tableOrphelines.getSelectionModel().selectedItemProperty());
        boutonReconstruire.disableProperty().bind(reconstructible.not().or(operationEnCours));
        boutonFermer.disableProperty().bind(operationEnCours);
        IndicateurBlocage.expliquer(
                enveloppeReconstruire,
                Bindings.when(reconstructible)
                        .then("Rapatrie cette nuit en passage archivé : observations comprises, sans audio.")
                        .otherwise(motifDeBlocage()));

        charger();
    }

    /// Motif affiché quand le bouton est bloqué : dit **quelle** condition manque, et comment la lever.
    private StringBinding motifDeBlocage() {
        return Bindings.createStringBinding(
                () -> tableOrphelines.getSelectionModel().getSelectedItem() == null
                        ? "Choisissez la nuit à reconstruire dans la liste."
                        : "Le point d'écoute de cette nuit n'existe pas sur cette machine. Créez d'abord le"
                                + " site et le point (Mes sites), puis revenez : rattacher une nuit au mauvais"
                                + " point serait une donnée fausse.",
                tableOrphelines.getSelectionModel().selectedItemProperty());
    }

    /// Lecture des participations (réseau) hors du fil JavaFX ; l'échec (hors connexion, plateforme
    /// injoignable) rejoint le message de la modale au lieu de disparaître dans le fil de fond.
    private void charger() {
        operationEnCours.set(true);
        executeur.executer(
                viewModel::charger,
                chargees -> {
                    operationEnCours.set(false);
                    lblVide.setText("Aucune nuit manquante.");
                    viewModel.appliquer(chargees);
                },
                erreur -> {
                    operationEnCours.set(false);
                    lblVide.setText("Liste indisponible.");
                    viewModel.signalerErreur(erreur);
                });
    }

    @FXML
    private void reconstruire() {
        ParticipationOrpheline choisie = tableOrphelines.getSelectionModel().getSelectedItem();
        if (choisie == null) {
            return;
        }
        operationEnCours.set(true);
        executeur.executer(
                () -> viewModel.reconstruire(choisie),
                rapport -> {
                    operationEnCours.set(false);
                    viewModel.restituer(choisie, rapport);
                },
                erreur -> {
                    operationEnCours.set(false);
                    viewModel.signalerErreur(erreur);
                });
    }

    /// Ferme la modale et, **si** une nuit a été reconstruite, rafraîchit l'écran appelant : elle y
    /// apparaît alors dans la table des passages.
    @FXML
    private void fermer() {
        if (viewModel.reconstruitProperty().get()) {
            apresSucces.get().run();
        }
        ((Stage) racine.getScene().getWindow()).close();
    }

    /// Appelé par [NavigationMultisite] juste après le chargement du FXML.
    public void demarrer(Runnable rafraichirLAppelant) {
        apresSucces.set(Objects.requireNonNull(rafraichirLAppelant, "rafraichirLAppelant"));
    }

    private static ObservableValue<String> texte(String valeur) {
        return new SimpleObjectProperty<>(valeur == null ? "" : valeur);
    }
}

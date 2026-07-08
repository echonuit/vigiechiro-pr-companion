package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.passage.model.MaterielMicro;
import fr.univ_amu.iut.passage.model.MeteoReleve;
import fr.univ_amu.iut.passage.model.PositionMicro;
import fr.univ_amu.iut.passage.viewmodel.RattachementViewModel;
import java.util.Objects;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/// Controller de la **modale « Modifier le passage »** (`RattachementModale.fxml`, E2.S8).
///
/// Rassemble en une seule fenêtre l'édition du **rattachement** (année, n° de passage : deux `Spinner`
/// liés en bidirectionnel au [RattachementViewModel]) **et** des **conditions de dépôt** VigieChiro
/// (relevé météo, matériel du micro) : ces métadonnées, autrefois posées sur l'écran M-Passage, se
/// saisissent au moment où l'on modifie le passage. Le type de micro est une **liste fermée**
/// ([MaterielMicro#TYPES_VIGIECHIRO]).
///
/// « Appliquer » enregistre le tout d'un bloc via [RattachementViewModel#appliquer] ; en cas de succès
/// la modale se ferme et exécute le `Runnable` fourni par l'appelant (rafraîchissement de M-Passage).
/// « Annuler » ferme sans rien persister. « Récupérer la météo » (réseau) pré-remplit les champs
/// hors du fil JavaFX.
public class RattachementModaleController {

    private final RattachementViewModel viewModel;
    private Runnable apresSucces = () -> {};

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

    @FXML
    private TextField champTemperature;

    @FXML
    private TextField champTemperatureFin;

    @FXML
    private TextField champVent;

    @FXML
    private TextField champCouverture;

    @FXML
    private Button boutonRecupererMeteo;

    @FXML
    private ComboBox<PositionMicro> champPosition;

    @FXML
    private TextField champHauteur;

    @FXML
    private ComboBox<String> champTypeMicro;

    @Inject
    public RattachementModaleController(RattachementViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

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

        lierConditions();
    }

    /// Lie les champs des conditions de dépôt (météo + matériel du micro) au sous-ViewModel
    /// [RattachementViewModel#conditions] : saisie bidirectionnelle des grandeurs (vide = effacer). La
    /// position et le type de micro sont des **listes fermées** (une entrée « non renseigné » en tête).
    private void lierConditions() {
        var conditions = viewModel.conditions();
        champTemperature.textProperty().bindBidirectional(conditions.temperatureSaisieProperty());
        champTemperatureFin.textProperty().bindBidirectional(conditions.temperatureFinSaisieProperty());
        champVent.textProperty().bindBidirectional(conditions.ventSaisieProperty());
        champCouverture.textProperty().bindBidirectional(conditions.couvertureNuageuseSaisieProperty());

        // Position : liste sol/canopée + entrée vide « non renseigné » ; converter → libellé lisible.
        champPosition.getItems().setAll(null, PositionMicro.SOL, PositionMicro.CANOPEE);
        champPosition.setConverter(new StringConverter<>() {
            @Override
            public String toString(PositionMicro position) {
                return position == null ? "" : position.libelle();
            }

            @Override
            public PositionMicro fromString(String texte) {
                return null;
            }
        });
        champPosition.valueProperty().bindBidirectional(conditions.positionSaisieProperty());

        champHauteur.textProperty().bindBidirectional(conditions.hauteurSaisieProperty());

        // Type de micro : liste fermée VigieChiro + entrée vide « (non renseigné) » en tête. Une valeur
        // héritée absente de la liste reste affichée telle quelle (pas de perte à l'ouverture).
        champTypeMicro.getItems().add("");
        champTypeMicro.getItems().addAll(MaterielMicro.TYPES_VIGIECHIRO);
        champTypeMicro.setConverter(new StringConverter<>() {
            @Override
            public String toString(String type) {
                return type == null || type.isBlank() ? "(non renseigné)" : type;
            }

            @Override
            public String fromString(String texte) {
                return texte;
            }
        });
        champTypeMicro.valueProperty().bindBidirectional(conditions.typeMicroSaisieProperty());
    }

    /// Prépare la modale sur le passage `idPassage` (carré/point fournis par M-Passage) et mémorise
    /// l'action de succès (rafraîchir l'écran appelant).
    public void demarrer(Long idPassage, String carre, String codePoint, Runnable apresSucces) {
        this.apresSucces = Objects.requireNonNull(apresSucces, "apresSucces");
        viewModel.ouvrirSur(idPassage, carre, codePoint);
    }

    @FXML
    private void appliquer() {
        if (viewModel.appliquer()) {
            apresSucces.run();
            fermer();
        }
    }

    /// « Récupérer la météo » (#547) : l'appel Open-Meteo est **réseau**, donc lancé en **tâche de fond**
    /// (thread virtuel) pour ne pas geler l'IHM ; le bouton est désactivé le temps de l'appel, et le
    /// pré-remplissage des champs (ou le message d'indisponibilité) revient sur le fil JavaFX via
    /// [Platform#runLater].
    @FXML
    private void recupererMeteo() {
        boutonRecupererMeteo.setDisable(true);
        Thread.ofVirtual().name("recuperation-meteo").start(() -> {
            Optional<MeteoReleve> releve = viewModel.conditions().recupererMeteo();
            Platform.runLater(() -> {
                viewModel.conditions().appliquerMeteoRecuperee(releve);
                boutonRecupererMeteo.setDisable(false);
            });
        });
    }

    @FXML
    private void annuler() {
        fermer();
    }

    private void fermer() {
        ((Stage) racine.getScene().getWindow()).close();
    }
}

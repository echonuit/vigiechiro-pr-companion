package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ValidationFormulaire;
import fr.univ_amu.iut.passage.model.CouvertureNuageuse;
import fr.univ_amu.iut.passage.model.MaterielMicro;
import fr.univ_amu.iut.passage.model.PositionMicro;
import fr.univ_amu.iut.passage.model.Vent;
import fr.univ_amu.iut.passage.viewmodel.RattachementViewModel;
import java.util.Objects;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
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
    private final ExecuteurTache executeur;
    private Runnable apresSucces = () -> {};

    /// Confirmation d'action destructive : porteur partagé injectable (#1013), stub déterministe en test.
    private final ConfirmateurModifiable confirmateur = new ConfirmateurModifiable();

    /// Opération réseau en cours (#1216) : patron de la modale connexion (#1255), l'état local + le
    /// grisage des boutons jouent le rôle du voile d'occupation (une modale compacte n'en porte pas).
    /// Une seule opération à la fois : les trois actions touchent les mêmes champs météo/micro.
    private final BooleanProperty operationEnCours = new SimpleBooleanProperty(false);

    private ObjectProperty<Integer> anneeObjet;
    private ObjectProperty<Integer> numeroObjet;

    @FXML
    private VBox racine;

    @FXML
    private Spinner<Integer> spinnerAnnee;

    @FXML
    private Spinner<Integer> spinnerNumero;

    @FXML
    private Button boutonAppliquer;

    @FXML
    private Label labelRecap;

    @FXML
    private Label messageErreur;

    @FXML
    private TextField champTemperature;

    @FXML
    private TextField champTemperatureFin;

    @FXML
    private ComboBox<Vent> champVent;

    @FXML
    private ComboBox<CouvertureNuageuse> champCouverture;

    @FXML
    private Button boutonRecupererMeteo;

    @FXML
    private HBox ligneSyncVigieChiro;

    @FXML
    private Button boutonTirerVigieChiro;

    @FXML
    private ComboBox<PositionMicro> champPosition;

    @FXML
    private TextField champHauteur;

    @FXML
    private ComboBox<String> champTypeMicro;

    @Inject
    public RattachementModaleController(RattachementViewModel viewModel, ExecuteurTache executeur) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
    }

    /// Porteur de confirmation exposé aux tests (#1013) : `confirmateur().definir(stub)`.
    ConfirmateurModifiable confirmateur() {
        return confirmateur;
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

        // Validation « en direct » (#790) : « Appliquer » reste désactivé tant que l'année n'a pas 4
        // chiffres et le n° de passage au moins 1 ; chaque spinner rougit sur une valeur hors domaine. Le
        // service reste l'autorité finale (le message inline reste affiché en cas d'échec métier).
        BooleanBinding anneeValide = Bindings.createBooleanBinding(
                () -> viewModel.anneeProperty().get() >= 1000
                        && viewModel.anneeProperty().get() <= 9999,
                viewModel.anneeProperty());
        BooleanBinding numeroValide = Bindings.createBooleanBinding(
                () -> viewModel.numeroPassageProperty().get() >= 1, viewModel.numeroPassageProperty());
        boutonAppliquer.disableProperty().bind(anneeValide.and(numeroValide).not());
        ValidationFormulaire.marquerInvalide(spinnerAnnee, anneeValide.not());
        ValidationFormulaire.marquerInvalide(spinnerNumero, numeroValide.not());

        lierConditions();

        // « Synchroniser depuis VigieChiro » n'apparaît que si l'observateur est connecté (passerelle
        // disponible) : inutile de proposer un tir hors connexion.
        boolean peutSynchroniser = viewModel.peutSynchroniser();
        ligneSyncVigieChiro.setVisible(peutSynchroniser);
        ligneSyncVigieChiro.setManaged(peutSynchroniser);

        // Boutons réseau relâchés par binding (#1216, patron #1254) : plus de setDisable posé à la
        // main de part et d'autre du travail, plus de bouton figé si l'appel échoue.
        boutonRecupererMeteo.disableProperty().bind(operationEnCours);
        boutonTirerVigieChiro.disableProperty().bind(operationEnCours);
    }

    /// Lie les champs des conditions de dépôt (météo + matériel du micro) au sous-ViewModel
    /// [RattachementViewModel#conditions] : températures/hauteur en saisie libre ; vent, couverture,
    /// position et type de micro en **listes fermées** (une entrée « non renseigné » en tête).
    private void lierConditions() {
        var conditions = viewModel.conditions();
        champTemperature.textProperty().bindBidirectional(conditions.temperatureSaisieProperty());
        champTemperatureFin.textProperty().bindBidirectional(conditions.temperatureFinSaisieProperty());

        // Vent et couverture nuageuse : catégories d'appréciation VigieChiro (nul/faible/… et tranches).
        lierListeEnum(champVent, Vent.values(), Vent::libelle, conditions.ventSaisieProperty());
        lierListeEnum(
                champCouverture,
                CouvertureNuageuse.values(),
                CouvertureNuageuse::libelle,
                conditions.couvertureNuageuseSaisieProperty());

        // Position : liste sol/canopée.
        lierListeEnum(
                champPosition, PositionMicro.values(), PositionMicro::libelle, conditions.positionSaisieProperty());

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

    /// Peuple un `ComboBox` d'énum (avec une entrée « non renseigné » `null` en tête) et le lie en
    /// bidirectionnel à `propriete`, en affichant le `libelle` de chaque valeur.
    private static <E extends Enum<E>> void lierListeEnum(
            ComboBox<E> combo, E[] valeurs, Function<E, String> libelle, ObjectProperty<E> propriete) {
        combo.getItems().add(null);
        combo.getItems().addAll(valeurs);
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(E valeur) {
                return valeur == null ? "" : libelle.apply(valeur);
            }

            @Override
            public E fromString(String texte) {
                return null;
            }
        });
        combo.valueProperty().bindBidirectional(propriete);
    }

    /// Prépare la modale sur le passage `idPassage` (carré/point fournis par M-Passage) et mémorise
    /// l'action de succès (rafraîchir l'écran appelant).
    public void demarrer(Long idPassage, String carre, String codePoint, Runnable apresSucces) {
        this.apresSucces = Objects.requireNonNull(apresSucces, "apresSucces");
        viewModel.ouvrirSur(idPassage, carre, codePoint);
    }

    @FXML
    private void appliquer() {
        // Confirmation (#798) avant le renommage irréversible des séquences sur le disque. Inutile si le
        // rattachement ne change pas (aucun effet disque) : on reprend le récap vivant comme message.
        if (viewModel.entraineRenommage()
                && !confirmateur.confirmer(viewModel.recapProperty().get() + "\n\nAppliquer ce rattachement ?")) {
            return;
        }
        if (viewModel.appliquer()) {
            pousserVersVigieChiro();
            apresSucces.run();
            fermer();
        }
    }

    /// Après un enregistrement **local** réussi, **pousse** les métadonnées (météo/micro/dates) vers la
    /// participation VigieChiro en **tâche de fond** (réseau, hors fil JavaFX via [ExecuteurTache]) :
    /// best-effort et silencieux (le ViewModel avale un passage non encore lié à une participation). La
    /// modale se ferme sans attendre le réseau : les callbacks sont volontairement muets, un échec
    /// inattendu n'a plus de fenêtre où s'afficher (les métadonnées repartiront au prochain dépôt).
    private void pousserVersVigieChiro() {
        executeur.executer(
                () -> {
                    viewModel.pousserVersVigieChiro();
                    return null;
                },
                resultat -> {},
                erreur -> {});
    }

    /// « Récupérer la météo » (#547) : l'appel Open-Meteo est **réseau**, donc déporté hors du fil
    /// JavaFX (socle [ExecuteurTache] #1216) ; le bouton suit `operationEnCours` par binding, et le
    /// pré-remplissage des champs (ou le message d'indisponibilité) revient sur le fil JavaFX.
    @FXML
    private void recupererMeteo() {
        operationEnCours.set(true);
        executeur.executer(
                () -> viewModel.conditions().recupererMeteo(),
                releve -> {
                    operationEnCours.set(false);
                    viewModel.conditions().appliquerMeteoRecuperee(releve);
                },
                erreur -> {
                    operationEnCours.set(false);
                    viewModel.signalerErreur(erreur);
                });
    }

    /// « Synchroniser depuis VigieChiro » : **tire** les métadonnées de la participation (réseau) hors
    /// du fil JavaFX (socle [ExecuteurTache] #1216), puis **recharge** les champs météo/micro et
    /// affiche le message sur le fil JavaFX — même patron que « Récupérer la météo ».
    @FXML
    private void tirerDepuisVigieChiro() {
        operationEnCours.set(true);
        executeur.executer(
                viewModel::tirerDepuisVigieChiro,
                recupere -> {
                    operationEnCours.set(false);
                    viewModel.rechargerApresTir(recupere);
                },
                erreur -> {
                    operationEnCours.set(false);
                    viewModel.signalerErreur(erreur);
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

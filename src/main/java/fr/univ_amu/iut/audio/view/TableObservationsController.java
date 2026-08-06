package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MarqueurEspecesAEnjeu;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/// Controller de la **table des observations** de la vue audio (`TableObservations.fxml`, #2745).
///
/// Sous-vue de [SonsValidationController], à qui elle retire ses 23 colonnes et leur câblage. Elle ne
/// connaît que la table : tri, mode de sélection, colonnes, revue au clavier et actions de sélection.
/// Tout ce qui a besoin de la table **et** d'autre chose (panneau d'écoute, menu ☰, barre de filtres,
/// gestionnaire de colonnes) reste câblé par le parent, qui obtient la table par [#table()].
///
/// ⚠️ Cette classe existe pour une raison **mesurée**, pas esthétique : `NcssCount` compte les
/// déclarations de champs, et les 82 champs `@FXML` du parent pesaient 67 de ses 199 points, à un
/// plafond de 200. Aucun regroupement de méthodes ne pouvait les déplacer. Voir
/// `dev-docs/decisions/2745-une-vue-riche-se-decoupe-en-sous-vues.md`.
public class TableObservationsController {

    private AudioViewModel viewModel;

    @FXML
    private TableView<LigneObservationAudio> tableObservations;

    @FXML
    private TableColumn<LigneObservationAudio, String> colTadarida;

    @FXML
    private TableColumn<LigneObservationAudio, String> colProba;

    @FXML
    private TableColumn<LigneObservationAudio, String> colFrequence;

    @FXML
    private TableColumn<LigneObservationAudio, String> colFme;

    @FXML
    private TableColumn<LigneObservationAudio, String> colFreqTerminale;

    @FXML
    private TableColumn<LigneObservationAudio, LocalDateTime> colHeure;

    @FXML
    private TableColumn<LigneObservationAudio, String> colDebut;

    @FXML
    private TableColumn<LigneObservationAudio, String> colDuree;

    @FXML
    private TableColumn<LigneObservationAudio, String> colObservateur;

    @FXML
    private TableColumn<LigneObservationAudio, String> colCertitude;

    @FXML
    private TableColumn<LigneObservationAudio, String> colFichier;

    @FXML
    private TableColumn<LigneObservationAudio, String> colPassage;

    @FXML
    private TableColumn<LigneObservationAudio, String> colCarre;

    @FXML
    private TableColumn<LigneObservationAudio, String> colNomSite;

    @FXML
    private TableColumn<LigneObservationAudio, String> colPoint;

    /// Commune du point d'écoute (#3164) : contexte, donc masquée sur un passage unique.
    @FXML
    private TableColumn<LigneObservationAudio, String> colCommune;

    @FXML
    private TableColumn<LigneObservationAudio, String> colDate;

    @FXML
    private TableColumn<LigneObservationAudio, String> colStatut;

    @FXML
    private TableColumn<LigneObservationAudio, String> colReference;

    @FXML
    private TableColumn<LigneObservationAudio, String> colCommentaire;

    @FXML
    private TableColumn<LigneObservationAudio, String> colTexteCommentaire;

    @FXML
    private TableColumn<LigneObservationAudio, String> colValidateur;

    @FXML
    private TableColumn<LigneObservationAudio, String> colFil;

    /// Colonne-indicateur **espèce à enjeu** (#2353) : bouclier sur les lignes dont le taxon retenu est
    /// une espèce prioritaire du plan national.
    @FXML
    private TableColumn<LigneObservationAudio, String> colEnjeu;

    @FXML
    private Label lblVide;

    private ColonnesAudio.Colonnes colonnes;
    private MarqueurEspecesAEnjeu marqueurEnjeu;
    private ActionsSelectionAudio actionsSelection;

    /// Câble la table sur le modèle **du parent**, appelée par [SonsValidationController#initialize()].
    ///
    /// ⚠️ Ce câblage ne peut pas vivre dans un `initialize()` avec un [AudioViewModel] injecté : ce
    /// modèle est délibérément **non-singleton** (`AudioModule`, « un VM frais par chargement d'écran »),
    /// si bien qu'une injection ici rendrait un **second** modèle, vide. L'écran compilait, se chargeait
    /// et s'affichait ; la table restait simplement vide et les actions ne portaient sur rien. Seuls les
    /// TestFX l'ont vu. Une sous-vue reçoit donc son modèle de son parent, elle ne se le procure pas.
    void installer(AudioViewModel viewModel, AppuisAudio appuis) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        Objects.requireNonNull(appuis, "appuis");

        // Densité et habillage de table uniformes (#690).
        TableDonnees.uniformiser(tableObservations);
        configurerColonnes(appuis);

        // Rendre les en-têtes cliquables réellement triants : la table est alimentée par une FilteredList
        // (non triable en place) ; on l'enveloppe dans une SortedList dont le comparateur suit celui de la
        // table. Sans cela, cliquer un en-tête ne réordonnait rien. L'ordre initial reste l'ordre de revue.
        SortedList<LigneObservationAudio> triees = new SortedList<>(viewModel.observationsFiltrees());
        triees.comparatorProperty().bind(tableObservations.comparatorProperty());
        tableObservations.setItems(triees);

        // Multi-sélection (#479) : traiter un lot d'un coup. Le suivi audio/détail suit la DERNIÈRE ligne
        // sélectionnée (selectedItemProperty), les actions opèrent sur tout le lot via actionsSelection.
        tableObservations.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        actionsSelection = new ActionsSelectionAudio(tableObservations, viewModel);

        // Revue au clavier (#478) : Entrée = valider, R = référence, N = prochaine « À revoir » ; ↑/↓ =
        // navigation native. Entrée/R passent par actionsSelection (unitaire si 1 ligne, lot si plusieurs).
        RevueClavier.installer(tableObservations, viewModel, actionsSelection);
    }

    /// Câble les colonnes (valeur, cellules, comparateurs de tri). Le détail vit dans [ColonnesAudio] ;
    /// on lui passe les colonnes injectées par le FXML, regroupées.
    private void configurerColonnes(AppuisAudio appuis) {
        marqueurEnjeu = new MarqueurEspecesAEnjeu(appuis.especesPrioritaires());
        colonnes = new ColonnesAudio.Colonnes(
                colTadarida,
                colProba,
                colFrequence,
                colDebut,
                colDuree,
                colObservateur,
                colCertitude,
                colFichier,
                colPassage,
                colCarre,
                colNomSite,
                colPoint,
                colCommune,
                colDate,
                colHeure,
                colStatut,
                colReference,
                colCommentaire,
                colTexteCommentaire,
                colValidateur,
                colFil,
                colEnjeu);
        ColonnesAudio.configurer(
                colonnes, ligne -> marqueurEnjeu.aEnjeu(ligne.taxonRetenu()), viewModel.actions()::commenter);
    }

    /// La table elle-même, pour les câblages qui ont besoin d'elle **et** d'un nœud du parent.
    TableView<LigneObservationAudio> table() {
        return tableObservations;
    }

    /// Les colonnes regroupées, pour l'adaptation au contexte ([ColonnesAudio#adapterAuContexte]).
    ColonnesAudio.Colonnes colonnes() {
        return colonnes;
    }

    /// Actions sur la sélection courante (unitaires ou en lot), partagées avec la barre d'actions du parent.
    ActionsSelectionAudio actionsSelection() {
        return actionsSelection;
    }

    /// Marqueur des espèces à enjeu, réutilisé par le résumé de la barre de statut et la barre de filtres.
    MarqueurEspecesAEnjeu marqueurEnjeu() {
        return marqueurEnjeu;
    }

    /// Message d'état vide, superposé à la table : câblé par [MessagesEcranAudio] avec les deux bandeaux
    /// du parent, dont il forme la troisième voix.
    Label labelVide() {
        return lblVide;
    }

    /// Colonnes proposées au sélecteur d'affichage/réordonnancement, dans l'ordre voulu.
    List<GestionnaireColonnes.Colonne> pourLeSelecteur() {
        return ColonnesAudio.pourLeSelecteur(colonnes, colFme, colFreqTerminale);
    }

    /// Colonne **FME**, alimentée par le repérage du cri dans le panneau d'écoute (#500).
    TableColumn<LigneObservationAudio, String> colonneFme() {
        return colFme;
    }

    /// Colonne **fréquence terminale**, alimentée par le même repérage (#500).
    TableColumn<LigneObservationAudio, String> colonneFrequenceTerminale() {
        return colFreqTerminale;
    }
}

package fr.univ_amu.iut.qualification.view;

import com.google.inject.Inject;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.SequenceEnSelection;
import fr.univ_amu.iut.qualification.viewmodel.QualificationViewModel;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

/// Controller de l'écran **M-Qualification** (`Qualification.fxml`).
///
/// Pur câblage (patron CM4) : relie la liste de la sélection (colonne gauche) au
/// [SelectionEcouteViewModel] et le verdict différé (colonne droite) au [QualificationViewModel].
/// Aucun accès base de données ni logique métier ici (règle ArchUnit `view_sans_jdbc`). La vue
/// audio, la modale de personnalisation et les raccourcis clavier viennent dans les tranches
/// suivantes ; un emplacement réservé tient lieu de zone audio.
public class QualificationController {

  private final QualificationViewModel verdictVm;
  private final SelectionEcouteViewModel selectionVm;

  @FXML private Label lblTitreContexte;
  @FXML private Label lblPlageHoraire;
  @FXML private Label lblVolumetrie;
  @FXML private Label lblVerdictActuel;
  @FXML private Label lblStatut;
  @FXML private Label feuCouverture;
  @FXML private Label feuNombre;
  @FXML private Label feuRenommage;
  @FXML private Label lblAnomalie;
  @FXML private Label lblListeTitre;
  @FXML private ProgressBar barreProgression;
  @FXML private Label lblProgression;
  @FXML private TableView<SequenceEnSelection> tableSequences;
  @FXML private TableColumn<SequenceEnSelection, String> colPosition;
  @FXML private TableColumn<SequenceEnSelection, String> colFichier;
  @FXML private TableColumn<SequenceEnSelection, String> colDuree;
  @FXML private TableColumn<SequenceEnSelection, String> colEcoute;
  @FXML private Label lblSeqNumero;
  @FXML private Label lblSeqMeta;
  @FXML private AudioView audioView;
  @FXML private Button boutonOk;
  @FXML private Button boutonDouteux;
  @FXML private Button boutonAJeter;
  @FXML private TextArea champCommentaire;
  @FXML private Label lblAvertissement;
  @FXML private Label lblMessage;
  @FXML private Button boutonEnregistrer;

  @Inject
  public QualificationController(
      QualificationViewModel verdictVm, SelectionEcouteViewModel selectionVm) {
    this.verdictVm = Objects.requireNonNull(verdictVm, "verdictVm");
    this.selectionVm = Objects.requireNonNull(selectionVm, "selectionVm");
  }

  @FXML
  private void initialize() {
    // Bandeau : identité de la nuit (VM sélection) + statut/verdict persistés (VM verdict).
    lblTitreContexte.textProperty().bind(selectionVm.titreContexteProperty());
    lblPlageHoraire.textProperty().bind(selectionVm.plageHoraireProperty());
    lblVolumetrie.textProperty().bind(selectionVm.volumetrieProperty());
    lblVerdictActuel
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () -> libelleVerdict(verdictVm.verdictActuelProperty().get()),
                verdictVm.verdictActuelProperty()));
    lblStatut
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () -> libelleStatut(verdictVm.statutProperty().get()), verdictVm.statutProperty()));

    // Pré-check 3 feux (R13, consultatif et jamais bloquant).
    lierFeu(feuCouverture, "Couverture horaire", verdictVm.feuCouvertureProperty());
    lierFeu(feuNombre, "Nombre de fichiers", verdictVm.feuNombreProperty());
    lierFeu(feuRenommage, "Cohérence du renommage", verdictVm.feuRenommageProperty());
    lblAnomalie.visibleProperty().bind(verdictVm.preCheckAnomalieProperty());
    lblAnomalie.managedProperty().bind(verdictVm.preCheckAnomalieProperty());

    // Liste de la sélection + progression d'écoute.
    lblListeTitre
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () -> "📋 Sélection d'écoute (" + selectionVm.lignes().size() + " séquences)",
                selectionVm.lignes()));
    tableSequences.setItems(selectionVm.lignes());
    colPosition.setCellValueFactory(
        c -> new ReadOnlyStringWrapper(Integer.toString(c.getValue().position() + 1)));
    colFichier.setCellValueFactory(
        c -> new ReadOnlyStringWrapper(c.getValue().sequence().nomFichier()));
    colDuree.setCellValueFactory(
        c -> new ReadOnlyStringWrapper(formatDuree(c.getValue().sequence().dureeSecondes())));
    colEcoute.setCellValueFactory(
        c -> new ReadOnlyStringWrapper(c.getValue().ecoutee() ? "✓" : "○"));
    tableSequences
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, ancien, nouveau) -> selectionVm.selectionner(nouveau));
    barreProgression.progressProperty().bind(selectionVm.progressionProperty());
    lblProgression.textProperty().bind(selectionVm.progressionTexteProperty());

    // Détail de la séquence courante.
    lblSeqNumero
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () -> numeroSequence(selectionVm.sequenceCouranteProperty().get()),
                selectionVm.sequenceCouranteProperty()));
    lblSeqMeta
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () -> metaSequence(selectionVm.sequenceCouranteProperty().get()),
                selectionVm.sequenceCouranteProperty()));

    // Vue audio (composant fourni) : la source suit la séquence courante ; le marquage écouté (R10)
    // se déclenche au début de la lecture ; le clip est libéré quand la vue quitte la scène.
    audioView.audioFileProperty().bind(selectionVm.cheminSequenceCouranteProperty());
    audioView
        .playingProperty()
        .addListener(
            (obs, avant, lecture) -> {
              if (Boolean.TRUE.equals(lecture)) {
                selectionVm.marquerCouranteEcoutee();
              }
            });
    audioView
        .sceneProperty()
        .addListener(
            (obs, avant, scene) -> {
              if (scene == null) {
                audioView.dispose();
              }
            });

    // Verdict différé : surbrillance du bouton choisi + liaison du commentaire.
    marquerChoisi(boutonOk, Verdict.OK);
    marquerChoisi(boutonDouteux, Verdict.DOUTEUX);
    marquerChoisi(boutonAJeter, Verdict.A_JETER);
    champCommentaire.textProperty().bindBidirectional(verdictVm.commentaireProperty());

    lblAvertissement.textProperty().bind(verdictVm.avertissementAJeterProperty());
    lblAvertissement.visibleProperty().bind(verdictVm.avertissementAJeterProperty().isNotEmpty());
    lblAvertissement.managedProperty().bind(verdictVm.avertissementAJeterProperty().isNotEmpty());
    lblMessage.textProperty().bind(verdictVm.messageProperty());
    lblMessage.visibleProperty().bind(verdictVm.messageProperty().isNotEmpty());
    lblMessage.managedProperty().bind(verdictVm.messageProperty().isNotEmpty());

    boutonEnregistrer.disableProperty().bind(verdictVm.peutEnregistrer().not());
  }

  /// Ouvre l'écran sur le passage `idPassage` : les deux VM se synchronisent sur le même passage.
  /// Appelée par [NavigationQualification] après le chargement du FXML.
  public void ouvrirSur(Long idPassage) {
    verdictVm.ouvrirSur(idPassage);
    selectionVm.ouvrirSur(idPassage);
  }

  @FXML
  private void choisirOk() {
    verdictVm.choisirVerdict(Verdict.OK);
  }

  @FXML
  private void choisirDouteux() {
    verdictVm.choisirVerdict(Verdict.DOUTEUX);
  }

  @FXML
  private void choisirAJeter() {
    verdictVm.choisirVerdict(Verdict.A_JETER);
  }

  @FXML
  private void enregistrer() {
    verdictVm.enregistrer();
  }

  @FXML
  private void regenerer() {
    selectionVm.regenerer();
  }

  private void marquerChoisi(Button bouton, Verdict verdict) {
    verdictVm
        .verdictChoisiProperty()
        .addListener((obs, ancien, nouveau) -> appliquerChoix(bouton, nouveau == verdict));
    appliquerChoix(bouton, verdictVm.verdictChoisiProperty().get() == verdict);
  }

  private static void appliquerChoix(Button bouton, boolean choisi) {
    bouton.getStyleClass().remove("verdict-choisi");
    if (choisi) {
      bouton.getStyleClass().add("verdict-choisi");
    }
  }

  private static void lierFeu(
      Label feu, String libelle, ReadOnlyObjectProperty<PreCheckNuit.Feu> couleur) {
    feu.setText(libelle);
    appliquerFeu(feu, couleur.get());
    couleur.addListener((obs, ancien, nouveau) -> appliquerFeu(feu, nouveau));
  }

  private static void appliquerFeu(Label feu, PreCheckNuit.Feu valeur) {
    feu.getStyleClass().removeAll("feu-vert", "feu-orange", "feu-rouge");
    if (valeur != null) {
      feu.getStyleClass().add("feu-" + valeur.name().toLowerCase(Locale.ROOT));
    }
  }

  private static String libelleVerdict(Verdict verdict) {
    return verdict == null || verdict == Verdict.A_VERIFIER ? "non saisi" : verdict.libelle();
  }

  private static String libelleStatut(StatutWorkflow statut) {
    return statut == null ? "" : statut.libelle();
  }

  private static String numeroSequence(SequenceEnSelection ligne) {
    return ligne == null ? "Aucune séquence sélectionnée" : "N° " + (ligne.position() + 1);
  }

  private static String metaSequence(SequenceEnSelection ligne) {
    if (ligne == null) {
      return "Sélectionnez une séquence dans la liste pour l'écouter.";
    }
    return "Fichier "
        + ligne.sequence().nomFichier()
        + " · durée "
        + formatDuree(ligne.sequence().dureeSecondes())
        + (ligne.ecoutee() ? " · ✓ écoutée" : " · ○ non écoutée");
  }

  private static String formatDuree(double secondes) {
    return String.format(Locale.FRANCE, "%.1f s", secondes);
  }
}

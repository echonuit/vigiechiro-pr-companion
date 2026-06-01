package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.viewmodel.EtapeWorkflow;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

/// Controller de l'écran pivot **M-Passage** (`Passage.fxml`) : fil d'Ariane (affichage), bandeau
/// d'identité, stepper de statut et onglet « Vue d'ensemble » (stats + actions rapides).
///
/// Pur câblage (patron CM4) : lie les contrôles aux propriétés du [PassageViewModel]. Les boutons
/// « Vérifier » et « Diagnostic » ouvrent M-Qualification et M-Diagnostic via les contrats socle
/// [OuvrirVerification] et [OuvrirDiagnostic] (sans dépendre des features `qualification` ni
/// `diagnostic`). Aucun accès base de données ni logique métier ici (règle ArchUnit
/// `view_sans_jdbc`). La validation Tadarida : tranche suivante.
public class PassageController {

  private final PassageViewModel viewModel;
  private final OuvrirVerification ouvrirVerification;
  private final OuvrirDiagnostic ouvrirDiagnostic;
  private Long idPassage;

  @FXML private BorderPane racine;
  @FXML private Label lblFilAriane;
  @FXML private Label lblTitre;
  @FXML private Label lblPlageHoraire;
  @FXML private Label lblEnregistreur;
  @FXML private Label lblStatut;
  @FXML private Label lblVerdict;
  @FXML private HBox stepper;
  @FXML private Label lblMessage;
  @FXML private Label lblVolBruts;
  @FXML private Label lblVolTransformes;
  @FXML private Label lblDureeAudible;
  @FXML private Label lblNbSequences;
  @FXML private Button boutonVerifier;
  @FXML private Button boutonValidation;
  @FXML private Label lblIndiceAction;

  @Inject
  public PassageController(
      PassageViewModel viewModel,
      OuvrirVerification ouvrirVerification,
      OuvrirDiagnostic ouvrirDiagnostic) {
    this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    this.ouvrirVerification = Objects.requireNonNull(ouvrirVerification, "ouvrirVerification");
    this.ouvrirDiagnostic = Objects.requireNonNull(ouvrirDiagnostic, "ouvrirDiagnostic");
  }

  @FXML
  private void initialize() {
    lblTitre.textProperty().bind(viewModel.titreContexteProperty());
    lblFilAriane
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () -> {
                  String titre = viewModel.titreContexteProperty().get();
                  return titre.isEmpty() ? "" : "‹ Mes sites › " + titre;
                },
                viewModel.titreContexteProperty()));
    lblPlageHoraire.textProperty().bind(viewModel.plageHoraireProperty());
    lblEnregistreur.textProperty().bind(viewModel.enregistreurProperty());
    lblStatut
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () -> libelleStatut(viewModel.statutProperty().get()), viewModel.statutProperty()));
    lblVerdict
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () -> libelleVerdict(viewModel.verdictProperty().get()),
                viewModel.verdictProperty()));

    viewModel.etapes().addListener((ListChangeListener<EtapeWorkflow>) changement -> majStepper());
    majStepper();

    lblMessage.textProperty().bind(viewModel.messageProperty());
    var messagePresent = viewModel.messageProperty().isNotEmpty();
    lblMessage.visibleProperty().bind(messagePresent);
    lblMessage.managedProperty().bind(messagePresent);

    // Onglet « Vue d'ensemble » : statistiques + actions rapides.
    lblVolBruts.textProperty().bind(viewModel.volumeBrutsProperty());
    lblVolTransformes.textProperty().bind(viewModel.volumeTransformesProperty());
    lblDureeAudible.textProperty().bind(viewModel.dureeAudibleProperty());
    lblNbSequences.textProperty().bind(viewModel.nombreSequencesProperty().asString());

    boutonVerifier.disableProperty().bind(viewModel.verificationDisponibleProperty().not());
    boutonValidation.disableProperty().bind(viewModel.validationVerrouilleeProperty());
    lblIndiceAction
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () ->
                    viewModel.verificationDisponibleProperty().get()
                        ? ""
                        : "🔒 La vérification sera possible une fois la nuit transformée.",
                viewModel.verificationDisponibleProperty()));
  }

  /// Ouvre l'écran sur le passage `idPassage`, avec le contexte site fourni par la navigation.
  /// Appelée par [NavigationPassage] après le chargement du FXML.
  public void ouvrirSur(Long idPassage, ContexteSite contexte) {
    this.idPassage = idPassage;
    viewModel.ouvrirSur(idPassage, contexte);
  }

  /// « Vérifier l'enregistrement » : ouvre M-Qualification sur ce passage via le contrat socle
  /// [OuvrirVerification] (la feature `qualification` en fournit l'implémentation).
  @FXML
  private void verifier() {
    // Le bouton n'est actif qu'après ouvrirSur (verificationDisponible) : idPassage est défini.
    ouvrirVerification.ouvrir(idPassage);
  }

  /// « Diagnostic matériel » : ouvre M-Diagnostic sur ce passage via le contrat socle
  /// [OuvrirDiagnostic] (la feature `diagnostic` en fournit l'implémentation). Toujours disponible
  // :
  /// le relevé climatique et le journal existent dès l'import de la nuit.
  @FXML
  private void diagnostiquer() {
    ouvrirDiagnostic.ouvrir(idPassage);
  }

  private void majStepper() {
    stepper.getChildren().clear();
    for (EtapeWorkflow etape : viewModel.etapes()) {
      Label puce = new Label(etape.statut().libelle());
      puce.getStyleClass().addAll("etape", "etape-" + etape.etat().name().toLowerCase(Locale.ROOT));
      stepper.getChildren().add(puce);
    }
  }

  private static String libelleStatut(StatutWorkflow statut) {
    return statut == null ? "" : statut.libelle();
  }

  private static String libelleVerdict(Verdict verdict) {
    return verdict == null || verdict == Verdict.A_VERIFIER ? "non saisi" : verdict.libelle();
  }
}

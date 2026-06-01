package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.viewmodel.EtapeWorkflow;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

/// Controller de l'écran pivot **M-Passage** (`Passage.fxml`), tranche « coquille » : fil d'Ariane
/// (affichage), bandeau d'identité et stepper de statut.
///
/// Pur câblage (patron CM4) : lie les contrôles aux propriétés du [PassageViewModel]. Aucun accès
/// base de données ni logique métier ici (règle ArchUnit `view_sans_jdbc`). Les onglets, les cartes
/// d'action et le câblage « Vérifier » → M-Qualification (contrat socle
/// `OuvrirVerification`) arrivent dans la tranche suivante.
public class PassageController {

  private final PassageViewModel viewModel;

  @FXML private BorderPane racine;
  @FXML private Label lblFilAriane;
  @FXML private Label lblTitre;
  @FXML private Label lblPlageHoraire;
  @FXML private Label lblEnregistreur;
  @FXML private Label lblStatut;
  @FXML private Label lblVerdict;
  @FXML private HBox stepper;
  @FXML private Label lblMessage;

  @Inject
  public PassageController(PassageViewModel viewModel) {
    this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
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
  }

  /// Ouvre l'écran sur le passage `idPassage`, avec le contexte site fourni par la navigation.
  /// Appelée par [NavigationPassage] après le chargement du FXML.
  public void ouvrirSur(Long idPassage, ContexteSite contexte) {
    viewModel.ouvrirSur(idPassage, contexte);
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

package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.viewmodel.EtapeWorkflow;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

/// Controller de l'écran pivot **M-Passage** (`Passage.fxml`) : fil d'Ariane (affichage), bandeau
/// d'identité, stepper de statut et onglet « Vue d'ensemble » (stats + actions rapides).
///
/// Pur câblage (patron CM4) : lie les contrôles aux propriétés du [PassageViewModel]. Les boutons
/// « Vérifier », « Diagnostic », « Préparer le dépôt » et « Validation Tadarida » ouvrent
/// M-Qualification, M-Diagnostic, M-Lot et M-Vision-Tadarida via les contrats socle
/// [OuvrirVerification], [OuvrirDiagnostic], [OuvrirLot] et [OuvrirValidation] (sans dépendre des
/// features `qualification`, `diagnostic`, `lot` ni `validation`). Aucun accès base de données ni
/// logique métier ici (règle ArchUnit `view_sans_jdbc`).
public class PassageController {

    private final PassageViewModel viewModel;
    private final OuvrirVerification ouvrirVerification;
    private final OuvrirDiagnostic ouvrirDiagnostic;
    private final OuvrirValidation ouvrirValidation;
    private final OuvrirLot ouvrirLot;
    private final NavigationPassage navigation;
    private Long idPassage;
    private ContexteSite contexte;

    @FXML
    private BorderPane racine;

    @FXML
    private Label lblFilAriane;

    @FXML
    private Label lblTitre;

    @FXML
    private Label lblPlageHoraire;

    @FXML
    private Label lblEnregistreur;

    @FXML
    private Label lblStatut;

    @FXML
    private Label lblVerdict;

    @FXML
    private HBox stepper;

    @FXML
    private Label lblMessage;

    @FXML
    private Label lblVolBruts;

    @FXML
    private Label lblVolTransformes;

    @FXML
    private Label lblDureeAudible;

    @FXML
    private Label lblNbSequences;

    @FXML
    private Button boutonVerifier;

    @FXML
    private Button boutonValidation;

    @FXML
    private Button boutonOuvrirValidation;

    @FXML
    private Button boutonDepot;

    @FXML
    private Label lblIndiceAction;

    @FXML
    private Label lblValidation;

    @Inject
    public PassageController(
            PassageViewModel viewModel,
            OuvrirVerification ouvrirVerification,
            OuvrirDiagnostic ouvrirDiagnostic,
            OuvrirValidation ouvrirValidation,
            OuvrirLot ouvrirLot,
            NavigationPassage navigation) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirVerification = Objects.requireNonNull(ouvrirVerification, "ouvrirVerification");
        this.ouvrirDiagnostic = Objects.requireNonNull(ouvrirDiagnostic, "ouvrirDiagnostic");
        this.ouvrirValidation = Objects.requireNonNull(ouvrirValidation, "ouvrirValidation");
        this.ouvrirLot = Objects.requireNonNull(ouvrirLot, "ouvrirLot");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    @FXML
    private void initialize() {
        lblTitre.textProperty().bind(viewModel.titreContexteProperty());
        lblFilAriane
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> {
                            String titre = viewModel.titreContexteProperty().get();
                            return titre.isEmpty() ? "" : "‹ Mes sites › " + titre;
                        },
                        viewModel.titreContexteProperty()));
        lblPlageHoraire.textProperty().bind(viewModel.plageHoraireProperty());
        lblEnregistreur.textProperty().bind(viewModel.enregistreurProperty());
        lblStatut
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> libelleStatut(viewModel.statutProperty().get()), viewModel.statutProperty()));
        lblVerdict
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> libelleVerdict(viewModel.verdictProperty().get()), viewModel.verdictProperty()));

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

        boutonVerifier
                .disableProperty()
                .bind(viewModel.verificationDisponibleProperty().not());
        boutonValidation.disableProperty().bind(viewModel.validationVerrouilleeProperty());
        boutonOuvrirValidation.disableProperty().bind(viewModel.validationVerrouilleeProperty());
        boutonDepot.disableProperty().bind(viewModel.depotDisponibleProperty().not());
        lblIndiceAction
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> viewModel.verificationDisponibleProperty().get()
                                ? ""
                                : "🔒 La vérification sera possible une fois la nuit transformée.",
                        viewModel.verificationDisponibleProperty()));

        lblValidation
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> viewModel.validationVerrouilleeProperty().get()
                                ? "🔒 La validation Tadarida sera disponible une fois le passage déposé."
                                : "✅ Passage déposé : la validation des identifications Tadarida"
                                        + " (M-Vision-Tadarida) s'ouvrira depuis cet onglet.",
                        viewModel.validationVerrouilleeProperty()));
    }

    /// Ouvre l'écran sur le passage `idPassage`, avec le contexte site fourni par la navigation.
    /// Appelée par [NavigationPassage] après le chargement du FXML.
    public void ouvrirSur(Long idPassage, ContexteSite contexte) {
        this.idPassage = idPassage;
        this.contexte = contexte;
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
    /// [OuvrirDiagnostic] (implémenté par la feature `diagnostic`). Toujours disponible : le
    /// relevé climatique et le journal existent dès l'import de la nuit.
    @FXML
    private void diagnostiquer() {
        ouvrirDiagnostic.ouvrir(idPassage);
    }

    /// « Validation Tadarida » : ouvre M-Vision-Tadarida sur ce passage via le contrat socle
    /// [OuvrirValidation] (implémenté par la feature `validation`). Le bouton n'est actif qu'une fois
    /// le passage déposé (validationVerrouillee) : idPassage est alors défini.
    @FXML
    private void validerTadarida() {
        ouvrirValidation.ouvrir(idPassage);
    }

    /// « Préparer le dépôt » : ouvre M-Lot sur ce passage via le contrat socle [OuvrirLot]
    /// (implémenté par la feature `lot`). Le bouton n'est actif que dans la phase de dépôt (Vérifié
    /// ou Prêt à déposer) : idPassage est alors défini.
    @FXML
    private void preparerDepot() {
        ouvrirLot.ouvrir(idPassage);
    }

    /// « Supprimer » : après confirmation, supprime le passage (et sa nuit, par cascade) puis revient
    /// à l'accueil. Un passage déposé est refusé par le service ([RegleMetierException]) ; l'erreur
    /// est alors présentée à l'utilisateur sans quitter l'écran.
    @FXML
    private void supprimer() {
        if (!confirmer("Supprimer définitivement ce passage et toute sa nuit (séquences, relevés) ?")) {
            return;
        }
        try {
            viewModel.supprimer();
            navigation.ouvrirAccueil();
        } catch (RegleMetierException refus) {
            alerteErreur(refus.getMessage());
        }
    }

    /// « Modifier rattachement » : ouvre la modale E2.S8 (année + n° de passage) en fenêtre modale.
    /// Après une modification réussie, M-Passage est rouvert sur le passage pour refléter le nouveau
    /// quadruplet (titre, fil d'Ariane).
    @FXML
    private void modifierRattachement() {
        navigation.ouvrirModaleRattachement(
                racine.getScene().getWindow(),
                idPassage,
                contexte.numeroCarre(),
                contexte.codePoint(),
                () -> viewModel.ouvrirSur(idPassage, contexte));
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

    private boolean confirmer(String message) {
        Alert alerte = new Alert(AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        return alerte.showAndWait().filter(bouton -> bouton == ButtonType.OK).isPresent();
    }

    private void alerteErreur(String message) {
        Alert alerte = new Alert(AlertType.WARNING, message, ButtonType.OK);
        alerte.setHeaderText("Suppression impossible");
        alerte.showAndWait();
    }
}

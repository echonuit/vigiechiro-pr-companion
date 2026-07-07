package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.CompteurValidations;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.model.MeteoReleve;
import fr.univ_amu.iut.passage.model.PositionMicro;
import fr.univ_amu.iut.passage.viewmodel.ActionRecommandee;
import fr.univ_amu.iut.passage.viewmodel.EtapeWorkflow;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

/// Controller de l'écran pivot **M-Passage** (`Passage.fxml`), en « hub à plat » : bandeau d'identité,
/// stepper de statut, résumé de la nuit (stats) et cartes d'actions « avancer ». Le retour et le fil
/// d'Ariane sont portés par le chrome (`commun`) ; cet écran ne porte donc plus de fil interne ni
/// d'onglets-lanceurs. Il fournit toutefois son [#emplacement()] (contrat [EmplacementNavigation]) que
/// le chrome rend dans le fil.
///
/// Pur câblage (patron CM4) : lie les contrôles aux propriétés du [PassageViewModel]. Les boutons
/// « Vérifier », « Diagnostic », « Préparer le dépôt » et « Validation Tadarida » ouvrent
/// M-Qualification, M-Diagnostic, M-Lot et M-Vision-Tadarida via les contrats socle
/// [OuvrirVerification], [OuvrirDiagnostic], [OuvrirLot] et [OuvrirValidation] (sans dépendre des
/// features `qualification`, `diagnostic`, `lot` ni `validation`). Aucun accès base de données ni
/// logique métier ici (règle ArchUnit `view_sans_jdbc`).
public class PassageController implements EmplacementNavigation, RafraichirAuRetour {

    /// Pseudo-classe CSS portant le liseré « prochaine action recommandée » sur la carte concernée.
    private static final PseudoClass RECOMMANDEE = PseudoClass.getPseudoClass("recommandee");

    private final PassageViewModel viewModel;
    private final OuvrirVerification ouvrirVerification;
    private final OuvrirDiagnostic ouvrirDiagnostic;
    private final OuvrirValidation ouvrirValidation;
    private final OuvrirLot ouvrirLot;
    private final NavigationPassage navigation;
    private final OuvrirSite ouvrirSite;
    private final OuvrirMultisite ouvrirMultisite;
    private final CompteurValidations compteurValidations;
    private Long idPassage;
    private ContexteSite contexte;

    @FXML
    private BorderPane racine;

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
    private Button boutonDepot;

    @FXML
    private Button boutonAnnulerDepot;

    @FXML
    private Button boutonPurger;

    @FXML
    private Label lblIndiceAction;

    @FXML
    private TextField champTemperature;

    @FXML
    private TextField champTemperatureFin;

    @FXML
    private TextField champVent;

    @FXML
    private TextField champCouverture;

    @FXML
    private Button boutonMeteo;

    @FXML
    private Button boutonRecupererMeteo;

    @FXML
    private ComboBox<PositionMicro> champPosition;

    @FXML
    private TextField champHauteur;

    @FXML
    private TextField champTypeMicro;

    @FXML
    private Button boutonMateriel;

    @Inject
    public PassageController(
            PassageViewModel viewModel,
            OuvrirVerification ouvrirVerification,
            OuvrirDiagnostic ouvrirDiagnostic,
            OuvrirValidation ouvrirValidation,
            OuvrirLot ouvrirLot,
            NavigationPassage navigation,
            OuvrirSite ouvrirSite,
            OuvrirMultisite ouvrirMultisite,
            CompteurValidations compteurValidations) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirVerification = Objects.requireNonNull(ouvrirVerification, "ouvrirVerification");
        this.ouvrirDiagnostic = Objects.requireNonNull(ouvrirDiagnostic, "ouvrirDiagnostic");
        this.ouvrirValidation = Objects.requireNonNull(ouvrirValidation, "ouvrirValidation");
        this.ouvrirLot = Objects.requireNonNull(ouvrirLot, "ouvrirLot");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirMultisite = Objects.requireNonNull(ouvrirMultisite, "ouvrirMultisite");
        this.compteurValidations = Objects.requireNonNull(compteurValidations, "compteurValidations");
    }

    @FXML
    private void initialize() {
        lblTitre.textProperty().bind(viewModel.titreContexteProperty());
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

        // Résumé de la nuit (stats) + cartes d'actions.
        lblVolBruts.textProperty().bind(viewModel.volumeBrutsProperty());
        lblVolTransformes.textProperty().bind(viewModel.volumeTransformesProperty());
        lblDureeAudible.textProperty().bind(viewModel.dureeAudibleProperty());
        lblNbSequences.textProperty().bind(viewModel.nombreSequencesProperty().asString());

        // Conditions météo (#106 étendu) : saisie bidirectionnelle des quatre grandeurs.
        champTemperature.textProperty().bindBidirectional(viewModel.conditions().temperatureSaisieProperty());
        champTemperatureFin
                .textProperty()
                .bindBidirectional(viewModel.conditions().temperatureFinSaisieProperty());
        champVent.textProperty().bindBidirectional(viewModel.conditions().ventSaisieProperty());
        champCouverture.textProperty().bindBidirectional(viewModel.conditions().couvertureNuageuseSaisieProperty());

        // Matériel du micro : position (liste sol/canopée, avec entrée vide « non renseigné »), hauteur,
        // type. Le convertisseur affiche le libellé lisible de la position.
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
        champPosition.valueProperty().bindBidirectional(viewModel.conditions().positionSaisieProperty());
        champHauteur.textProperty().bindBidirectional(viewModel.conditions().hauteurSaisieProperty());
        champTypeMicro.textProperty().bindBidirectional(viewModel.conditions().typeMicroSaisieProperty());

        boutonVerifier
                .disableProperty()
                .bind(viewModel.verificationDisponibleProperty().not());
        boutonValidation.disableProperty().bind(viewModel.validationVerrouilleeProperty());
        boutonDepot.disableProperty().bind(viewModel.depotDisponibleProperty().not());
        // « Annuler le dépôt » n'a de sens que sur un passage déposé : le bouton n'apparaît (et n'occupe
        // de place) que dans ce cas, au lieu de rester grisé en permanence dans la barre d'actions.
        boutonAnnulerDepot.visibleProperty().bind(viewModel.annulationDepotDisponibleProperty());
        boutonAnnulerDepot.managedProperty().bind(viewModel.annulationDepotDisponibleProperty());
        // « Purger les originaux » n'apparaît que si des originaux sont encore stockés (volume bruts > 0).
        boutonPurger.visibleProperty().bind(viewModel.purgeDisponibleProperty());
        boutonPurger.managedProperty().bind(viewModel.purgeDisponibleProperty());
        lblIndiceAction
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> viewModel.verificationDisponibleProperty().get()
                                ? ""
                                : "🔒 La vérification sera possible une fois la nuit transformée.",
                        viewModel.verificationDisponibleProperty()));

        // Mise en avant de la « prochaine action » : le liseré recommandé se déplace selon le statut
        // (Vérifier → Préparer le dépôt → Validation Tadarida), au lieu de rester figé sur Vérifier.
        viewModel.actionRecommandeeProperty().addListener((obs, ancienne, nouvelle) -> majActionRecommandee(nouvelle));
        majActionRecommandee(viewModel.actionRecommandeeProperty().get());
    }

    /// Applique le liseré « recommandée » à la seule carte correspondant à la prochaine étape du
    /// workflow (les autres le perdent). Diagnostic n'est jamais « recommandé » : c'est une action
    /// transverse, disponible mais hors progression linéaire.
    private void majActionRecommandee(ActionRecommandee action) {
        boutonVerifier.pseudoClassStateChanged(RECOMMANDEE, action == ActionRecommandee.VERIFIER);
        boutonDepot.pseudoClassStateChanged(RECOMMANDEE, action == ActionRecommandee.DEPOSER);
        boutonValidation.pseudoClassStateChanged(RECOMMANDEE, action == ActionRecommandee.VALIDER);
    }

    /// Ouvre l'écran sur le passage `idPassage`, avec le contexte site fourni par la navigation.
    /// Appelée par [NavigationPassage] après le chargement du FXML.
    public void ouvrirSur(Long idPassage, ContexteSite contexte) {
        this.idPassage = idPassage;
        this.contexte = contexte;
        viewModel.ouvrirSur(idPassage, contexte);
    }

    /// Rechargé par le [fr.univ_amu.iut.commun.view.Navigateur] quand on **revient** sur ce passage
    /// (← Retour ou fil d'Ariane) : une sous-activité (M-Qualification, M-Vision-Tadarida, M-Lot…) a pu
    /// faire avancer le statut du workflow pendant qu'il était masqué. On rejoue [#ouvrirSur] avec le
    /// contexte courant pour réafficher l'état réel (statut, verdict…), au lieu d'un état périmé.
    @Override
    public void rafraichirAuRetour() {
        if (idPassage != null) {
            viewModel.ouvrirSur(idPassage, contexte);
        }
    }

    /// Libellé identifiant ce passage pour le fil d'Ariane (« Détails du passage N° X »), valide une
    /// fois [#ouvrirSur] appelée. Utilisé par [NavigationPassage] au moment d'empiler l'écran.
    public String libelleFil() {
        return EmplacementPassage.libellePassage(viewModel.getNumeroPassage());
    }

    /// Emplacement hiérarchique pour le fil d'Ariane : `Mes sites › Carré N › Détails du passage N° X`
    /// (le chrome préfixe « Accueil »). Le carré vient du [ContexteSite], donc le passage affiche **son
    /// site quel que soit le chemin** d'arrivée (depuis M-Sites comme depuis M-Multisite). Les ancêtres
    /// site sont cliquables via le contrat socle [OuvrirSite] (fabrique partagée [EmplacementPassage]) ;
    /// le passage lui-même est le segment courant (non cliquable).
    @Override
    public List<Lieu> emplacement() {
        List<Lieu> ancetres = EmplacementPassage.ancetresSite(contexte, ouvrirSite);
        if (ancetres.isEmpty()) {
            return List.of(Lieu.courant(libelleFil()));
        }
        List<Lieu> fil = new ArrayList<>(ancetres);
        fil.add(Lieu.courant(libelleFil()));
        return List.copyOf(fil);
    }

    /// « Vérifier l'enregistrement » : ouvre M-Qualification sur ce passage via le contrat socle
    /// [OuvrirVerification] (la feature `qualification` en fournit l'implémentation).
    @FXML
    private void verifier() {
        // Le bouton n'est actif qu'après ouvrirSur (verificationDisponible) : idPassage est défini.
        ouvrirVerification.ouvrir(contextePassage());
    }

    /// « Diagnostic matériel » : ouvre M-Diagnostic sur ce passage via le contrat socle
    /// [OuvrirDiagnostic] (implémenté par la feature `diagnostic`). Toujours disponible : le
    /// relevé climatique et le journal existent dès l'import de la nuit.
    @FXML
    private void diagnostiquer() {
        ouvrirDiagnostic.ouvrir(contextePassage());
    }

    /// « Validation Tadarida » : ouvre M-Vision-Tadarida sur ce passage via le contrat socle
    /// [OuvrirValidation] (implémenté par la feature `validation`). Le bouton n'est actif qu'une fois
    /// le passage déposé (validationVerrouillee) : idPassage est alors défini.
    @FXML
    private void validerTadarida() {
        ouvrirValidation.ouvrir(contextePassage());
    }

    /// « Préparer le dépôt » : ouvre M-Lot sur ce passage via le contrat socle [OuvrirLot]
    /// (implémenté par la feature `lot`). Le bouton n'est actif que dans la phase de dépôt (Vérifié
    /// ou Prêt à déposer) : idPassage est alors défini.
    @FXML
    private void preparerDepot() {
        ouvrirLot.ouvrir(contextePassage());
    }

    /// Contexte de navigation transmis aux écrans enfants (identité + n° + site), pour qu'ils
    /// reconstruisent le même fil d'Ariane `Mes sites › Carré N › Détails du passage N° X › <écran>`,
    /// quelle que soit la route d'arrivée (les boutons ne sont actifs qu'après [#ouvrirSur]).
    private ContextePassage contextePassage() {
        return new ContextePassage(idPassage, viewModel.getNumeroPassage(), contexte);
    }

    /// « Supprimer » : après confirmation, supprime le passage (et sa nuit, par cascade) puis revient
    /// à l'accueil. Un passage déposé est refusé par le service ([RegleMetierException]) ; l'erreur
    /// est alors présentée à l'utilisateur sans quitter l'écran.
    @FXML
    private void supprimer() {
        if (!confirmer("Supprimer définitivement ce passage et toute sa nuit (séquences, relevés) ?"
                + alerteValidationsMenacees())) {
            return;
        }
        try {
            viewModel.supprimer();
            navigation.ouvrirAccueil();
        } catch (RegleMetierException refus) {
            alerteErreur(refus.getMessage());
        }
    }

    /// Complément d'avertissement pour la confirmation de suppression : si le passage porte des validations
    /// observateur (taxon corrigé, référence, commentaire), elles seront **définitivement perdues** avec la
    /// cascade. Chaîne vide sinon (rien à signaler). Contrairement à une ré-importation de CSV, la
    /// suppression ne permet aucune préservation.
    private String alerteValidationsMenacees() {
        int menacees = compteurValidations.menaceesPourPassage(idPassage);
        if (menacees == 0) {
            return "";
        }
        return "\n\n⚠ " + menacees
                + " validation(s) Tadarida (correction, référence, commentaire) seront définitivement perdues.";
    }

    /// « Annuler le dépôt » : après confirmation, ramène le passage de « Déposé » à « Prêt à déposer »
    /// (les validations Tadarida déjà saisies sont **conservées**) puis recharge l'écran pour refléter le
    /// nouveau statut. Un passage non déposé est refusé par le service ([RegleMetierException]) ; l'erreur
    /// est alors présentée sans quitter l'écran.
    @FXML
    private void annulerDepot() {
        if (!confirmer("Annuler le dépôt de ce passage et le ramener à « Prêt à déposer » ? "
                + "Les validations Tadarida déjà saisies sont conservées.")) {
            return;
        }
        try {
            viewModel.annulerDepot();
            viewModel.ouvrirSur(idPassage, contexte);
        } catch (RegleMetierException refus) {
            alerteErreur(refus.getMessage());
        }
    }

    /// « Purger les originaux » : après confirmation, supprime les fichiers `bruts/` de cette nuit pour
    /// récupérer l'espace disque, puis recharge l'écran (le volume bruts tombe à 0). Les séquences
    /// transformées, la validation Tadarida et le dépôt sont **conservés** (ils n'utilisent pas les
    /// originaux). Action réservée aux nuits qui conservent encore des originaux (bouton masqué sinon).
    @FXML
    private void purgerOriginaux() {
        if (!confirmer("Supprimer les enregistrements originaux (bruts) de cette nuit pour libérer de "
                + "l'espace disque ? Les séquences d'écoute, la validation et le dépôt sont conservés ; "
                + "cette suppression est définitive.")) {
            return;
        }
        try {
            viewModel.purgerOriginaux();
            viewModel.ouvrirSur(idPassage, contexte);
        } catch (RuntimeException echec) {
            alerteErreur(echec.getMessage());
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

    /// « Voir sur la carte » : ouvre la vue multi-sites centrée/surlignée sur le carré de ce passage.
    @FXML
    private void voirSurCarte() {
        if (contexte != null) {
            ouvrirMultisite.ouvrirSurCarre(contexte.numeroCarre());
        }
    }

    /// « Enregistrer » le relevé météo (#106 étendu) : délègue au VM (grandeur vide = effacer ; saisie
    /// invalide = message d'erreur, sans modification).
    @FXML
    private void enregistrerMeteo() {
        viewModel.conditions().enregistrerMeteo();
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

    /// « Enregistrer » le matériel du micro (dépôt VigieChiro) : délègue au VM (grandeur vide = effacer ;
    /// hauteur invalide = message d'erreur, sans modification).
    @FXML
    private void enregistrerMateriel() {
        viewModel.conditions().enregistrerMateriel();
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

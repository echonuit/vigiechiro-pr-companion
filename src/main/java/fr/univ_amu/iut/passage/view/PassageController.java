package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.CompteurValidations;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.view.Stepper;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.passage.viewmodel.ActionRecommandee;
import fr.univ_amu.iut.passage.viewmodel.EtapeWorkflow;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/// Controller de l'écran pivot **M-Passage** (`Passage.fxml`), en « hub à plat » : bandeau d'identité,
/// stepper de statut, résumé de la nuit (stats) et cartes d'actions « avancer ». Le retour et le fil
/// d'Ariane sont portés par le chrome (`commun`) ; cet écran ne porte donc plus de fil interne ni
/// d'onglets-lanceurs. Il fournit toutefois son [#emplacement()] (contrat [EmplacementNavigation]) que
/// le chrome rend dans le fil.
///
/// Pur câblage (patron CM4) : lie les contrôles aux propriétés du [PassageViewModel]. Les boutons
/// « Vérifier », « Diagnostic », « Préparer le dépôt » et « Sons & validation » ouvrent
/// M-Qualification, M-Diagnostic, M-Lot et M-Vision-Tadarida via les contrats socle
/// [OuvrirVerification], [OuvrirDiagnostic], [OuvrirLot] et [OuvrirValidation] (sans dépendre des
/// features `qualification`, `diagnostic`, `lot` ni `validation`). Aucun accès base de données ni
/// logique métier ici (règle ArchUnit `view_sans_jdbc`).
public class PassageController implements EmplacementNavigation, RafraichirAuRetour, ResumeStatut {

    /// Pseudo-classe CSS portant le liseré « prochaine action recommandée » sur la carte concernée.
    private static final PseudoClass RECOMMANDEE = PseudoClass.getPseudoClass("recommandee");

    private final PassageViewModel viewModel;
    private final Optional<OuvrirVerification> ouvrirVerification;
    private final Optional<OuvrirDiagnostic> ouvrirDiagnostic;
    private final OuvrirValidation ouvrirValidation;
    private final Optional<OuvrirLot> ouvrirLot;
    private final NavigationPassage navigation;
    private final OuvrirSite ouvrirSite;
    private final OuvrirMultisite ouvrirMultisite;
    private final CompteurValidations compteurValidations;
    private final PortailVigieChiro portail;
    private final OuvreurDeLien ouvreurDeLien;
    private Long idPassage;
    private ContexteSite contexte;

    /// URL de la participation liée sur le portail (#1124), vide tant que le passage n’est pas lié.
    /// Rafraîchie à chaque [#ouvrirSur] / retour : un lien posé entre-temps (import connecté, dépôt)
    /// est vu au retour sur l’écran.
    private final SimpleStringProperty lienParticipation = new SimpleStringProperty(this, "lienParticipation", "");

    /// Contexte du passage (carré / point / numéro), déporté en zone gauche de la barre de statut (#693)
    /// au lieu d'un titre d'en-tête redondant avec le fil d'Ariane.
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    /// Confirmation d'action destructive : porteur partagé injectable (#1013), stub déterministe en test.
    private final ConfirmateurModifiable confirmateur = new ConfirmateurModifiable();

    /// Porteur de confirmation exposé aux tests (#1013) : `confirmateur().definir(stub)`.
    ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }

    @FXML
    private BorderPane racine;

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
    private Label lblDureeEnregistree;

    @FXML
    private Label lblNbSequences;

    @FXML
    private Button boutonVerifier;

    @FXML
    private Button boutonDiagnostic;

    @FXML
    private Button boutonValidation;

    @FXML
    private Button boutonDepot;

    @FXML
    private Button boutonAnnulerDepot;

    @FXML
    private Button boutonPurger;

    @FXML
    private Button boutonSupprimer;

    @FXML
    private Button boutonOuvrirPortail;

    @FXML
    private StackPane enveloppeOuvrirPortail;

    /// Enveloppe (non désactivée) du bouton « Supprimer » : porte le tooltip expliquant le blocage sur un
    /// passage déposé (un Button désactivé n'affiche pas de tooltip). Cf. [IndicateurBlocage].
    @FXML
    private StackPane enveloppeSupprimer;

    @FXML
    private Button boutonRattachement;

    /// Enveloppe (non désactivée) du bouton « Modifier le passage » : porte le tooltip expliquant le
    /// blocage du renommage sur un passage déposé. Cf. [IndicateurBlocage].
    @FXML
    private StackPane enveloppeRattachement;

    @FXML
    private Label lblIndiceAction;

    @Inject
    public PassageController(
            PassageViewModel viewModel,
            Optional<OuvrirVerification> ouvrirVerification,
            Optional<OuvrirDiagnostic> ouvrirDiagnostic,
            OuvrirValidation ouvrirValidation,
            Optional<OuvrirLot> ouvrirLot,
            NavigationPassage navigation,
            OuvrirSite ouvrirSite,
            OuvrirMultisite ouvrirMultisite,
            CompteurValidations compteurValidations,
            PortailVigieChiro portail,
            OuvreurDeLien ouvreurDeLien) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirVerification = Objects.requireNonNull(ouvrirVerification, "ouvrirVerification");
        this.ouvrirDiagnostic = Objects.requireNonNull(ouvrirDiagnostic, "ouvrirDiagnostic");
        this.ouvrirValidation = Objects.requireNonNull(ouvrirValidation, "ouvrirValidation");
        this.ouvrirLot = Objects.requireNonNull(ouvrirLot, "ouvrirLot");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirMultisite = Objects.requireNonNull(ouvrirMultisite, "ouvrirMultisite");
        this.compteurValidations = Objects.requireNonNull(compteurValidations, "compteurValidations");
        this.portail = Objects.requireNonNull(portail, "portail");
        this.ouvreurDeLien = Objects.requireNonNull(ouvreurDeLien, "ouvreurDeLien");
    }

    @Override
    public ReadOnlyObjectProperty<ZonesStatut> zonesStatutProperty() {
        return zonesStatut.getReadOnlyProperty();
    }

    /// Compose les 3 zones de la barre de statut (#1022) : identité (gauche), statut et — s'il est saisi —
    /// verdict (centre), nombre de séquences de la nuit (droite, vide tant que rien n'est chargé).
    private ZonesStatut calculerZonesStatut() {
        String statut = libelleStatut(viewModel.statutProperty().get());
        Verdict verdict = viewModel.verdictProperty().get();
        String centre = verdict == null || verdict == Verdict.A_VERIFIER ? statut : statut + " · " + verdict.libelle();
        int sequences = viewModel.nombreSequencesProperty().get();
        String droite = sequences == 0 ? "" : sequences + " séquence(s)";
        return new ZonesStatut(viewModel.titreContexteProperty().get(), centre, droite);
    }

    @FXML
    private void initialize() {
        // Barre de statut 3 zones (#1022, EPIC #1016) : identité à gauche, statut (+ verdict) au centre,
        // volumétrie de la nuit à droite. Le contexte n'est plus la seule zone renseignée.
        zonesStatut.bind(Bindings.createObjectBinding(
                this::calculerZonesStatut,
                viewModel.titreContexteProperty(),
                viewModel.statutProperty(),
                viewModel.verdictProperty(),
                viewModel.nombreSequencesProperty()));
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
        lblDureeEnregistree.textProperty().bind(viewModel.dureeEnregistreeProperty());
        lblNbSequences.textProperty().bind(viewModel.nombreSequencesProperty().asString());

        boutonVerifier
                .disableProperty()
                .bind(viewModel.verificationDisponibleProperty().not());
        // « Vérifier » n'apparaît que si la feature `qualification` est activée (feature-flag #1087) : quand
        // elle est coupée, le contrat est absent et la carte est retirée plutôt que laissée sans effet.
        boolean verificationActive = ouvrirVerification.isPresent();
        boutonVerifier.setVisible(verificationActive);
        boutonVerifier.setManaged(verificationActive);
        // « Diagnostic » n'apparaît que si la feature `diagnostic` est activée (feature-flag #1087) : quand
        // elle est coupée, le contrat est absent et la carte est retirée plutôt que laissée sans effet.
        boolean diagnosticActif = ouvrirDiagnostic.isPresent();
        boutonDiagnostic.setVisible(diagnosticActif);
        boutonDiagnostic.setManaged(diagnosticActif);
        boutonValidation.disableProperty().bind(viewModel.validationVerrouilleeProperty());
        boutonDepot.disableProperty().bind(viewModel.depotDisponibleProperty().not());
        // « Préparer le dépôt » n'apparaît que si la feature `lot` est activée (feature-flag #1087) : quand
        // elle est coupée, le contrat est absent et la carte est retirée plutôt que laissée sans effet.
        boolean depotActif = ouvrirLot.isPresent();
        boutonDepot.setVisible(depotActif);
        boutonDepot.setManaged(depotActif);
        // Suppression gatée en amont (#789) : un passage déposé n'est pas supprimable (le service le refuse).
        // Plutôt que de laisser l'utilisateur découvrir le refus APRÈS la confirmation, on grise le bouton et
        // on explique le blocage par un tooltip posé sur l'enveloppe (un Button désactivé n'en affiche pas).
        boutonSupprimer
                .disableProperty()
                .bind(viewModel.suppressionPossibleProperty().not());
        // « Voir la participation » (#1124) : actif seulement quand le passage est lié à une participation ;
        // désactivé, il documente ce qui manque (affordance #789) plutôt que de disparaître.
        boutonOuvrirPortail.disableProperty().bind(lienParticipation.isEmpty());
        IndicateurBlocage.expliquer(
                enveloppeOuvrirPortail,
                Bindings.when(lienParticipation.isNotEmpty())
                        .then("Ouvre la participation liée sur le portail Vigie-Chiro (navigateur).")
                        .otherwise("Ce passage n'est pas encore lié à une participation VigieChiro :"
                                + " elle est créée à l'import (connecté) ou au premier dépôt."));
        IndicateurBlocage.expliquer(
                enveloppeSupprimer,
                Bindings.when(viewModel.suppressionPossibleProperty())
                        .then("Supprimer définitivement ce passage et toute sa nuit (séquences, relevés).")
                        .otherwise("Suppression impossible : ce passage est déposé sur VigieChiro."
                                + " Annulez d'abord le dépôt."));
        // Renommage gaté en amont (#789) : un passage déposé (ou en cours de dépôt) n'est plus renommable
        // (le service le refuse, son nom étant l'identité de ses fichiers côté serveur). On grise « Modifier
        // le passage » et on explique le blocage par le tooltip de l'enveloppe.
        boutonRattachement
                .disableProperty()
                .bind(viewModel.renommagePossibleProperty().not());
        IndicateurBlocage.expliquer(
                enveloppeRattachement,
                Bindings.when(viewModel.renommagePossibleProperty())
                        .then("Modifier le rattachement (année, n° de passage) : renomme toute la nuit.")
                        .otherwise("Modification impossible : ce passage est déposé sur VigieChiro."
                                + " Annulez d'abord le dépôt."));
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
        // (Vérifier → Préparer le dépôt → Sons & validation), au lieu de rester figé sur Vérifier.
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
        lienParticipation.set(portail.pageParticipation(idPassage).orElse(""));
    }

    /// Rechargé par le [fr.univ_amu.iut.commun.view.Navigateur] quand on **revient** sur ce passage
    /// (← Retour ou fil d'Ariane) : une sous-activité (M-Qualification, M-Vision-Tadarida, M-Lot…) a pu
    /// faire avancer le statut du workflow pendant qu'il était masqué. On rejoue [#ouvrirSur] avec le
    /// contexte courant pour réafficher l'état réel (statut, verdict…), au lieu d'un état périmé.
    @Override
    public void rafraichirAuRetour() {
        if (idPassage != null) {
            viewModel.ouvrirSur(idPassage, contexte);
            lienParticipation.set(portail.pageParticipation(idPassage).orElse(""));
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
    /// [OuvrirVerification] (implémenté par la feature `qualification`, **désactivable** : la carte
    /// n'apparaît que si la feature est activée, cf. #1087).
    @FXML
    private void verifier() {
        // Le bouton n'est actif qu'après ouvrirSur (verificationDisponible) : idPassage est défini.
        ouvrirVerification.ifPresent(ouvrir -> ouvrir.ouvrir(contextePassage()));
    }

    /// « Diagnostic matériel » : ouvre M-Diagnostic sur ce passage via le contrat socle
    /// [OuvrirDiagnostic] (implémenté par la feature `diagnostic`, **désactivable** : la carte n'apparaît
    /// que si la feature est activée, cf. #1087).
    @FXML
    private void diagnostiquer() {
        ouvrirDiagnostic.ifPresent(ouvrir -> ouvrir.ouvrir(contextePassage()));
    }

    /// « Sons & validation » : ouvre l'écran audio du passage (M-Vision-Tadarida) via le contrat socle
    /// [OuvrirValidation] (implémenté par la feature `validation`). Le bouton n'est actif qu'une fois
    /// le passage déposé (validationVerrouillee) : idPassage est alors défini.
    @FXML
    private void validerTadarida() {
        ouvrirValidation.ouvrir(contextePassage());
    }

    /// « Préparer le dépôt » : ouvre M-Lot sur ce passage via le contrat socle [OuvrirLot]
    /// (implémenté par la feature `lot`, **désactivable** : la carte n'apparaît que si la feature est
    /// activée, cf. #1087). Le bouton n'est actif que dans la phase de dépôt (Vérifié ou Prêt à
    /// déposer) : idPassage est alors défini.
    @FXML
    private void preparerDepot() {
        ouvrirLot.ifPresent(ouvrir -> ouvrir.ouvrir(contextePassage()));
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
        if (!confirmateur.confirmer("Supprimer définitivement ce passage et toute sa nuit (séquences, relevés) ?"
                + alerteValidationsMenacees())) {
            return;
        }
        try {
            viewModel.supprimer();
            navigation.ouvrirAccueil();
        } catch (RegleMetierException refus) {
            alerteErreur("Suppression impossible", refus.getMessage());
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
        if (!confirmateur.confirmer("Annuler le dépôt de ce passage et le ramener à « Prêt à déposer » ? "
                + "Les validations Tadarida déjà saisies sont conservées.")) {
            return;
        }
        try {
            viewModel.annulerDepot();
            viewModel.ouvrirSur(idPassage, contexte);
        } catch (RegleMetierException refus) {
            alerteErreur("Annulation impossible", refus.getMessage());
        }
    }

    /// « Purger les originaux » : après confirmation, supprime les fichiers `bruts/` de cette nuit pour
    /// récupérer l'espace disque, puis recharge l'écran (le volume bruts tombe à 0). Les séquences
    /// transformées, la validation Tadarida et le dépôt sont **conservés** (ils n'utilisent pas les
    /// originaux). Action réservée aux nuits qui conservent encore des originaux (bouton masqué sinon).
    @FXML
    private void purgerOriginaux() {
        if (!confirmateur.confirmer("Supprimer les enregistrements originaux (bruts) de cette nuit pour libérer de "
                + "l'espace disque ? Les séquences d'écoute, la validation et le dépôt sont conservés ; "
                + "cette suppression est définitive.")) {
            return;
        }
        try {
            viewModel.purgerOriginaux();
            viewModel.ouvrirSur(idPassage, contexte);
        } catch (RuntimeException echec) {
            alerteErreur("Purge impossible", echec.getMessage());
        }
    }

    /// « Modifier le passage » : ouvre la modale E2.S8 en fenêtre modale. Elle édite d'un bloc le
    /// rattachement (année + n° de passage) **et** les conditions de dépôt VigieChiro (relevé météo,
    /// matériel du micro). Après une modification réussie, M-Passage est rouvert sur le passage pour
    /// refléter le nouveau quadruplet (titre, fil d'Ariane).
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
    /// Ouvre la participation liée sur le portail Vigie-Chiro (#1124) — vérification visuelle du
    /// rattachement (« la bonne nuit au bon endroit ») avant ou après un dépôt.
    @FXML
    private void ouvrirSurVigieChiro() {
        String lien = lienParticipation.get();
        if (lien != null && !lien.isBlank()) {
            ouvreurDeLien.ouvrir(lien);
        }
    }

    @FXML
    private void voirSurCarte() {
        if (contexte != null) {
            ouvrirMultisite.ouvrirSurCarre(contexte.numeroCarre());
        }
    }

    private void majStepper() {
        Stepper.reconstruire(stepper, viewModel.etapes(), e -> e.statut().libelle(), EtapeWorkflow::etat);
    }

    private static String libelleStatut(StatutWorkflow statut) {
        return statut == null ? "" : statut.libelle();
    }

    private static String libelleVerdict(Verdict verdict) {
        return verdict == null || verdict == Verdict.A_VERIFIER ? "non saisi" : verdict.libelle();
    }

    private void alerteErreur(String entete, String message) {
        Alert alerte = new Alert(AlertType.WARNING, message, ButtonType.OK);
        alerte.setHeaderText(entete);
        alerte.showAndWait();
    }
}

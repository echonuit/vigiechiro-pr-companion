package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.CompteurValidations;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.BandeauRetour;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.Modales;
import fr.univ_amu.iut.commun.view.NotificateurModifiable;
import fr.univ_amu.iut.commun.view.NotificationDialogue;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvrirActivite;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.OuvrirSynthese;
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.view.SelecteurFichierJavaFx;
import fr.univ_amu.iut.commun.view.SelecteurFichierModifiable;
import fr.univ_amu.iut.commun.view.Stepper;
import fr.univ_amu.iut.commun.view.SuitLaRevision;
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
import javafx.scene.control.Button;
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
public class PassageController implements EmplacementNavigation, RafraichirAuRetour, ResumeStatut, SuitLaRevision {

    /// Pseudo-classe CSS portant le liseré « prochaine action recommandée » sur la carte concernée.
    private static final PseudoClass RECOMMANDEE = PseudoClass.getPseudoClass("recommandee");

    private final PassageViewModel viewModel;

    private final Optional<OuvrirVerification> ouvrirVerification;
    private final Optional<OuvrirDiagnostic> ouvrirDiagnostic;
    private final Optional<OuvrirActivite> ouvrirActivite;
    private final OuvrirValidation ouvrirValidation;
    private final Optional<OuvrirLot> ouvrirLot;
    private final NavigationPassage navigation;
    private final OuvrirSite ouvrirSite;
    private final OuvrirMultisite ouvrirMultisite;
    private final CompteurValidations compteurValidations;
    private final ExecuteurTache executeur;
    private final PortailVigieChiro portail;
    private final OuvreurDeLien ouvreurDeLien;

    /// Entrée vers la Synthèse de la nuit (#2351), dépaquetée des appuis comme les trois précédentes.
    private final Optional<OuvrirSynthese> ouvrirSynthese;
    private Long idPassage;
    private ContexteSite contexte;

    /// Voile « … en cours » de l'écran (#1213) : la projection du passage (agrégats de la nuit) se
    /// charge hors du fil JavaFX ([IndicateurOccupation], patron #1014).
    private IndicateurOccupation occupation;

    /// URL de la participation liée sur le portail (#1124), vide tant que le passage n'est pas lié.
    /// Rafraîchie à chaque [#ouvrirSur] / retour : un lien posé entre-temps (import connecté, dépôt)
    /// est vu au retour sur l'écran.
    private final SimpleStringProperty lienParticipation = new SimpleStringProperty(this, "lienParticipation", "");

    /// Contexte du passage (carré / point / numéro), déporté en zone gauche de la barre de statut (#693)
    /// au lieu d'un titre d'en-tête redondant avec le fil d'Ariane.
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    /// Confirmation d'action destructive : porteur partagé injectable (#1013), stub déterministe en test.
    private final ConfirmateurModifiable confirmateur = new ConfirmateurModifiable();

    /// Porteur de compte rendu de l'écran : le pendant du confirmateur pour ce qui est **dit** après
    /// l'action. Exposé aux tests (`notificateur().definir(double)`), sans quoi le `showAndWait` du
    /// dialogue figerait TestFX headless - et le clic sur « Réactiver » resterait à jamais non testé.
    private final NotificateurModifiable notificateur =
            new NotificateurModifiable(new NotificationDialogue(() -> Modales.fenetreDe(this.racine)));

    /// Porteur de confirmation exposé aux tests (#1013) : `confirmateur().definir(stub)`.
    ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }

    /// Porteur de compte rendu exposé aux tests : `notificateur().definir(double)`.
    NotificateurModifiable notificateur() {
        return notificateur;
    }

    /// Désignation d'un dossier : porteur partagé injectable (#1431), double répondant en test. Le seul
    /// geste de l'écran qui en ait besoin est « Réactiver ce passage », et c'est **par lui** qu'il
    /// restait intestable : un `DirectoryChooser` en dur fige un test headless au même titre qu'un
    /// `Alert`, et il ouvre l'action - le test s'arrêtait à la première ligne.
    private final SelecteurFichierModifiable selecteur = new SelecteurFichierModifiable(
            // `this.racine` (et non `racine`) : le champ @FXML est déclaré plus bas, et une référence
            // simple en avant est refusée dans un initialiseur. La fenêtre n'est lue qu'au clic.
            new SelecteurFichierJavaFx(() -> this.racine.getScene().getWindow()));

    /// Porteur de désignation exposé aux tests (#1431) : `selecteur().definir(double)`.
    SelecteurFichierModifiable selecteur() {
        return selecteur;
    }

    @FXML
    private StackPane hoteOccupation;

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
    private HBox bandeauRetour;

    @FXML
    private Label lblRetour;

    @FXML
    private Button btnFermerRetour;

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
    private Button boutonActivite;

    @FXML
    private Button boutonSynthese;

    @FXML
    private Button boutonValidation;

    @FXML
    private StackPane enveloppeValidation;

    @FXML
    private Button boutonDepot;

    /// Enveloppe porteuse du tooltip de « Préparer le dépôt » (#2581) : une carte désactivée n'en affiche
    /// pas. Cf. [IndicateurBlocage].
    @FXML
    private StackPane enveloppeDepot;

    @FXML
    private Button boutonAnnulerDepot;

    /// Enveloppe porteuse du tooltip d'« Annuler le dépôt » (#2771) : un Button désactivé n'en affiche
    /// pas. Cf. [IndicateurBlocage].
    @FXML
    private StackPane enveloppeAnnulerDepot;

    @FXML
    private Button boutonReactiver;

    /// Enveloppe (non désactivée) du bouton « Réactiver » : porte le tooltip expliquant le blocage
    /// (rien à réactiver : audio déjà présent, ou aucune séquence importée), cf. [IndicateurBlocage]
    /// (#1302).
    @FXML
    private StackPane enveloppeReactiver;

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
            Optional<OuvrirActivite> ouvrirActivite,
            OuvrirValidation ouvrirValidation,
            Optional<OuvrirLot> ouvrirLot,
            NavigationPassage navigation,
            OuvrirSite ouvrirSite,
            OuvrirMultisite ouvrirMultisite,
            CompteurValidations compteurValidations,
            AppuisPassage appuis) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirVerification = Objects.requireNonNull(ouvrirVerification, "ouvrirVerification");
        this.ouvrirDiagnostic = Objects.requireNonNull(ouvrirDiagnostic, "ouvrirDiagnostic");
        this.ouvrirActivite = Objects.requireNonNull(ouvrirActivite, "ouvrirActivite");
        this.ouvrirValidation = Objects.requireNonNull(ouvrirValidation, "ouvrirValidation");
        this.ouvrirLot = Objects.requireNonNull(ouvrirLot, "ouvrirLot");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirMultisite = Objects.requireNonNull(ouvrirMultisite, "ouvrirMultisite");
        this.compteurValidations = Objects.requireNonNull(compteurValidations, "compteurValidations");
        Objects.requireNonNull(appuis, "appuis");
        this.executeur = appuis.executeur();
        this.portail = appuis.portail();
        this.ouvreurDeLien = appuis.ouvreurDeLien();
        this.ouvrirSynthese = appuis.ouvrirSynthese();
    }

    @Override
    public ReadOnlyObjectProperty<ZonesStatut> zonesStatutProperty() {
        return zonesStatut.getReadOnlyProperty();
    }

    /// Compose les 3 zones de la barre de statut (#1022) : identité (gauche), statut et, s'il est saisi :
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

        // Bandeau de retour partagé (ADR 0023) : libellé, visibilité, sévérité et croix de fermeture.
        BandeauRetour.installer(
                bandeauRetour, lblRetour, btnFermerRetour, viewModel.retourProperty(), viewModel::effacerRetour);

        // Résumé de la nuit (stats) + cartes d'actions.
        lblVolBruts.textProperty().bind(viewModel.volumeBrutsProperty());
        lblVolTransformes.textProperty().bind(viewModel.volumeTransformesProperty());
        lblDureeEnregistree.textProperty().bind(viewModel.dureeEnregistreeProperty());
        lblNbSequences.textProperty().bind(viewModel.nombreSequencesProperty().asString());

        // Cartes d'action : disponibilité (feature-flag #1087), activation selon l'état, et
        // explication du grisage posée sur l'ENVELOPPE (un Button désactivé n'affiche pas de
        // tooltip, #789). Extrait dans CartesActionPassage (#2745) : cinquante instructions qui
        // répétaient les trois mêmes règles.
        CartesActionPassage.cabler(
                new CartesActionPassage.Cartes(
                        boutonVerifier,
                        boutonDiagnostic,
                        boutonActivite,
                        boutonSynthese,
                        boutonValidation,
                        boutonDepot,
                        enveloppeDepot,
                        enveloppeValidation,
                        boutonSupprimer,
                        enveloppeSupprimer,
                        boutonOuvrirPortail,
                        enveloppeOuvrirPortail,
                        boutonRattachement,
                        enveloppeRattachement,
                        boutonAnnulerDepot,
                        enveloppeAnnulerDepot,
                        boutonReactiver,
                        enveloppeReactiver,
                        lblIndiceAction),
                viewModel,
                new CartesActionPassage.Disponibilites(
                        ouvrirVerification.isPresent(),
                        ouvrirDiagnostic.isPresent(),
                        ouvrirActivite.isPresent(),
                        ouvrirSynthese.isPresent(),
                        ouvrirLot.isPresent()),
                lienParticipation);

        // Mise en avant de la « prochaine action » : le liseré recommandé se déplace selon le statut
        // (Vérifier → Préparer le dépôt → Sons & validation), au lieu de rester figé sur Vérifier.
        viewModel.actionRecommandeeProperty().addListener((obs, ancienne, nouvelle) -> majActionRecommandee(nouvelle));
        majActionRecommandee(viewModel.actionRecommandeeProperty().get());

        occupation = new IndicateurOccupation(hoteOccupation, executeur);
    }

    /// Applique le liseré « recommandée » à la seule carte correspondant à la prochaine étape du
    /// workflow (les autres le perdent). Diagnostic n'est jamais « recommandé » : c'est une action
    /// transverse, disponible mais hors progression linéaire.
    private void majActionRecommandee(ActionRecommandee action) {
        boutonVerifier.pseudoClassStateChanged(RECOMMANDEE, action == ActionRecommandee.VERIFIER);
        boutonDepot.pseudoClassStateChanged(RECOMMANDEE, action == ActionRecommandee.DEPOSER);
        boutonValidation.pseudoClassStateChanged(RECOMMANDEE, action == ActionRecommandee.VALIDER);
        // Une nuit récupérée de Vigie-Chiro (#2581) : ce qui lui manque, c'est son audio. Sans cette
        // ligne, la valeur existerait dans le modèle sans que rien ne s'allume - une recommandation
        // qu'aucune carte ne porte est une recommandation muette.
        boutonReactiver.pseudoClassStateChanged(RECOMMANDEE, action == ActionRecommandee.REACTIVER);
    }

    /// Ouvre l'écran sur le passage `idPassage`, avec le contexte site fourni par la navigation.
    /// Appelée par [NavigationPassage] après le chargement du FXML. La projection (agrégats de la
    /// nuit) se charge **hors du fil JavaFX** sous le voile d'occupation (#1213) ; l'application des
    /// propriétés revient sur le fil, l'erreur rejoint le message de l'écran (#795). Le fil d'Ariane,
    /// empilé avant la fin du chargement, est actualisé une fois le numéro connu.
    public void ouvrirSur(Long idPassage, ContexteSite contexte) {
        memoriser(idPassage, contexte);
        occupation.occuper(
                "Chargement du passage…",
                () -> viewModel.charger(idPassage),
                detail -> {
                    viewModel.appliquer(idPassage, detail, contexte);
                    navigation.actualiserFil(this, libelleFil());
                    lienParticipation.set(portail.pageParticipation(idPassage).orElse(""));
                },
                erreur -> viewModel.signalerErreur(idPassage, erreur));
    }

    /// Mémorise l'identité de l'écran (fil d'Ariane, actions), premier effet d'[#ouvrirSur]. Extrait
    /// pour les tests d'emplacement sans FXML (le chargement, lui, passe par l'occupation, #1213).
    void memoriser(Long idPassage, ContexteSite contexte) {
        this.idPassage = idPassage;
        this.contexte = contexte;
    }

    /// Rechargé par le [fr.univ_amu.iut.commun.view.Navigateur] quand on **revient** sur ce passage
    /// (← Retour ou fil d'Ariane) : une sous-activité (M-Qualification, M-Vision-Tadarida, M-Lot…) a pu
    /// faire avancer le statut du workflow pendant qu'il était masqué. On rejoue [#ouvrirSur] avec le
    /// contexte courant pour réafficher l'état réel (statut, verdict…), au lieu d'un état périmé.
    @Override
    public void rafraichirAuRetour() {
        if (idPassage != null) {
            ouvrirSur(idPassage, contexte);
        }
    }

    /// {@inheritDoc} Ce qui arrive d'AILLEURS pendant qu'on regarde la nuit (#3626) : une
    /// synchronisation qui **hydrate** un squelette rapatrié lui donne enfin ses séquences.
    /// [#rafraichirAuRetour()] couvre l'autre moitié, ce qu'une sous-activité (M-Qualification,
    /// M-Lot...) a fait avancer pendant que l'écran était masqué : des `update` de statut, qui
    /// n'émettent pas.
    @Override
    public void rafraichirDepuisLaDonnee() {
        rafraichirAuRetour();
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

    /// « Activité de la nuit » : ouvre M-Activite sur ce passage via le contrat socle [OuvrirActivite]
    /// (implémenté par la feature `analyse`, **désactivable** : la carte n'apparaît que si la feature
    /// `activite-nuit` est activée, cf. #2352).
    /// « Synthèse de la nuit » : ouvre M-Synthese via le contrat socle [OuvrirSynthese] (#2351). La
    /// carte n'apparaît que si la feature `synthese-nuit` est activée.
    @FXML
    private void voirSynthese() {
        ouvrirSynthese.ifPresent(ouvrir -> ouvrir.ouvrir(contextePassage()));
    }

    @FXML
    private void voirActivite() {
        ouvrirActivite.ifPresent(ouvrir -> ouvrir.ouvrir(contextePassage()));
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

    /// « Supprimer » et « Annuler le dépôt » : confirmation, action et message d'erreur délégués à
    /// [ActionsFichePassage] (le contrôleur reste du pur câblage).
    @FXML
    private void supprimer() {
        actionsFiche().supprimer();
    }

    @FXML
    private void annulerDepot() {
        actionsFiche().annulerDepot();
    }

    /// Actions de la fiche, construites à l'usage (elles capturent le passage **courant**).
    private ActionsFichePassage actionsFiche() {
        return new ActionsFichePassage(
                viewModel,
                confirmateur,
                notificateur,
                compteurValidations,
                () -> idPassage,
                () -> viewModel.ouvrirSur(idPassage, contexte),
                navigation::ouvrirAccueil);
    }

    /// « Réactiver ce passage » (#1302) : choix du dossier, vérification et rebranchement hors du fil
    /// JavaFX, rapport : délégués à [ActionReactivation] (le contrôleur reste du pur câblage).
    @FXML
    private void reactiver() {
        new ActionReactivation(
                        viewModel,
                        navigation,
                        () -> racine.getScene().getWindow(),
                        selecteur,
                        confirmateur,
                        () -> viewModel.ouvrirSur(idPassage, contexte))
                .reactiver();
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
                // #3455 : le rappel repasse par CE contrôleur, et non par `viewModel.ouvrirSur`.
                // Le ViewModel rechargeait bien ses propriétés - le titre et le fil d'Ariane suivaient,
                // celui-ci se recalculant à chaque affichage - mais `actualiserFil` n'était jamais
                // appelée, et le libellé figé dans l'étape de navigation gardait l'ANCIEN numéro. Le
                // bouton Retour d'un écran ouvert ensuite annonçait donc une nuit qui n'existait plus
                // sous ce nom, pendant que le fil d'Ariane annonçait la bonne.
                () -> ouvrirSur(idPassage, contexte));
    }

    /// « Voir sur la carte » : ouvre la vue multi-sites centrée/surlignée sur le carré de ce passage.
    /// Ouvre la participation liée sur le portail Vigie-Chiro (#1124) : vérification visuelle du
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
}

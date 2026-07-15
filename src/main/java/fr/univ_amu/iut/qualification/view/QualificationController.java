package fr.univ_amu.iut.qualification.view;

import com.google.inject.Inject;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.GardeQuitter;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.qualification.model.SequenceEnSelection;
import fr.univ_amu.iut.qualification.viewmodel.EtatVerdict;
import fr.univ_amu.iut.qualification.viewmodel.QualificationViewModel;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

/// Controller de l'écran **M-Qualification** (`Qualification.fxml`).
///
/// Pur câblage (patron CM4) : relie la liste de la sélection (colonne gauche) au
/// [SelectionEcouteViewModel] et le verdict différé (colonne droite) au [QualificationViewModel],
/// branche la vue audio fournie ([AudioView]), ouvre la modale de personnalisation et gère les
/// raccourcis clavier (O/D/J, Entrée, Espace). Aucun accès base de données ni logique métier ici
/// (règle ArchUnit `view_sans_jdbc`).
public class QualificationController implements GardeQuitter, EmplacementNavigation, ResumeStatut {

    /// Facteur d'expansion temporelle ×10 du protocole Vigie-Chiro : les séquences transformées sont les
    /// originaux ralentis ×10 (cf. `TransformationAudio` côté import). Posé sur l'[AudioView] pour que ses
    /// axes affichent les grandeurs **réelles** (fréquences × 10), et non celles du fichier ralenti. Même
    /// valeur que la vue « Sons & validation » (`ConfigurationAudioView`).
    private static final double FACTEUR_EXPANSION_TEMPS = 10;

    private final QualificationViewModel verdictVm;
    private final SelectionEcouteViewModel selectionVm;
    private final OuvrirPassage ouvrirPassage;
    private final OuvrirSite ouvrirSite;
    private final DepotDispositionColonnes depotColonnes;
    private final ExecuteurTache executeur;

    /// Façade de navigation de la feature : ouvre la modale « Personnaliser la sélection » (#1431).
    private final NavigationQualification navigation;
    private IndicateurOccupation occupation;

    /// Contexte de navigation (passage + site), mémorisé pour reconstruire le fil d'Ariane du chrome.
    private ContextePassage contexte;

    /// Zones de la barre de statut (#1021) : identité / statut+volumétrie / état vivant, recomposées à
    /// chaque changement des propriétés observées.
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    /// Confirmation d'action destructive : porteur partagé injectable (#1013), stub déterministe en test.
    private final ConfirmateurModifiable confirmateur = new ConfirmateurModifiable();

    @FXML
    private StackPane hoteOccupation;

    @FXML
    private BorderPane racine;

    @FXML
    private Label lblTitreContexte;

    @FXML
    private Label lblPlageHoraire;

    @FXML
    private Label lblVolumetrie;

    @FXML
    private Label lblVerdictActuel;

    @FXML
    private Label lblStatut;

    @FXML
    private Label feuCouverture;

    @FXML
    private Label feuNombre;

    @FXML
    private Label feuRenommage;

    @FXML
    private Label lblAnomalie;

    @FXML
    private Label lblListeTitre;

    @FXML
    private ProgressBar barreProgression;

    @FXML
    private Label lblProgression;

    /// Menu ☰ « outils » (#920) : porte l'entrée « Colonnes… » (le clic droit de la table la porte aussi).
    @FXML
    private MenuButton menuOutils;

    @FXML
    private TableView<SequenceEnSelection> tableSequences;

    @FXML
    private TableColumn<SequenceEnSelection, String> colPosition;

    @FXML
    private TableColumn<SequenceEnSelection, String> colFichier;

    @FXML
    private TableColumn<SequenceEnSelection, String> colDuree;

    @FXML
    private TableColumn<SequenceEnSelection, String> colEcoute;

    @FXML
    private Label lblSeqNumero;

    @FXML
    private Label lblSeqMeta;

    @FXML
    private AudioView audioView;

    @FXML
    private Button boutonOk;

    @FXML
    private Button boutonDouteux;

    @FXML
    private Button boutonAJeter;

    @FXML
    private TextArea champCommentaire;

    @FXML
    private Label lblApercuR14;

    @FXML
    private Label lblAvertissement;

    @FXML
    private Label lblMessage;

    /// Erreur de chargement / régénération de la sélection d'écoute (#795), branchée à
    /// `selectionVm.messageProperty()` (jusqu'ici non affichée, donc avalée).
    @FXML
    private Label lblSelectionMessage;

    /// Confirmation de succès locale (#797) : « ✓ Verdict enregistré », visible tant que le verdict à
    /// l'écran correspond à l'état persisté (ENREGISTRE).
    @FXML
    private Label lblSucces;

    @FXML
    private Button boutonEnregistrer;

    /// Enveloppe (non désactivée) du bouton « Enregistrer » : porte le tooltip d'explication du blocage,
    /// qu'un Button désactivé n'affiche pas. Cf. [IndicateurBlocage] (#789).
    @FXML
    private StackPane enveloppeEnregistrer;

    /// Porteur de confirmation exposé aux tests (#1013) : `confirmateur().definir(stub)`.
    ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }

    @Inject
    public QualificationController(
            QualificationViewModel verdictVm,
            SelectionEcouteViewModel selectionVm,
            OuvrirPassage ouvrirPassage,
            OuvrirSite ouvrirSite,
            DepotDispositionColonnes depotColonnes,
            ExecuteurTache executeur,
            NavigationQualification navigation) {
        this.verdictVm = Objects.requireNonNull(verdictVm, "verdictVm");
        this.selectionVm = Objects.requireNonNull(selectionVm, "selectionVm");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.depotColonnes = Objects.requireNonNull(depotColonnes, "depotColonnes");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    /// Garde de navigation : un verdict a été **choisi mais pas encore enregistré** (brouillon). Quitter
    /// l'écran perdrait ce verdict ; le socle demande confirmation avant de naviguer ailleurs.
    @Override
    public boolean aSaisieNonEnregistree() {
        return verdictVm.etatVerdictProperty().get() == EtatVerdict.BROUILLON
                && verdictVm.verdictChoisiProperty().get() != null;
    }

    @Override
    public String messageConfirmationQuitter() {
        return "Un verdict choisi n'a pas été enregistré. Quitter cet écran sans l'enregistrer ?";
    }

    @Override
    public ReadOnlyObjectProperty<ZonesStatut> zonesStatutProperty() {
        return zonesStatut.getReadOnlyProperty();
    }

    @FXML
    private void initialize() {
        occupation = new IndicateurOccupation(hoteOccupation, executeur);
        // Densite et habillage de table uniformes (#690).
        TableDonnees.uniformiser(tableSequences);
        // Sélecteur de colonnes (#920) : clic droit + ☰ « outils » ; disposition retenue par écran (#994).
        GestionnaireColonnes.installerEtPersister(
                tableSequences, menuOutils, colonnesSequences(), depotColonnes, "qualification", "principale");
        // Bandeau : identité de la nuit (VM sélection) + statut/verdict persistés (VM verdict).
        lblTitreContexte.textProperty().bind(selectionVm.titreContexteProperty());
        lblPlageHoraire.textProperty().bind(selectionVm.plageHoraireProperty());
        lblVolumetrie.textProperty().bind(selectionVm.volumetrieProperty());
        lblVerdictActuel
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> libelleVerdict(verdictVm.verdictActuelProperty().get()),
                        verdictVm.verdictActuelProperty()));
        lblStatut
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> libelleStatut(verdictVm.statutProperty().get()), verdictVm.statutProperty()));

        // Barre de statut 3 zones (#1021, EPIC #1016), même modèle que M-Lot : identité à gauche,
        // statut + volumétrie au centre, état vivant à droite (anomalie de pré-check > progression d'écoute).
        zonesStatut.bind(Bindings.createObjectBinding(
                () -> StatutQualification.zones(contexte, verdictVm, selectionVm),
                verdictVm.statutProperty(),
                selectionVm.volumetrieProperty(),
                verdictVm.preCheckAnomalieProperty(),
                selectionVm.progressionTexteProperty()));

        // Pré-check 3 feux (R13, consultatif et jamais bloquant).
        Feux.lier(feuCouverture, "Couverture horaire", verdictVm.feuCouvertureProperty());
        Feux.lier(feuNombre, "Nombre de fichiers", verdictVm.feuNombreProperty());
        Feux.lier(feuRenommage, "Cohérence du renommage", verdictVm.feuRenommageProperty());
        lblAnomalie.visibleProperty().bind(verdictVm.preCheckAnomalieProperty());
        lblAnomalie.managedProperty().bind(verdictVm.preCheckAnomalieProperty());

        // Liste de la sélection + progression d'écoute.
        lblListeTitre
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> "Sélection d'écoute (" + selectionVm.lignes().size() + " séquences)",
                        selectionVm.lignes()));
        tableSequences.setItems(selectionVm.lignes());
        colPosition.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(Integer.toString(c.getValue().position() + 1)));
        colFichier.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(c.getValue().sequence().nomFichier()));
        colDuree.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                Formats.dureeSecondes(c.getValue().sequence().dureeSecondes())));
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
                .bind(Bindings.createStringBinding(
                        () -> numeroSequence(
                                selectionVm.sequenceCouranteProperty().get()),
                        selectionVm.sequenceCouranteProperty()));
        lblSeqMeta
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> metaSequence(
                                selectionVm.sequenceCouranteProperty().get()),
                        selectionVm.sequenceCouranteProperty()));

        // Normalisation du niveau à l'écoute (#109) : les cris ont des amplitudes très variables ;
        // audio-view égalise le rendu (gain seulement, fichier R9 inchangé). Activée par défaut.
        audioView.setNormalisation(true);
        // Expansion temporelle ×10 du protocole Vigie-Chiro : les séquences sont les originaux ralentis ×10,
        // l'axe des fréquences doit donc afficher les valeurs RÉELLES (× 10) et non celles du fichier ralenti.
        // Sans ce réglage, l'axe plafonnait à ~19 kHz au lieu des ~192 kHz réels (fréquences ÷10).
        audioView.setTimeExpansionFactor(FACTEUR_EXPANSION_TEMPS);
        // Vue audio (composant fourni) : la source suit la séquence courante ; le marquage écouté (R10)
        // se déclenche au début de la lecture ; le clip est libéré quand la vue quitte la scène.
        audioView.audioFileProperty().bind(selectionVm.cheminSequenceCouranteProperty());
        audioView.playingProperty().addListener((obs, avant, lecture) -> {
            if (Boolean.TRUE.equals(lecture)) {
                selectionVm.marquerCouranteEcoutee();
            }
        });
        audioView.sceneProperty().addListener((obs, avant, scene) -> {
            if (scene == null) {
                audioView.dispose();
            }
        });

        // Verdict différé : surbrillance du bouton choisi + liaison du commentaire.
        marquerChoisi(boutonOk, Verdict.OK);
        marquerChoisi(boutonDouteux, Verdict.DOUTEUX);
        marquerChoisi(boutonAJeter, Verdict.A_JETER);
        champCommentaire.textProperty().bindBidirectional(verdictVm.commentaireProperty());

        // Aperçu R14 : prévient, avant l'enregistrement, qu'un verdict « à jeter » exclura le passage.
        var apercuAJeter = verdictVm
                .verdictChoisiProperty()
                .isEqualTo(Verdict.A_JETER)
                .and(verdictVm.etatVerdictProperty().isEqualTo(EtatVerdict.BROUILLON));
        lblApercuR14.visibleProperty().bind(apercuAJeter);
        lblApercuR14.managedProperty().bind(apercuAJeter);

        lblAvertissement.textProperty().bind(verdictVm.avertissementAJeterProperty());
        lblAvertissement
                .visibleProperty()
                .bind(verdictVm.avertissementAJeterProperty().isNotEmpty());
        lblAvertissement
                .managedProperty()
                .bind(verdictVm.avertissementAJeterProperty().isNotEmpty());
        lierMessagesErreur();

        // Confirmation de succès locale (#797) : le badge « ✓ Verdict enregistré » apparaît une fois le
        // verdict persisté et disparaît dès qu'une modification recrée un brouillon.
        var verdictEnregistre = verdictVm.etatVerdictProperty().isEqualTo(EtatVerdict.ENREGISTRE);
        lblSucces.visibleProperty().bind(verdictEnregistre);
        lblSucces.managedProperty().bind(verdictEnregistre);

        boutonEnregistrer.disableProperty().bind(verdictVm.peutEnregistrer().not());
        // Explique le grisage (#789) sur l'enveloppe (un Button désactivé n'affiche pas de tooltip) : tant
        // qu'aucun verdict n'est choisi, l'enregistrement n'a rien à persister.
        IndicateurBlocage.expliquer(
                enveloppeEnregistrer,
                Bindings.when(verdictVm.peutEnregistrer())
                        .then("Enregistrer le verdict de ce passage.")
                        .otherwise("Choisissez d'abord un verdict (OK, Douteux…) pour pouvoir l'enregistrer."));

        // Raccourcis clavier (O/D/J, Entrée, Espace) sur la racine de l'écran : addEventFilter (phase de
        // **capture**, #1504) et non addEventHandler (phase de bulle). En bulle, Espace était consommé par
        // le nœud focalisé (un bouton de verdict, ou la liste) comme activation, avant d'atteindre la
        // racine : la lecture ne démarrait jamais. En capture, la racine voit la touche **avant** le nœud
        // focalisé. Limité à cette vue (et non à la scène partagée du chrome) ; la saisie du commentaire
        // reste protégée par le test focusOwner ci-dessous.
        racine.addEventFilter(KeyEvent.KEY_PRESSED, this::gererRaccourci);
    }

    /// Branche les deux zones de message d'erreur de l'écran : le verdict (colonne droite) et la sélection
    /// d'écoute (#795, colonne gauche, jusqu'ici non branchée) ; chacune n'apparaît qu'avec un message.
    private void lierMessagesErreur() {
        lblMessage.textProperty().bind(verdictVm.messageProperty());
        lblMessage.visibleProperty().bind(verdictVm.messageProperty().isNotEmpty());
        lblMessage.managedProperty().bind(verdictVm.messageProperty().isNotEmpty());
        lblSelectionMessage.textProperty().bind(selectionVm.messageProperty());
        lblSelectionMessage.visibleProperty().bind(selectionVm.messageProperty().isNotEmpty());
        lblSelectionMessage.managedProperty().bind(selectionVm.messageProperty().isNotEmpty());
    }

    /// Ouvre l'écran sur le passage `passage` : les deux VM se synchronisent sur le même passage.
    /// Appelée par [NavigationQualification] après le chargement du FXML ; mémorise le contexte pour le
    /// fil d'Ariane.
    public void ouvrirSur(ContextePassage passage) {
        this.contexte = passage;
        Long idPassage = passage.idPassage();
        // Ouverture **hors du fil JavaFX** (#1210) : vérification + sélection d'écoute chargées en
        // arrière-plan sous l'overlay, puis appliquées (ou l'erreur routée, filet #795) sur le fil FX.
        occupation.occuper(
                "Ouverture du passage…",
                () -> new DonneesQualification(verdictVm.charger(idPassage), selectionVm.charger(idPassage)),
                donnees -> {
                    verdictVm.appliquer(idPassage, donnees.verdict());
                    selectionVm.appliquer(idPassage, donnees.selection());
                },
                erreur -> {
                    verdictVm.signalerErreur(idPassage, erreur);
                    selectionVm.signalerErreur(idPassage, erreur);
                });
    }

    /// Données des deux ViewModels de l'écran, chargées ensemble hors du fil JavaFX (#1210).
    private record DonneesQualification(
            QualificationViewModel.DonneesVerdict verdict, SelectionEcouteViewModel.DonneesSelection selection) {}

    /// Emplacement dans le fil d'Ariane : `Mes sites › Carré N › Détails du passage N° X › Vérifier
    /// l'enregistrement` (rendu par le chrome). Le segment passage rouvre M-Passage ; le retour est
    /// désormais porté par le chrome (plus de fil ni de retour internes à l'écran).
    @Override
    public List<Lieu> emplacement() {
        return EmplacementPassage.emplacementEnfant(contexte, ouvrirSite, ouvrirPassage, "Vérifier l'enregistrement");
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
        // Régénération directe (#798) : comme la modale « Personnaliser… », prévenir avant d'effacer une
        // progression d'écoute déjà entamée. Rien à perdre si aucune séquence n'a été écoutée → pas de nag.
        if (selectionVm.progressionProperty().get() > 0
                && !confirmateur.confirmer("Régénérer efface la progression d'écoute (le verdict est conservé)."
                        + " Régénérer quand même ?")) {
            return;
        }
        selectionVm.regenerer();
    }

    /// Colonnes de la sélection d'écoute proposées au sélecteur (#920). « Fichier » est l'identité
    /// (verrouillée) ; les autres sont masquables.
    private List<GestionnaireColonnes.Colonne> colonnesSequences() {
        return List.of(
                new GestionnaireColonnes.Colonne(colPosition, "N°", false),
                new GestionnaireColonnes.Colonne(colFichier, "Fichier", true),
                new GestionnaireColonnes.Colonne(colDuree, "Durée", false),
                new GestionnaireColonnes.Colonne(colEcoute, "Écouté", false));
    }

    /// Ouvre la modale de personnalisation de la sélection (R12) : choix de la méthode et de la taille,
    /// puis régénération (la progression repart de zéro, le verdict est conservé).
    ///
    /// Le `Dialog` bâti ici se terminait par un `showAndWait` : le geste était **injouable dans un test**
    /// - alors qu'il **efface la progression d'écoute** de l'observateur (#1431). Il est désormais une
    /// vraie modale ([ModaleSelectionController]), sur le patron déjà en service dans l'application.
    @FXML
    private void personnaliser() {
        navigation.ouvrirModaleSelection(racine.getScene().getWindow());
    }

    /// Raccourcis clavier (footer de la maquette) : O / D / J posent le verdict, Entrée enregistre,
    /// Espace lance/met en pause la lecture. Inhibés pendant la saisie du commentaire (focus dans un
    /// champ texte) ; ↑/↓ restent gérés nativement par la liste.
    private void gererRaccourci(KeyEvent evenement) {
        if (racine.getScene().getFocusOwner() instanceof TextInputControl) {
            return;
        }
        switch (evenement.getCode()) {
            case O -> consommer(evenement, () -> verdictVm.choisirVerdict(Verdict.OK));
            case D -> consommer(evenement, () -> verdictVm.choisirVerdict(Verdict.DOUTEUX));
            case J -> consommer(evenement, () -> verdictVm.choisirVerdict(Verdict.A_JETER));
            case ENTER -> {
                if (verdictVm.peutEnregistrer().get()) {
                    consommer(evenement, verdictVm::enregistrer);
                }
            }
            case SPACE -> consommer(evenement, audioView::togglePlay);
            default -> {}
        }
    }

    private static void consommer(KeyEvent evenement, Runnable action) {
        action.run();
        evenement.consume();
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
                + Formats.dureeSecondes(ligne.sequence().dureeSecondes())
                + (ligne.ecoutee() ? " · ✓ écoutée" : " · ○ non écoutée");
    }
}

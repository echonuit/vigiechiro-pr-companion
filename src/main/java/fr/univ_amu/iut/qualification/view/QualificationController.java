package fr.univ_amu.iut.qualification.view;

import com.google.inject.Inject;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.GardeQuitter;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.qualification.model.GenerateurSelection;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.SequenceEnSelection;
import fr.univ_amu.iut.qualification.viewmodel.EtatVerdict;
import fr.univ_amu.iut.qualification.viewmodel.QualificationViewModel;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/// Controller de l'écran **M-Qualification** (`Qualification.fxml`).
///
/// Pur câblage (patron CM4) : relie la liste de la sélection (colonne gauche) au
/// [SelectionEcouteViewModel] et le verdict différé (colonne droite) au [QualificationViewModel],
/// branche la vue audio fournie ([AudioView]), ouvre la modale de personnalisation et gère les
/// raccourcis clavier (O/D/J, Entrée, Espace). Aucun accès base de données ni logique métier ici
/// (règle ArchUnit `view_sans_jdbc`).
public class QualificationController implements GardeQuitter, EmplacementNavigation {

    /// Facteur d'expansion temporelle ×10 du protocole Vigie-Chiro : les séquences transformées sont les
    /// originaux ralentis ×10 (cf. `TransformationAudio` côté import). Posé sur l'[AudioView] pour que ses
    /// axes affichent les grandeurs **réelles** (fréquences × 10), et non celles du fichier ralenti. Même
    /// valeur que la vue « Sons & validation » (`ConfigurationAudioView`).
    private static final double FACTEUR_EXPANSION_TEMPS = 10;

    private final QualificationViewModel verdictVm;
    private final SelectionEcouteViewModel selectionVm;
    private final OuvrirPassage ouvrirPassage;
    private final OuvrirSite ouvrirSite;

    /// Contexte de navigation (passage + site), mémorisé pour reconstruire le fil d'Ariane du chrome.
    private ContextePassage contexte;

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

    @Inject
    public QualificationController(
            QualificationViewModel verdictVm,
            SelectionEcouteViewModel selectionVm,
            OuvrirPassage ouvrirPassage,
            OuvrirSite ouvrirSite) {
        this.verdictVm = Objects.requireNonNull(verdictVm, "verdictVm");
        this.selectionVm = Objects.requireNonNull(selectionVm, "selectionVm");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
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

    @FXML
    private void initialize() {
        // Densite et habillage de table uniformes (#690).
        TableDonnees.uniformiser(tableSequences);
        // Sélecteur de colonnes (#920) : clic droit + ☰ « outils ».
        GestionnaireColonnes.installer(tableSequences, menuOutils, colonnesSequences());
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

        // Pré-check 3 feux (R13, consultatif et jamais bloquant).
        lierFeu(feuCouverture, "Couverture horaire", verdictVm.feuCouvertureProperty());
        lierFeu(feuNombre, "Nombre de fichiers", verdictVm.feuNombreProperty());
        lierFeu(feuRenommage, "Cohérence du renommage", verdictVm.feuRenommageProperty());
        lblAnomalie.visibleProperty().bind(verdictVm.preCheckAnomalieProperty());
        lblAnomalie.managedProperty().bind(verdictVm.preCheckAnomalieProperty());

        // Liste de la sélection + progression d'écoute.
        lblListeTitre
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> "📋 Sélection d'écoute (" + selectionVm.lignes().size() + " séquences)",
                        selectionVm.lignes()));
        tableSequences.setItems(selectionVm.lignes());
        colPosition.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(Integer.toString(c.getValue().position() + 1)));
        colFichier.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(c.getValue().sequence().nomFichier()));
        colDuree.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(formatDuree(c.getValue().sequence().dureeSecondes())));
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
        lblMessage.textProperty().bind(verdictVm.messageProperty());
        lblMessage.visibleProperty().bind(verdictVm.messageProperty().isNotEmpty());
        lblMessage.managedProperty().bind(verdictVm.messageProperty().isNotEmpty());

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

        // Raccourcis clavier (O/D/J, Entrée, Espace) sur la racine de l'écran : addEventHandler (et non
        // setOnKeyPressed) pour coexister avec d'autres handlers, et limité à cette vue plutôt qu'à la
        // scène partagée du chrome. Les événements remontent depuis le nœud focalisé jusqu'à la racine.
        racine.addEventHandler(KeyEvent.KEY_PRESSED, this::gererRaccourci);
    }

    /// Ouvre l'écran sur le passage `passage` : les deux VM se synchronisent sur le même passage.
    /// Appelée par [NavigationQualification] après le chargement du FXML ; mémorise le contexte pour le
    /// fil d'Ariane.
    public void ouvrirSur(ContextePassage passage) {
        this.contexte = passage;
        verdictVm.ouvrirSur(passage.idPassage());
        selectionVm.ouvrirSur(passage.idPassage());
    }

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

    /// Ouvre la modale de personnalisation de la sélection (R12) : choix de la méthode et de la
    /// taille, puis régénération (la progression repart de zéro, le verdict est conservé).
    @FXML
    private void personnaliser() {
        RadioButton repar = new RadioButton("⏱ RéparTemporel — réparties sur la nuit");
        RadioButton aleatoire = new RadioButton("🎲 Aléatoire");
        ToggleGroup methode = new ToggleGroup();
        repar.setToggleGroup(methode);
        aleatoire.setToggleGroup(methode);
        boolean estAleatoire = selectionVm.methodeProperty().get() == MethodeSelection.ALEATOIRE;
        (estAleatoire ? aleatoire : repar).setSelected(true);

        Slider taille = new Slider(
                GenerateurSelection.TAILLE_MIN,
                GenerateurSelection.TAILLE_MAX,
                selectionVm.tailleProperty().get());
        taille.setShowTickLabels(true);
        taille.setShowTickMarks(true);
        taille.setMajorTickUnit(5);
        taille.setMinorTickCount(0);
        taille.setSnapToTicks(true);
        Label valeur = new Label();
        valeur.textProperty().bind(taille.valueProperty().asString("Taille : %.0f séquences"));
        Label avert = new Label("⚠ Régénérer efface la progression d'écoute (le verdict est conservé).");
        avert.setWrapText(true);

        Dialog<ButtonType> dialogue = new Dialog<>();
        dialogue.setTitle("Personnaliser la sélection d'écoute");
        dialogue.setHeaderText("Méthode de constitution et taille de la sélection.");
        dialogue.initOwner(tableSequences.getScene().getWindow());
        ButtonType boutonRegenerer = new ButtonType("↺ Régénérer", ButtonBar.ButtonData.OK_DONE);
        dialogue.getDialogPane().getButtonTypes().addAll(boutonRegenerer, ButtonType.CANCEL);
        dialogue.getDialogPane()
                .setContent(
                        new VBox(8, new Label("Méthode :"), repar, aleatoire, new Separator(), valeur, taille, avert));

        if (dialogue.showAndWait().filter(bouton -> bouton == boutonRegenerer).isPresent()) {
            selectionVm
                    .methodeProperty()
                    .set(aleatoire.isSelected() ? MethodeSelection.ALEATOIRE : MethodeSelection.REPARTITION_TEMPORELLE);
            selectionVm.tailleProperty().set((int) Math.round(taille.getValue()));
            selectionVm.regenerer();
        }
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

    private static void lierFeu(Label feu, String libelle, ReadOnlyObjectProperty<PreCheckNuit.Feu> couleur) {
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

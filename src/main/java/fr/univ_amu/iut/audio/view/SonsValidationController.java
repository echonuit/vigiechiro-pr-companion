package fr.univ_amu.iut.audio.view;

import com.google.inject.Inject;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ComparateursAudio;
import fr.univ_amu.iut.audio.viewmodel.ComptageAudio;
import fr.univ_amu.iut.audio.viewmodel.FormatLigneAudio;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.Taxon;
import java.io.File;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/// Controller de la **vue audio unifiée** (`SonsValidation.fxml`, #audio).
///
/// Pur câblage (patron CM4) : lie la table des [LigneObservationAudio], la sélection, le panneau
/// d'écoute (détail + `AudioView`) et la revue au [AudioViewModel]. Les actions communes (valider /
/// corriger / basculer la référence) sont toujours offertes ; les actions propres à la source (import
/// CSV / export `_Vu` d'un passage, export bibliothèque des références) ne s'affichent dans le menu
/// « ☰ » que pour la source concernée, et les colonnes de **contexte** (passage / carré / point) sont
/// masquées quand la source est un **unique passage** (elles y seraient constantes). Aucun accès base
/// ni logique métier ici (règle ArchUnit `view_sans_jdbc`).
public class SonsValidationController implements EmplacementNavigation, ResumeStatut {

    private final AudioViewModel viewModel;
    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;
    private final OuvrirAnalyse ouvrirAnalyse;
    private final OuvrirMultisite ouvrirMultisite;

    /// Source courante, mémorisée pour adapter colonnes / actions / fil d'Ariane.
    private SourceObservations source;

    /// Résumé exposé à la **barre de statut** ([ResumeStatut]) : compteurs de revue de la source courante,
    /// mis à jour en direct. Remplace l'ancien bandeau de titre (redondant avec le fil d'Ariane).
    private final ReadOnlyStringWrapper resumeStatut = new ReadOnlyStringWrapper(this, "resumeStatut", "");

    @FXML
    private VBox racine;

    @FXML
    private TextField champRecherche;

    @FXML
    private MenuButton menuAjoutFiltre;

    @FXML
    private FlowPane pucesFiltres;

    /// Barre de filtres « à la Notion » (#470/#471) : recherche + « + Filtre » + puces, pilotant
    /// [AudioViewModel#filtres]. Mémorisée pour la réinitialiser lors d'une navigation ciblée.
    private GestionnaireFiltres gestionnaireFiltres;

    @FXML
    private MenuButton menuActions;

    @FXML
    private MenuItem itemImporter;

    @FXML
    private CheckMenuItem itemInclureMode;

    @FXML
    private MenuItem itemExporterVu;

    @FXML
    private MenuItem itemExporterBiblio;

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
    private TableColumn<LigneObservationAudio, String> colDebut;

    @FXML
    private TableColumn<LigneObservationAudio, String> colDuree;

    @FXML
    private TableColumn<LigneObservationAudio, String> colObservateur;

    @FXML
    private TableColumn<LigneObservationAudio, String> colFichier;

    @FXML
    private TableColumn<LigneObservationAudio, String> colPassage;

    @FXML
    private TableColumn<LigneObservationAudio, String> colCarre;

    @FXML
    private TableColumn<LigneObservationAudio, String> colPoint;

    @FXML
    private TableColumn<LigneObservationAudio, String> colDate;

    @FXML
    private TableColumn<LigneObservationAudio, String> colStatut;

    @FXML
    private TableColumn<LigneObservationAudio, String> colReference;

    @FXML
    private TableColumn<LigneObservationAudio, String> colCommentaire;

    @FXML
    private Label lblVide;

    @FXML
    private AudioView audioView;

    @FXML
    private ComboBox<ModeRevue> choixMode;

    @FXML
    private Button btnValider;

    @FXML
    private ComboBox<Taxon> choixTaxon;

    @FXML
    private Button btnCorriger;

    @FXML
    private Button btnReference;

    @FXML
    private HBox bandeauRetour;

    @FXML
    private Label lblMessage;

    @FXML
    private Button btnFermerRetour;

    @Inject
    public SonsValidationController(
            AudioViewModel viewModel,
            OuvrirSite ouvrirSite,
            OuvrirPassage ouvrirPassage,
            OuvrirAnalyse ouvrirAnalyse,
            OuvrirMultisite ouvrirMultisite) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvrirAnalyse = Objects.requireNonNull(ouvrirAnalyse, "ouvrirAnalyse");
        this.ouvrirMultisite = Objects.requireNonNull(ouvrirMultisite, "ouvrirMultisite");
    }

    /// Câble chaque colonne à son champ de la ligne (valeur affichée), ses cellules personnalisées (fichier
    /// et commentaire : infobulle) et ses comparateurs de tri **numériques / par ordre de revue** là où
    /// l'affichage est une chaîne formatée (sans quoi « 100 % » précèderait « 83 % » et « N°10 » « N°2 »).
    /// Les colonnes purement texte (taxon, carré, point, date ISO) gardent le tri texte par défaut.
    private void configurerColonnes() {
        colTadarida.setCellValueFactory(c -> new ReadOnlyStringWrapper(FormatLigneAudio.tadarida(c.getValue())));
        colProba.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                FormatLigneAudio.probabilite(c.getValue().probTadarida())));
        colFrequence.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                FormatLigneAudio.frequenceColonne(c.getValue().frequenceKHz())));
        colDebut.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                FormatLigneAudio.positionColonne(c.getValue().debutS())));
        colDuree.setCellValueFactory(c -> new ReadOnlyStringWrapper(FormatLigneAudio.dureeColonne(
                c.getValue().debutS(), c.getValue().finS())));
        colObservateur.setCellValueFactory(c -> new ReadOnlyStringWrapper(FormatLigneAudio.votreTaxon(c.getValue())));
        colFichier.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(ouTiret(c.getValue().nomFichier())));
        // Le nom de fichier transformé est long (préfixe de campagne + suffixe de segment) : la cellule
        // l'élide, une infobulle en donne la valeur complète au survol.
        colFichier.setCellFactory(colonne -> CellulesAudio.avecInfobulle());
        colPassage.setCellValueFactory(
                c -> new ReadOnlyStringWrapper("N°" + c.getValue().numeroPassage()));
        colCarre.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(ouTiret(c.getValue().numeroCarre())));
        colPoint.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(ouTiret(c.getValue().codePoint())));
        colDate.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(ouTiret(c.getValue().dateEnregistrement())));
        colStatut.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                FormatLigneAudio.libelleStatut(c.getValue().statut())));

        // Colonnes dont l'affichage est une chaîne à préfixe/suffixe numérique : même comparateur numérique
        // (sinon « 100 % » précèderait « 83 % » et « N°10 » « N°2 »). Le statut a son propre ordre de revue.
        List.of(colProba, colFrequence, colDebut, colPassage)
                .forEach(colonne -> colonne.setComparator(ComparateursAudio.comparateurNumerique()));
        // Durée : unité adaptative ms/s → comparateur dédié (le tri numérique naïf mêlerait « 120 ms » et
        // « 2,1 s »).
        colDuree.setComparator(ComparateursAudio.comparateurDuree());
        colStatut.setComparator(ComparateursAudio.comparateurStatut());

        // Indicateurs référence / commentaire : en-tête et cellule rendus par une **icône Ikonli colorée**
        // (les emojis ⭐/💬 ne s'affichaient pas dans toutes les polices). En-tête sans texte (icône seule),
        // un id stable pour les retrouver, cellules dédiées (icône + infobulle), et **non triables** (trier
        // une icône n'a pas de sens et donnait une colonne « vide » triable déroutante).
        CellulesAudio.configurerIndicateurs(colReference, colCommentaire, viewModel::commenter);
    }

    @FXML
    private void initialize() {
        configurerColonnes();

        // Rendre les en-têtes cliquables réellement triants : la table est alimentée par une FilteredList
        // (non triable en place) ; on l'enveloppe dans une SortedList dont le comparateur suit celui de la
        // table. Sans cela, cliquer un en-tête ne réordonnait rien. L'ordre initial reste l'ordre de revue.
        SortedList<LigneObservationAudio> triees = new SortedList<>(viewModel.observationsFiltrees());
        triees.comparatorProperty().bind(tableObservations.comparatorProperty());
        tableObservations.setItems(triees);
        tableObservations
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, ancienne, nouvelle) ->
                        viewModel.selectionProperty().set(nouvelle));
        // Resynchronisation **VM → table** : après un Valider/Corriger, charger() reconstruit la liste avec
        // de nouvelles instances de record (statut/taxon changés), ce qui vide la surbrillance de la table
        // alors que le VM restaure la sélection par identifiant. On réaligne la ligne surlignée sur la
        // sélection du VM (et on désélectionne si null). La garde d'égalité empêche toute boucle avec le
        // listener table → VM ci-dessus (une sélection déjà alignée ne redéclenche rien).
        viewModel.selectionProperty().addListener((obs, ancienne, nouvelle) -> {
            var modele = tableObservations.getSelectionModel();
            if (nouvelle == null) {
                modele.clearSelection();
            } else if (!nouvelle.equals(modele.getSelectedItem())) {
                modele.select(nouvelle);
            }
        });

        // Barre de filtres « à la Notion » (#470/#471) : recherche texte permanente + « + Filtre » + puces,
        // pilotant les filtres composables du view-model. Catalogue de critères : statut et groupe taxon.
        gestionnaireFiltres = new GestionnaireFiltres(
                champRecherche,
                menuAjoutFiltre,
                pucesFiltres,
                viewModel.filtres(),
                List.of(
                        CriteresAudio.statut(),
                        CriteresAudio.groupe(viewModel::observationsFiltrees),
                        CriteresAudio.taxon(viewModel::observationsFiltrees),
                        CriteresAudio.references(),
                        CriteresAudio.probabilite()));

        resumeStatut.bind(Bindings.createStringBinding(this::resumeStatutTexte, viewModel.comptageProperty()));

        // Vue audio (composant fourni, E7.S3) : la source suit l'observation sélectionnée ; le clip est
        // libéré quand la vue quitte la scène. Trois normalisations activées, complémentaires : celle du
        // NIVEAU à la lecture (#109) pour égaliser le volume d'un cri à l'autre, et les deux VISUELLES
        // (audio-view 1.14) pour les cris faibles — l'onde du sonogramme remplit la gouttière au lieu de
        // rester plate, et la fenêtre dB du spectrogramme se recale sur le pic au lieu de rester noire.
        audioView.setNormalisation(true);
        audioView.setWaveNormalisation(true);
        audioView.setSpectrogramNormalisation(true);
        // Expansion temporelle ×10 du protocole Vigie-Chiro : les séquences transformées sont les
        // originaux ralentis ×10 (importation.model.TransformationAudio.FACTEUR_EXPANSION). Réglé ici
        // pour que les axes affichent les grandeurs RÉELLES : fréquences × 10 (les vraies fréquences,
        // pas celles du signal ralenti) et temps ÷ 10. N'affecte que les libellés, pas l'audio.
        audioView.setTimeExpansionFactor(RepereCriAudio.FACTEUR_EXPANSION_TEMPS);
        audioView.audioFileProperty().bind(viewModel.cheminAudioCourantProperty());
        audioView.sceneProperty().addListener((obs, avant, scene) -> {
            if (scene == null) {
                audioView.dispose();
            }
        });
        // Repérage du cri sélectionné (#482) : surligne la fenêtre [début, fin] sur l'onde et le
        // spectrogramme (emphase) et y positionne la lecture (seek). Détail dans RepereCriAudio.
        RepereCriAudio.installer(audioView, viewModel.selectionProperty());
        // Grandeurs acoustiques (#500) : FME / fréq. terminale calculées paresseusement par l'audio-view
        // pour le cri sélectionné, colonnes peuplées au fil de la navigation (détail dans le helper).
        MetriquesAcoustiquesAudio.installer(
                audioView, viewModel.selectionProperty(), tableObservations, colFme, colFreqTerminale);

        choixMode.getItems().setAll(ModeRevue.values());
        choixMode.setConverter(libelleConverter(mode -> mode == null ? "" : libelleMode(mode)));
        choixMode.valueProperty().bindBidirectional(viewModel.modeRevueProperty());

        choixTaxon.setItems(viewModel.taxons());
        choixTaxon.setConverter(libelleConverter(taxon -> taxon == null ? "" : libelleTaxon(taxon)));

        btnValider.disableProperty().bind(viewModel.selectionPresenteProperty().not());
        btnCorriger
                .disableProperty()
                .bind(viewModel
                        .selectionPresenteProperty()
                        .not()
                        .or(choixTaxon.valueProperty().isNull()));
        btnReference
                .disableProperty()
                .bind(viewModel.selectionPresenteProperty().not());
        // Libellé + icône (étoile dorée) de la bascule selon l'état de l'observation sélectionnée.
        btnReference.setGraphic(CellulesAudio.icone(CellulesAudio.ICONE_REFERENCE, CellulesAudio.STYLE_REFERENCE));
        btnReference
                .textProperty()
                .bind(Bindings.when(viewModel.selectionReferenceProperty())
                        .then("Retirer la référence")
                        .otherwise("Marquer référence"));

        // Workflow Tadarida (source ParPassage) : toujours actif ; « Importer » tant qu'aucun résultat,
        // « Réimporter » (remplacement après confirmation) une fois un jeu chargé.
        itemImporter
                .textProperty()
                .bind(Bindings.when(viewModel.resultatsDisponiblesProperty())
                        .then("🔁 Réimporter un CSV Tadarida…")
                        .otherwise("📥 Importer un CSV Tadarida…"));
        itemExporterVu
                .disableProperty()
                .bind(viewModel.resultatsDisponiblesProperty().not());
        // Inclure (ou non) la colonne validation_mode dans l'export _Vu (R24), coché par défaut.
        itemInclureMode.selectedProperty().bindBidirectional(viewModel.inclureModeProperty());

        // État vide : placeholder gris superposé à la table, réservé au seul « aucune observation… ».
        var listeVide = Bindings.isEmpty(viewModel.observationsFiltrees());
        lblVide.textProperty().bind(viewModel.messageProperty());
        lblVide.visibleProperty().bind(listeVide);
        lblVide.managedProperty().bind(listeVide);

        // Bandeau de retour d'opération (import / export / valider / corriger) : libellé, visibilité,
        // couleur de sévérité et croix de fermeture, décorrélés de l'état vide pour qu'une erreur d'import
        // ne soit plus noyée dans le placeholder gris. Câblage isolé dans BandeauRetour.
        BandeauRetour.installer(
                bandeauRetour, lblMessage, btnFermerRetour, viewModel.retourProperty(), viewModel::effacerRetour);

        // Glisser-déposer d'un CSV Tadarida sur l'écran : alternative au FileChooser natif (qui coince
        // parfois en devcontainer / bureau distant). Actif seulement pour la source workflow (ParPassage).
        DepotFichier.installer(racine, () -> source != null && source.permetWorkflowTadarida(), this::deposerFichiers);

        // Gestion des colonnes (afficher/masquer + réordonner par glisser) : menu contextuel (clic droit)
        // et item « Colonnes… » du ☰ ouvrent le même panneau. La proposition Tadarida, colonne d'identité,
        // reste toujours affichée (visibilité verrouillée) mais peut être déplacée comme les autres.
        GestionnaireColonnes.installer(
                tableObservations,
                menuActions,
                List.of(
                        new GestionnaireColonnes.Colonne(colTadarida, "Proposition Tadarida", true),
                        new GestionnaireColonnes.Colonne(colProba, "Proba.", false),
                        new GestionnaireColonnes.Colonne(colFrequence, "Fréquence", false),
                        new GestionnaireColonnes.Colonne(colFme, "FME", false),
                        new GestionnaireColonnes.Colonne(colFreqTerminale, "Fréq. terminale", false),
                        new GestionnaireColonnes.Colonne(colDebut, "Début", false),
                        new GestionnaireColonnes.Colonne(colDuree, "Durée", false),
                        new GestionnaireColonnes.Colonne(colObservateur, "Votre taxon", false),
                        new GestionnaireColonnes.Colonne(colFichier, "Fichier", false),
                        new GestionnaireColonnes.Colonne(colPassage, "Passage", false),
                        new GestionnaireColonnes.Colonne(colCarre, "Carré", false),
                        new GestionnaireColonnes.Colonne(colPoint, "Point", false),
                        new GestionnaireColonnes.Colonne(colDate, "Date", false),
                        new GestionnaireColonnes.Colonne(colStatut, "Statut", false),
                        new GestionnaireColonnes.Colonne(colReference, "Référence", false),
                        new GestionnaireColonnes.Colonne(colCommentaire, "Commentaire", false)));
    }

    /// Importe le **premier** fichier glissé-déposé sur l'écran (workflow Tadarida). Délègue à
    /// [ImportTadarida] (import, ou réimport avec confirmation si un jeu existe déjà) et propage son
    /// résultat réel : `true` seulement si un import a abouti, pour ne pas marquer le dépôt complété
    /// quand l'utilisateur annule le remplacement ou que l'import échoue.
    boolean deposerFichiers(List<File> fichiers) {
        if (fichiers.isEmpty()) {
            return false;
        }
        return ImportTadarida.lancer(viewModel, fichiers.get(0).toPath());
    }

    /// Ouvre la vue audio sur `source`, en adaptant colonnes, actions et fil d'Ariane. Appelée par
    /// [NavigationAudio] après le chargement du FXML.
    public void ouvrirSur(SourceObservations source) {
        ouvrirSur(source, null);
    }

    /// Comme [#ouvrirSur(SourceObservations)] mais **pré-sélectionne** l'observation `idObservationCible`
    /// (si non nulle) une fois la table chargée, ce qui déclenche l'écoute de sa séquence. Le filtre de
    /// statut est remis à zéro avant le ciblage pour que la détection visée soit visible.
    public void ouvrirSur(SourceObservations source, Long idObservationCible) {
        this.source = Objects.requireNonNull(source, "source");
        adapterAffichage(source);
        viewModel.ouvrirSur(source);
        if (idObservationCible != null) {
            gestionnaireFiltres.reinitialiser();
            selectionnerObservation(idObservationCible);
        }
    }

    /// Adapte l'affichage à la source : colonnes de contexte masquées si la source est un unique passage,
    /// et **items** du menu « ☰ » propres à la source. Le menu ☰ lui-même reste toujours affiché : il porte
    /// désormais le choix des colonnes, pertinent pour toutes les sources.
    private void adapterAffichage(SourceObservations source) {
        boolean passageUnique = source instanceof SourceObservations.ParPassage;
        colPassage.setVisible(!passageUnique);
        colCarre.setVisible(!passageUnique);
        colPoint.setVisible(!passageUnique);
        // La date d'enregistrement est constante au sein d'un passage (une nuit) : inutile en source unique.
        colDate.setVisible(!passageUnique);

        boolean workflow = source.permetWorkflowTadarida();
        itemImporter.setVisible(workflow);
        itemInclureMode.setVisible(workflow);
        itemExporterVu.setVisible(workflow);
        itemExporterBiblio.setVisible(source.permetExportBibliotheque());
    }

    private void selectionnerObservation(Long idObservation) {
        for (LigneObservationAudio ligne : viewModel.observationsFiltrees()) {
            if (idObservation.equals(ligne.idObservation())) {
                tableObservations.getSelectionModel().select(ligne);
                tableObservations.scrollTo(ligne);
                return;
            }
        }
    }

    /// Emplacement dans le fil d'Ariane, **piloté par la source** : pour `ParPassage`, on reconstruit les
    /// ancêtres site/passage (retour au passage via les contrats socle, comme l'ancienne validation) ;
    /// pour `References` (et, à terme, les autres sources atteintes depuis un écran parent) l'écran est
    /// autonome (segment courant seul).
    @Override
    public List<Lieu> emplacement() {
        if (source instanceof SourceObservations.ParPassage parPassage) {
            return EmplacementPassage.emplacementEnfant(
                    parPassage.contexte(), ouvrirSite, ouvrirPassage, libelleEcran());
        }
        if (source instanceof SourceObservations.ParEspece) {
            // Accueil › Espèces & observations › Écoute : [espèce] — le segment analyse rouvre l'écran.
            return List.of(Lieu.vers("Espèces & observations", ouvrirAnalyse::ouvrir), Lieu.courant(libelleEcran()));
        }
        if (source instanceof SourceObservations.ParPassages) {
            // Accueil › Carte & passages › Écoute : lot — le segment multisite rouvre la vue agrégée.
            return List.of(
                    Lieu.vers("Carte & passages", () -> ouvrirMultisite.ouvrirSurCarre(null)),
                    Lieu.courant(libelleEcran()));
        }
        return List.of(Lieu.courant(libelleEcran()));
    }

    private String libelleEcran() {
        if (source instanceof SourceObservations.References) {
            return "Sons de référence";
        }
        if (source instanceof SourceObservations.ParEspece espece) {
            return "Écoute : " + espece.libelle();
        }
        if (source instanceof SourceObservations.ParPassages lot) {
            return "Écoute : " + lot.libelle();
        }
        return "Sons & validation";
    }

    /// Texte de la **barre de statut** : total d'observations + avancement de la revue (« N observation(s)
    /// · X / N revues »). Sans le nom d'écran (déjà porté par le fil d'Ariane).
    private String resumeStatutTexte() {
        if (source == null) {
            return "";
        }
        ComptageAudio comptage = viewModel.comptageProperty().get();
        if (comptage.total() == 0) {
            return "Aucune observation";
        }
        return comptage.total() + " observation(s) · " + comptage.progression();
    }

    @Override
    public ReadOnlyStringProperty resumeStatutProperty() {
        return resumeStatut.getReadOnlyProperty();
    }

    @FXML
    private void valider() {
        viewModel.valider();
    }

    @FXML
    private void corriger() {
        viewModel.corriger(choixTaxon.getValue());
    }

    @FXML
    private void basculerReference() {
        viewModel.basculerReference();
    }

    /// « Importer / Réimporter un CSV Tadarida » : sélecteur de fichier natif (ouverture) puis
    /// [#lancerImport(Path)] (import, ou réimport avec confirmation si un jeu existe déjà).
    @FXML
    private void importer() {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Importer un CSV Tadarida (observations ou _Vu)");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File fichier = selecteur.showOpenDialog(fenetre());
        if (fichier != null) {
            ImportTadarida.lancer(viewModel, fichier.toPath());
        }
    }

    /// « Exporter _Vu » : sélecteur de fichier natif (enregistrement) puis délégation au VM.
    @FXML
    private void exporterVu() {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Exporter le fichier _Vu (réinjectable)");
        selecteur.setInitialFileName("resultats_Vu.csv");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File fichier = selecteur.showSaveDialog(fenetre());
        if (fichier != null) {
            viewModel.exporterVu(fichier.toPath());
        }
    }

    /// « Exporter la bibliothèque » : sélecteur de dossier natif puis délégation au VM (copie des sons de
    /// référence + récapitulatif CSV).
    @FXML
    private void exporterBibliotheque() {
        DirectoryChooser selecteur = new DirectoryChooser();
        selecteur.setTitle("Exporter la bibliothèque de sons de référence");
        File dossier = selecteur.showDialog(fenetre());
        if (dossier != null) {
            viewModel.exporterBibliotheque(dossier.toPath());
        }
    }

    private javafx.stage.Window fenetre() {
        return tableObservations.getScene().getWindow();
    }

    private static String ouTiret(String valeur) {
        return valeur == null || valeur.isBlank() ? "—" : valeur;
    }

    private static <T> StringConverter<T> libelleConverter(java.util.function.Function<T, String> versLibelle) {
        return new StringConverter<>() {
            @Override
            public String toString(T valeur) {
                return versLibelle.apply(valeur);
            }

            @Override
            public T fromString(String libelle) {
                return null; // ComboBox non éditables : conversion inverse inutile
            }
        };
    }

    private static String libelleTaxon(Taxon taxon) {
        String nom = taxon.nomVernaculaireFr();
        return nom == null || nom.isBlank() ? taxon.code() : taxon.code() + " (" + nom + ")";
    }

    private static String libelleMode(ModeRevue mode) {
        return switch (mode) {
            case ACTIVITE -> "Activité (une par une)";
            case INVENTAIRE -> "Inventaire (propage l'espèce)";
        };
    }
}

package fr.univ_amu.iut.audio.view;

import com.google.inject.Inject;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ComparateursAudio;
import fr.univ_amu.iut.audio.viewmodel.ComptageAudio;
import fr.univ_amu.iut.audio.viewmodel.FormatLigneAudio;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.EspeceIdentifiee;
import fr.univ_amu.iut.commun.view.ActionFicheEspece;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.GestionnaireVues;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.Taxon;
import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

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

    /// Clé de la feature pour les vues mémorisées (`saved_filter_view.feature`) : isole les vues de cet écran.
    private static final String FEATURE = "audio";

    private final AudioViewModel viewModel;
    private final ImportVigieChiroViewModel importVigieChiro;
    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;
    private final OuvrirAnalyse ouvrirAnalyse;
    private final OuvrirMultisite ouvrirMultisite;
    private final DepotVues depotVues;

    /// Action réutilisable « Fiche de l'espèce » (#846) : configure l'item du menu ☰ selon la ligne
    /// sélectionnée et ouvre la fiche dans le navigateur.
    private final ActionFicheEspece actionFicheEspece;

    /// Mémoire de session (tri, #484) : conserve l'état de la table entre deux ouvertures de la vue.
    private final MemoireRevueAudio memoire;

    /// Source courante, mémorisée pour adapter colonnes / actions / fil d'Ariane.
    private SourceObservations source;

    /// Zones exposées à la **barre de statut** ([ResumeStatut], #495) : total d'observations en centre,
    /// avancement de la revue à droite, mis à jour en direct. La gauche reste au défaut du chrome
    /// (identité). Remplace l'ancien bandeau de titre (redondant avec le fil d'Ariane).
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    @FXML
    private VBox racine;

    @FXML
    private TextField champRecherche;

    @FXML
    private MenuButton menuAjoutFiltre;

    @FXML
    private FlowPane pucesFiltres;

    /// Conteneur des onglets de vues mémorisées (`GestionnaireVues`, #623).
    @FXML
    private FlowPane barreOnglets;

    /// Barre de filtres « à la Notion » (#470/#471) : recherche + « + Filtre » + puces, pilotant
    /// [AudioViewModel#filtres]. Mémorisée pour la réinitialiser lors d'une navigation ciblée.
    private GestionnaireFiltres<LigneObservationAudio> gestionnaireFiltres;

    /// Aiguillage des actions de revue selon la sélection (unitaire vs lot, #479), partagé par les boutons et
    /// les raccourcis clavier.
    private ActionsSelectionAudio actionsSelection;

    @FXML
    private MenuButton menuActions;

    @FXML
    private MenuItem itemVoirCarte;

    @FXML
    private MenuItem itemFicheEspece;

    @FXML
    private MenuItem itemImporter;

    @FXML
    private MenuItem itemImporterVigieChiro;

    @FXML
    private Label lblImportVigieChiro;

    @FXML
    private CheckMenuItem itemInclureMode;

    @FXML
    private MenuItem itemExporterVu;

    @FXML
    private MenuItem itemExporterObservations;

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

    /// Colonne « Heure » typée par l'**instant** de capture (et non une chaîne) : le tri par défaut de
    /// [LocalDateTime] est chronologique et gère le passage à minuit (00:15 après 22:00). L'affichage « HH:mm »
    /// est produit par une cellule dédiée. #530.
    @FXML
    private TableColumn<LigneObservationAudio, LocalDateTime> colHeure;

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
    private Button btnDouteux;

    /// Enveloppes (non désactivées) des boutons d'action : portent le tooltip expliquant le blocage
    /// (un Button désactivé n'en affiche pas). Câblées par [ActionsRevueAudio] (#789).
    @FXML
    private StackPane enveloppeValider;

    @FXML
    private StackPane enveloppeCorriger;

    @FXML
    private StackPane enveloppeReference;

    @FXML
    private StackPane enveloppeDouteux;

    @FXML
    private HBox bandeauRetour;

    @FXML
    private Label lblMessage;

    @FXML
    private Button btnFermerRetour;

    @Inject
    public SonsValidationController(
            AudioViewModel viewModel,
            ImportVigieChiroViewModel importVigieChiro,
            OuvrirSite ouvrirSite,
            OuvrirPassage ouvrirPassage,
            OuvrirAnalyse ouvrirAnalyse,
            OuvrirMultisite ouvrirMultisite,
            MemoireRevueAudio memoire,
            DepotVues depotVues,
            ActionFicheEspece actionFicheEspece) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.importVigieChiro = Objects.requireNonNull(importVigieChiro, "importVigieChiro");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvrirAnalyse = Objects.requireNonNull(ouvrirAnalyse, "ouvrirAnalyse");
        this.ouvrirMultisite = Objects.requireNonNull(ouvrirMultisite, "ouvrirMultisite");
        this.memoire = Objects.requireNonNull(memoire, "memoire");
        this.depotVues = Objects.requireNonNull(depotVues, "depotVues");
        this.actionFicheEspece = Objects.requireNonNull(actionFicheEspece, "actionFicheEspece");
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
        colFichier.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(FormatLigneAudio.ouTiret(c.getValue().nomFichier())));
        // Le nom de fichier transformé est long (préfixe de campagne + suffixe de segment) : la cellule
        // l'élide, une infobulle en donne la valeur complète au survol.
        colFichier.setCellFactory(colonne -> CellulesAudio.avecInfobulle());
        colPassage.setCellValueFactory(
                c -> new ReadOnlyStringWrapper("N°" + c.getValue().numeroPassage()));
        colCarre.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(FormatLigneAudio.ouTiret(c.getValue().numeroCarre())));
        colPoint.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(FormatLigneAudio.ouTiret(c.getValue().codePoint())));
        colDate.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(FormatLigneAudio.ouTiret(c.getValue().dateEnregistrement())));
        // « Heure » : valeur = l'INSTANT complet (tri chronologique naturel de LocalDateTime, correct à cheval
        // sur minuit) ; affichage « HH:mm » via une cellule dédiée. Pas de comparateur de chaîne.
        CellulesAudio.configurerColonneHeure(colHeure);
        colStatut.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                FormatLigneAudio.libelleStatut(c.getValue().statut())));

        // Colonnes dont l'affichage est une chaîne à préfixe/suffixe numérique : même comparateur numérique
        // (sinon « 100 % » précèderait « 83 % » et « N°10 » « N°2 »). Le statut a son propre ordre de revue ;
        // « Heure » utilise le tri naturel de LocalDateTime (chronologique).
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
        // Densite et habillage de table uniformes (#690).
        TableDonnees.uniformiser(tableObservations);
        configurerColonnes();

        // Rendre les en-têtes cliquables réellement triants : la table est alimentée par une FilteredList
        // (non triable en place) ; on l'enveloppe dans une SortedList dont le comparateur suit celui de la
        // table. Sans cela, cliquer un en-tête ne réordonnait rien. L'ordre initial reste l'ordre de revue.
        SortedList<LigneObservationAudio> triees = new SortedList<>(viewModel.observationsFiltrees());
        triees.comparatorProperty().bind(tableObservations.comparatorProperty());
        tableObservations.setItems(triees);
        // Multi-sélection (#479) : traiter un lot d'un coup. Le suivi audio/détail suit la DERNIÈRE ligne
        // sélectionnée (selectedItemProperty), les actions opèrent sur tout le lot via actionsSelection.
        tableObservations.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        actionsSelection = new ActionsSelectionAudio(tableObservations, viewModel);
        // Revue au clavier (#478) : Entrée = valider, R = référence, N = prochaine « À revoir » ; ↑/↓ =
        // navigation native. Entrée/R passent par actionsSelection (unitaire si 1 ligne, lot si plusieurs).
        RevueClavier.installer(tableObservations, viewModel, actionsSelection);
        tableObservations.getSelectionModel().selectedItemProperty().addListener((obs, ancienne, nouvelle) -> {
            viewModel.selectionProperty().set(nouvelle);
            // « Fiche de l'espèce » (#847) : cible la proposition Tadarida de la ligne sélectionnée.
            actionFicheEspece.configurer(itemFicheEspece, especeDe(nouvelle));
        });
        // État initial de l'item « Fiche de l'espèce » avant toute sélection (désactivé, libellé explicatif).
        actionFicheEspece.configurer(
                itemFicheEspece, especeDe(tableObservations.getSelectionModel().getSelectedItem()));
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
        gestionnaireFiltres = new GestionnaireFiltres<>(
                champRecherche,
                menuAjoutFiltre,
                pucesFiltres,
                viewModel.filtres(),
                List.of(
                        CriteresAudio.statut(),
                        CriteresAudio.groupe(viewModel::observationsFiltrees),
                        CriteresAudio.taxon(viewModel::observationsFiltrees),
                        CriteresAudio.references(),
                        CriteresAudio.douteux(),
                        CriteresAudio.nonIdentifie(),
                        CriteresAudio.probabilite(),
                        CriteresAudio.heure(viewModel::plageNuitParDefaut)),
                CriteresAudio.rechercheTexte());
        // Mémoire de session (#484) : restaure le tri et l'état des filtres de la dernière ouverture, et les
        // re-mémorise à la fermeture. Placée après le gestionnaire de filtres (dont elle restitue l'état).
        memoire.installer(tableObservations, gestionnaireFiltres);
        // Onglets de vues mémorisées (#623) : enregistrent/rejouent l'état de la barre de filtres. Trois vues
        // par défaut en lecture seule (« Tout », « À valider », « Chiroptères ») : au chargement, « Tout » (sans
        // filtre) est active, d'où toujours un contexte modifiable, sans masquer d'observations.
        GestionnaireVues.avecDialogue(
                barreOnglets, gestionnaireFiltres, depotVues, FEATURE, CriteresAudio.vuesParDefaut());

        zonesStatut.bind(Bindings.createObjectBinding(this::zonesStatutCourantes, viewModel.comptageProperty()));

        // Panneau d'écoute : config AudioView (normalisations, expansion ×10, source, dispose) + repérage du
        // cri (#482) + métriques FME/fréq. terminale (#500) + options de lecture (#483). Détail dans le helper.
        PanneauEcouteAudio.installer(audioView, viewModel, tableObservations, colFme, colFreqTerminale, menuActions);

        choixMode.getItems().setAll(ModeRevue.values());
        choixMode.setConverter(LibellesAudio.converter(mode -> mode == null ? "" : LibellesAudio.mode(mode)));
        choixMode.valueProperty().bindBidirectional(viewModel.modeRevueProperty());

        choixTaxon.setItems(viewModel.taxons());
        choixTaxon.setConverter(LibellesAudio.converter(taxon -> taxon == null ? "" : LibellesAudio.taxon(taxon)));

        // Câblage de la barre d'actions (Valider / Corriger / Référence / Douteux) : désactivation selon la
        // sélection, icônes/libellés des bascules et tooltips d'explication du blocage (#789). Extrait dans
        // ActionsRevueAudio (unité cohésive) pour garder ce contrôleur sous le seuil de God Class.
        ActionsRevueAudio.configurer(
                viewModel,
                choixTaxon,
                btnValider,
                enveloppeValider,
                btnCorriger,
                enveloppeCorriger,
                btnReference,
                enveloppeReference,
                btnDouteux,
                enveloppeDouteux);

        // Workflow Tadarida (source ParPassage) : toujours actif ; « Importer » tant qu'aucun résultat,
        // « Réimporter » (remplacement après confirmation) une fois un jeu chargé.
        itemImporter
                .textProperty()
                .bind(Bindings.when(viewModel.resultatsDisponiblesProperty())
                        .then("🔁 Réimporter un CSV Tadarida…")
                        .otherwise("📥 Importer un CSV Tadarida…"));
        // Import VigieChiro (axe 4.2) : câblage (libellé Importer/Réimporter, désactivation, restitution)
        // délégué à ImportVigieChiroUI. Sa visibilité (workflow + connexion) est gérée dans adapterAffichage.
        ImportVigieChiroUI.cabler(itemImporterVigieChiro, lblImportVigieChiro, importVigieChiro, viewModel);
        itemExporterVu
                .disableProperty()
                .bind(viewModel.resultatsDisponiblesProperty().not());
        // Export des observations affichées : possible dès qu'il y a au moins une ligne (toutes sources).
        itemExporterObservations.disableProperty().bind(Bindings.isEmpty(viewModel.observationsFiltrees()));
        // Un MenuItem désactivé n'accueille pas de tooltip : pour cet item toujours visible, on surface la
        // cause du grisage dans son libellé (#789), qui n'apparaît que lorsqu'il est effectivement grisé
        // (aucune observation à exporter). L'item « Exporter _Vu » est, lui, masqué hors workflow Tadarida
        // (le menu montre alors « Importer un CSV Tadarida »), donc pas de libellé dynamique dessus.
        itemExporterObservations
                .textProperty()
                .bind(Bindings.when(Bindings.isEmpty(viewModel.observationsFiltrees()))
                        .then("📤 Exporter les observations (CSV)… (aucune observation à exporter)")
                        .otherwise("📤 Exporter les observations (CSV)…"));
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
                        new GestionnaireColonnes.Colonne(colHeure, "Heure", false),
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
        boolean passageUnique = source.cibleUnPassageUnique();
        colPassage.setVisible(!passageUnique);
        colCarre.setVisible(!passageUnique);
        colPoint.setVisible(!passageUnique);
        // La date d'enregistrement est constante au sein d'un passage (une nuit) : inutile en source unique.
        colDate.setVisible(!passageUnique);

        boolean workflow = source.permetWorkflowTadarida();
        itemImporter.setVisible(workflow);
        // Import VigieChiro : workflow Tadarida **et** application connectée (indisponible en capture).
        itemImporterVigieChiro.setVisible(workflow && importVigieChiro.disponible());
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
        // ParPassage cible un passage : ascendance site › passage › écran (retour au passage).
        var contextePassage = source.contexteDuPassage();
        if (contextePassage != null) {
            return EmplacementPassage.emplacementEnfant(contextePassage, ouvrirSite, ouvrirPassage, source.titre());
        }
        if (source instanceof SourceObservations.ParEspece) {
            // Accueil › Espèces & observations › Écoute : [espèce] — le segment analyse rouvre l'écran.
            return List.of(Lieu.vers("Espèces & observations", ouvrirAnalyse::ouvrir), Lieu.courant(source.titre()));
        }
        if (source instanceof SourceObservations.ParPassages) {
            // Accueil › Carte & passages › Écoute : lot — le segment multisite rouvre la vue agrégée.
            return List.of(
                    Lieu.vers("Carte & passages", () -> ouvrirMultisite.ouvrirSurCarre(null)),
                    Lieu.courant(source.titre()));
        }
        return List.of(Lieu.courant(source.titre()));
    }

    /// Zones de la **barre de statut** : le total d'observations en **centre**, l'avancement de la revue à
    /// **droite** (« N observation(s) » · « X / N revues »). La gauche reste au défaut du chrome (identité).
    /// Sans le nom d'écran (déjà porté par le fil d'Ariane).
    private ZonesStatut zonesStatutCourantes() {
        if (source == null) {
            return ZonesStatut.VIDE;
        }
        ComptageAudio comptage = viewModel.comptageProperty().get();
        if (comptage.total() == 0) {
            return ZonesStatut.centre("Aucune observation");
        }
        return ZonesStatut.centreEtDroite(comptage.total() + " observation(s)", comptage.progression());
    }

    @Override
    public ReadOnlyObjectProperty<ZonesStatut> zonesStatutProperty() {
        return zonesStatut.getReadOnlyProperty();
    }

    @FXML
    private void valider() {
        actionsSelection.valider();
    }

    @FXML
    private void corriger() {
        actionsSelection.corriger(choixTaxon.getValue());
    }

    @FXML
    private void basculerReference() {
        actionsSelection.basculerReference();
    }

    @FXML
    private void basculerDouteux() {
        actionsSelection.basculerDouteux();
    }

    /// L'espèce ciblée par « Fiche de l'espèce » : la **proposition Tadarida** de la ligne (code + nom
    /// vernaculaire). Le nom latin n'est pas porté par la projection audio ; il reste `null`, ce qui
    /// suffit aux chiroptères (fiche PNA par code) et grise l'item pour les taxons hors PNA sans latin
    /// (oiseaux…). `null` (aucune ligne sélectionnée) → espèce vide, item désactivé.
    private static EspeceIdentifiee especeDe(LigneObservationAudio ligne) {
        return ligne == null
                ? new EspeceIdentifiee(null, null, null)
                : new EspeceIdentifiee(ligne.taxonTadarida(), null, ligne.nomTadarida());
    }

    /// « 🗺 Voir sur la carte » (#476) : rouvre l'analyse « Espèces & observations » directement sur la
    /// **carte de répartition**, en y transportant les filtres courants. Le socle ne rejoue que les critères
    /// que l'analyse connaît (statut, groupe) et la recherche texte ; les filtres propres à l'audio (proba,
    /// références, espèce, heure) sont ignorés.
    @FXML
    private void voirSurCarte() {
        ouvrirAnalyse.ouvrir(gestionnaireFiltres.decrire(), true);
    }

    /// « Importer / Réimporter un CSV Tadarida » : sélecteur de fichier natif (ouverture) puis
    /// [#lancerImport(Path)] (import, ou réimport avec confirmation si un jeu existe déjà).
    @FXML
    private void importer() {
        File fichier = ChoixFichierCsv.selecteur("Importer un CSV Tadarida (observations ou _Vu)", null)
                .showOpenDialog(fenetre());
        if (fichier != null) {
            ImportTadarida.lancer(viewModel, fichier.toPath());
        }
    }

    /// Importe les résultats Tadarida depuis **VigieChiro** (axe 4.2) pour le passage courant. Délègue à
    /// [ImportVigieChiroUI] (confirmation + récupération réseau hors fil JavaFX + rafraîchissement).
    @FXML
    private void importerDepuisVigieChiro() {
        ImportVigieChiroUI.lancer(importVigieChiro, viewModel, source);
    }

    /// « Exporter _Vu » : sélecteur de fichier natif (enregistrement) puis délégation au VM.
    @FXML
    private void exporterVu() {
        ExportsAudioUI.exporterVu(viewModel, fenetre());
    }

    /// « Exporter les observations (CSV) » (#149) : sélecteur de fichier natif puis délégation au VM, qui
    /// écrit le **sous-ensemble affiché** (filtres appliqués).
    @FXML
    private void exporterObservations() {
        ExportsAudioUI.exporterObservations(viewModel, fenetre());
    }

    /// « Exporter la bibliothèque » : sélecteur de dossier natif puis délégation au VM (copie des sons de
    /// référence + récapitulatif CSV).
    @FXML
    private void exporterBibliotheque() {
        ExportsAudioUI.exporterBibliotheque(viewModel, fenetre());
    }

    private javafx.stage.Window fenetre() {
        return tableObservations.getScene().getWindow();
    }
}

package fr.univ_amu.iut.audio.view;

import com.google.inject.Inject;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.DemandeurDeChoixModifiable;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.view.SelecteurFichierModifiable;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.Taxon;
import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
    private final PublicationCorrectionsViewModel publicationCorrections;
    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;
    private final Optional<OuvrirAnalyse> ouvrirAnalyse;
    private final OuvrirMultisite ouvrirMultisite;
    private final AppuisAudio appuis;
    private IndicateurOccupation occupation;

    /// Action réutilisable « Fiche de l'espèce » (#846) : configure l'item du menu ☰ selon la ligne
    /// sélectionnée et ouvre la fiche dans le navigateur.
    private final ActionsMenuAudio actionsMenu;

    /// Mémoire de session (tri, #484) : conserve l'état de la table entre deux ouvertures de la vue.
    private final MemoireRevueAudio memoire;

    /// Réglages réactifs (#1006) : câble les options de lecture du menu ☰ ([LecteurAudio]) aux mêmes
    /// Property que l'onglet « Audio » de l'écran Réglages (persistance + synchro).
    private final ReglagesReactifs reactifs;

    /// Source courante, mémorisée pour adapter colonnes / actions / fil d'Ariane.
    private SourceObservations source;

    /// Zones exposées à la **barre de statut** ([ResumeStatut], #495) : total d'observations en centre,
    /// avancement de la revue à droite, mis à jour en direct. La gauche reste au défaut du chrome
    /// (identité). Remplace l'ancien bandeau de titre (redondant avec le fil d'Ariane).
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    /// Les porteurs de dialogue de l'écran (#1431) : le oui/non et le choix de participation. Réunis dans
    /// [DialoguesAudio] - ils forment une unité (« ce que l'écran demande à l'utilisateur »), et le
    /// contrôleur touchait son plafond de taille.
    private final DialoguesAudio dialogues =
            new DialoguesAudio(() -> this.tableObservations.getScene().getWindow());

    /// Porteur de confirmation exposé aux tests (#1013) : `confirmateur().definir(stub)`.
    ConfirmateurModifiable confirmateur() {
        return dialogues.confirmateur();
    }

    /// Porteur de désignation exposé aux tests (#1431) : `selecteur().definir(double)`.
    SelecteurFichierModifiable selecteur() {
        return dialogues.selecteur();
    }

    /// Porteur de choix exposé aux tests (#1431) : `demandeurParticipation().definir(double)`.
    DemandeurDeChoixModifiable<ParticipationVigieChiro> demandeurParticipation() {
        return dialogues.participation();
    }

    @FXML
    private StackPane hoteOccupation;

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
    private MenuItem itemPublierCorrections;

    @FXML
    private MenuItem itemOuvrirVigieChiro;

    @FXML
    private Label lblImportVigieChiro;

    @FXML
    private Label lblPublierCorrections;

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
    private TableColumn<LigneObservationAudio, String> colCertitude;

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

    /// Hôte du fil de discussion (#1417), à droite du lecteur : masqué tant qu'aucun message n'existe.
    @FXML
    private StackPane hoteDiscussion;

    @FXML
    private TableColumn<LigneObservationAudio, String> colValidateur;

    @FXML
    private TableColumn<LigneObservationAudio, String> colFil;

    @FXML
    private Label lblVide;

    @FXML
    private Label lblAstuceDepot;

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

    @FXML
    private MenuButton menuCertitude;

    @FXML
    private StackPane enveloppeCertitude;

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

    /// Bandeau de disponibilité de l'audio (#1301) : « passage archivé » ou « audio partiel n/total ».
    @FXML
    private Label lblBandeauArchive;

    /// Encart affiché à la place du lecteur quand le fichier de la séquence sélectionnée n'est plus
    /// sur disque (#1301) : explique au lieu de laisser un lecteur inerte.
    @FXML
    private VBox encartAudioManquant;

    @Inject
    public SonsValidationController(
            AudioViewModel viewModel,
            ImportVigieChiroViewModel importVigieChiro,
            PublicationCorrectionsViewModel publicationCorrections,
            OuvrirSite ouvrirSite,
            OuvrirPassage ouvrirPassage,
            Optional<OuvrirAnalyse> ouvrirAnalyse,
            OuvrirMultisite ouvrirMultisite,
            MemoireRevueAudio memoire,
            AppuisAudio appuis,
            ActionsMenuAudio actionsMenu,
            ReglagesReactifs reactifs) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.importVigieChiro = Objects.requireNonNull(importVigieChiro, "importVigieChiro");
        this.publicationCorrections = Objects.requireNonNull(publicationCorrections, "publicationCorrections");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvrirAnalyse = Objects.requireNonNull(ouvrirAnalyse, "ouvrirAnalyse");
        this.ouvrirMultisite = Objects.requireNonNull(ouvrirMultisite, "ouvrirMultisite");
        this.memoire = Objects.requireNonNull(memoire, "memoire");
        this.appuis = Objects.requireNonNull(appuis, "appuis");
        this.actionsMenu = Objects.requireNonNull(actionsMenu, "actionsMenu");
        this.reactifs = Objects.requireNonNull(reactifs, "reactifs");
    }

    /// Colonnes injectées par le FXML, regroupées ([ColonnesAudio.Colonnes]) : construites une fois,
    /// partagées entre le câblage initial et l'adaptation à la source.
    private ColonnesAudio.Colonnes colonnes;

    /// Items du ☰ pilotés par le workflow / la source, regroupés ([MenuAudio.Items]).
    private MenuAudio.Items itemsMenu;

    /// Câble les colonnes de la table (valeur, cellules, comparateurs de tri). Le détail vit dans
    /// [ColonnesAudio] (unité cohésive extraite pour garder ce contrôleur sous le seuil de God Class) ; on
    /// lui passe les colonnes injectées par le FXML, regroupées.
    private void configurerColonnes() {
        colonnes = new ColonnesAudio.Colonnes(
                colTadarida,
                colProba,
                colFrequence,
                colDebut,
                colDuree,
                colObservateur,
                colCertitude,
                colFichier,
                colPassage,
                colCarre,
                colPoint,
                colDate,
                colHeure,
                colStatut,
                colReference,
                colCommentaire,
                colValidateur,
                colFil);
        ColonnesAudio.configurer(colonnes, viewModel.actions()::commenter);
    }

    @FXML
    private void initialize() {
        // Densite et habillage de table uniformes (#690).
        TableDonnees.uniformiser(tableObservations);
        configurerColonnes();

        // « Voir sur la carte » rouvre l'analyse : masqué si la feature `analyse` est coupée (#1087).
        itemVoirCarte.setVisible(ouvrirAnalyse.isPresent());

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
        // Menu « Certitude » (#1139) : déclaration manuelle Sûr/Probable/Possible sur la sélection,
        // en miroir de la « Confiance observateur » du site (vide par défaut). Items et blocage câblés
        // dans MenuCertitude (classe dédiée, seuil de God Class).
        MenuCertitude.installer(menuCertitude, enveloppeCertitude, viewModel, actionsSelection);
        // Fil de discussion avec le validateur (#1417) : le panneau vit à droite du lecteur et suit la
        // sélection ; il ne s'ouvre que si la ligne porte réellement des messages. Câblage délégué
        // (PanneauDiscussion.installer), comme MenuCertitude : ce contrôleur est au plafond de NcssCount.
        PanneauDiscussion.installer(hoteDiscussion, tableObservations, viewModel, appuis.executeur());

        tableObservations.getSelectionModel().selectedItemProperty().addListener((obs, ancienne, nouvelle) -> {
            viewModel.selectionProperty().set(nouvelle);
            // « Fiche de l'espèce » (#847) : cible la proposition Tadarida de la ligne sélectionnée.
            actionsMenu.configurerFiche(itemFicheEspece, nouvelle);
        });
        // État initial de l'item « Fiche de l'espèce » avant toute sélection (désactivé, libellé explicatif).
        actionsMenu.configurerFiche(
                itemFicheEspece, tableObservations.getSelectionModel().getSelectedItem());
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

        // Barre de filtres « à la Notion » (#470/#471), mémoire de session (#484) et onglets de vues
        // mémorisées (#623) : assemblage délégué à FiltresVuesAudio, qui rend le gestionnaire (gardé pour
        // les navigations ciblées et le transport des filtres vers l'analyse).
        gestionnaireFiltres = FiltresVuesAudio.installer(
                new FiltresVuesAudio.Barre(champRecherche, menuAjoutFiltre, pucesFiltres, barreOnglets),
                tableObservations,
                viewModel,
                memoire,
                appuis.depotVues(),
                FEATURE,
                this::colonnesTableAudio);

        zonesStatut.bind(Bindings.createObjectBinding(this::zonesStatutCourantes, viewModel.comptageProperty()));

        // Panneau d'écoute : config AudioView (normalisations, expansion ×10, source, dispose) + repérage du
        // cri (#482) + métriques FME/fréq. terminale (#500) + options de lecture (#483). Détail dans le helper.
        PanneauEcouteAudio.installer(
                audioView, viewModel, tableObservations, colFme, colFreqTerminale, menuActions, reactifs);

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

        // Items du ☰ pilotés par le workflow / la source : bindings une fois pour toutes dans MenuAudio
        // (libellés Importer/Réimporter, exports, case validation_mode persistée #1006/R24).
        itemsMenu = new MenuAudio.Items(
                itemImporter,
                itemImporterVigieChiro,
                lblImportVigieChiro,
                itemPublierCorrections,
                lblPublierCorrections,
                itemInclureMode,
                itemExporterVu,
                itemExporterObservations,
                itemExporterBiblio,
                itemOuvrirVigieChiro);
        MenuAudio.cabler(itemsMenu, viewModel, importVigieChiro, publicationCorrections, reactifs);

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

        // Disponibilité de l'audio (#1301) : bandeau « passage archivé / audio partiel n/total » en tête
        // d'écran (masqué quand tout est là), et encart d'explication à la place du lecteur quand le
        // fichier de la séquence sélectionnée n'est plus sur disque (jamais un lecteur inerte).
        var bandeauPresent = viewModel.bandeauArchiveProperty().isNotEmpty();
        lblBandeauArchive.textProperty().bind(viewModel.bandeauArchiveProperty());
        lblBandeauArchive.visibleProperty().bind(bandeauPresent);
        lblBandeauArchive.managedProperty().bind(bandeauPresent);
        encartAudioManquant.visibleProperty().bind(viewModel.audioManquantProperty());
        encartAudioManquant.managedProperty().bind(viewModel.audioManquantProperty());
        audioView.visibleProperty().bind(viewModel.audioManquantProperty().not());

        // Glisser-déposer d'un CSV Tadarida sur l'écran : alternative au FileChooser natif (qui coince
        // parfois en devcontainer / bureau distant). Actif seulement pour la source workflow (ParPassage).
        DepotFichier.installer(racine, () -> source != null && source.permetWorkflowTadarida(), this::deposerFichiers);

        // Gestion des colonnes (afficher/masquer + réordonner par glisser) : menu contextuel (clic droit)
        // et item « Colonnes… » du ☰ ouvrent le même panneau. La proposition Tadarida, colonne d'identité,
        // reste toujours affichée (visibilité verrouillée) mais peut être déplacée comme les autres.
        GestionnaireColonnes.installerEtPersister(
                tableObservations, menuActions, colonnesTableAudio(), appuis.depotColonnes(), FEATURE, "principale");

        occupation = new IndicateurOccupation(hoteOccupation, appuis.executeur());
    }

    /// Colonnes de la table audio proposées au sélecteur (#916), partagées entre le câblage `installer` et la
    /// capture dans les vues mémorisées (#994). « Proposition Tadarida », colonne d'identité, reste toujours
    /// affichée (verrouillée) mais déplaçable.
    private List<GestionnaireColonnes.Colonne> colonnesTableAudio() {
        return List.of(
                new GestionnaireColonnes.Colonne(colTadarida, "Proposition Tadarida", true),
                new GestionnaireColonnes.Colonne(colProba, "Proba.", false),
                new GestionnaireColonnes.Colonne(colFrequence, "Fréquence", false),
                new GestionnaireColonnes.Colonne(colFme, "FME", false),
                new GestionnaireColonnes.Colonne(colFreqTerminale, "Fréq. terminale", false),
                new GestionnaireColonnes.Colonne(colDebut, "Début", false),
                new GestionnaireColonnes.Colonne(colDuree, "Durée", false),
                new GestionnaireColonnes.Colonne(colObservateur, "Votre taxon", false),
                new GestionnaireColonnes.Colonne(colCertitude, "Certitude", false),
                new GestionnaireColonnes.Colonne(colFichier, "Fichier", false),
                new GestionnaireColonnes.Colonne(colPassage, "Passage", false),
                new GestionnaireColonnes.Colonne(colCarre, "Carré", false),
                new GestionnaireColonnes.Colonne(colPoint, "Point", false),
                new GestionnaireColonnes.Colonne(colDate, "Date", false),
                new GestionnaireColonnes.Colonne(colHeure, "Heure", false),
                new GestionnaireColonnes.Colonne(colStatut, "Statut", false),
                new GestionnaireColonnes.Colonne(colReference, "Référence", false),
                new GestionnaireColonnes.Colonne(colCommentaire, "Commentaire", false),
                new GestionnaireColonnes.Colonne(colValidateur, "Avis du validateur", false),
                new GestionnaireColonnes.Colonne(colFil, "Discussion", false));
    }

    /// Importe le **premier** fichier glissé-déposé sur l'écran (workflow Tadarida). Délègue à
    /// [ImportTadarida] (import, ou réimport avec confirmation si un jeu existe déjà) et propage son
    /// résultat réel : `true` seulement si un import a abouti, pour ne pas marquer le dépôt complété
    /// quand l'utilisateur annule le remplacement ou que l'import échoue.
    boolean deposerFichiers(List<File> fichiers) {
        if (fichiers.isEmpty()) {
            return false;
        }
        return ImportTadarida.lancer(viewModel, fichiers.get(0).toPath(), confirmateur());
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
        // Chargement des sons **hors du fil JavaFX** (#1214) : résolution de la source en arrière-plan
        // sous l'overlay, puis application (ou erreur, filet #795) sur le fil JavaFX, enfin le ciblage.
        occupation.occuper(
                "Chargement des sons…",
                () -> viewModel.chargerOuverture(source),
                donnees -> {
                    viewModel.appliquerOuverture(source, donnees);
                    if (idObservationCible != null) {
                        gestionnaireFiltres.reinitialiser();
                        selectionnerObservation(idObservationCible);
                    }
                },
                erreur -> viewModel.signalerErreur(source, erreur));
    }

    /// Adapte l'affichage à la source : colonnes de contexte masquées si la source est un unique passage
    /// ([ColonnesAudio#adapterAuContexte]) et items du menu « ☰ » propres à la source ([MenuAudio#adapter]).
    private void adapterAffichage(SourceObservations source) {
        ColonnesAudio.adapterAuContexte(colonnes, source.cibleUnPassageUnique());
        MenuAudio.adapter(itemsMenu, source, importVigieChiro, publicationCorrections, actionsMenu.donneesVigieChiro());
        // Astuce de découvrabilité du glisser-déposer (#1015) : rien ne signalait qu'un CSV Tadarida
        // peut être déposé sur l'écran. Le rappel discret suit la même règle d'activation que le dépôt
        // lui-même et disparaît (non managé) pour les sources sans workflow, l'écran restant dense.
        boolean workflow = source.permetWorkflowTadarida();
        lblAstuceDepot.setVisible(workflow);
        lblAstuceDepot.setManaged(workflow);
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

    /// Emplacement dans le fil d'Ariane, **piloté par la source**. Détail dans [ChromeAudio].
    @Override
    public List<Lieu> emplacement() {
        return ChromeAudio.emplacement(source, ouvrirSite, ouvrirPassage, ouvrirAnalyse, ouvrirMultisite);
    }

    /// Zones de la **barre de statut**, dérivées de la source et du comptage. Détail dans [ChromeAudio].
    private ZonesStatut zonesStatutCourantes() {
        return ChromeAudio.zonesStatut(source, viewModel.comptageProperty().get());
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

    /// « 🗺 Voir sur la carte » (#476) : rouvre l'analyse « Espèces & observations » directement sur la
    /// **carte de répartition**, en y transportant les filtres courants. Le socle ne rejoue que les critères
    /// que l'analyse connaît (statut, groupe) et la recherche texte ; les filtres propres à l'audio (proba,
    /// références, espèce, heure) sont ignorés. Neutralisé si la feature `analyse` est désactivable et
    /// coupée (#1087) : l'item est alors masqué (cf. `initialize()`), ce handler n'est pas déclenché.
    @FXML
    private void voirSurCarte() {
        ouvrirAnalyse.ifPresent(ouvrir -> ouvrir.ouvrir(gestionnaireFiltres.decrire(), true));
    }

    /// Ouvre la page des données (observations Tadarida) de la participation liée au passage courant
    /// sur le portail Vigie-Chiro (#1124). Détail dans [ActionDonneesVigieChiro].
    @FXML
    private void ouvrirDonneesVigieChiro() {
        actionsMenu.donneesVigieChiro().ouvrir(source);
    }

    /// « Importer / Réimporter un CSV Tadarida » : demande le fichier puis [ImportTadarida] (import, ou
    /// réimport avec confirmation si un jeu existe déjà). La désignation passe par le porteur de l'écran
    /// (#1431) : sans lui, le geste s'arrêtait à sa première ligne dans un test.
    @FXML
    private void importer() {
        selecteur()
                .choisirFichier("Importer un CSV Tadarida (observations ou _Vu)", Optional.empty(), FiltreFichier.csv())
                .ifPresent(csv -> ImportTadarida.lancer(viewModel, csv, confirmateur()));
    }

    /// Importe les résultats Tadarida depuis **VigieChiro** (axe 4.2) pour le passage courant. Délègue à
    /// [ImportVigieChiroUI] (confirmation + récupération réseau hors fil JavaFX via le socle #1255 +
    /// rafraîchissement).
    @FXML
    private void importerDepuisVigieChiro() {
        ImportVigieChiroUI.lancer(
                importVigieChiro, viewModel, source, appuis.executeur(), confirmateur(), demandeurParticipation());
    }

    /// Publie les corrections observateur du passage courant vers VigieChiro (#723). Délègue à
    /// [PublicationCorrectionsUI] (tri hors fil, confirmation récapitulative, envoi hors fil, bilan).
    @FXML
    private void publierCorrections() {
        PublicationCorrectionsUI.lancer(publicationCorrections, source, appuis.executeur(), confirmateur());
    }

    /// « Exporter _Vu » : sélecteur de fichier natif (enregistrement) puis délégation au VM.
    @FXML
    private void exporterVu() {
        ExportsAudioUI.exporterVu(viewModel, selecteur());
    }

    /// « Exporter les observations (CSV) » (#149) : sélecteur de fichier natif puis délégation au VM, qui
    /// écrit le **sous-ensemble affiché** (filtres appliqués).
    @FXML
    private void exporterObservations() {
        ExportsAudioUI.exporterObservations(viewModel, selecteur());
    }

    /// « Exporter la bibliothèque » : sélecteur de dossier natif puis délégation au VM (copie des sons de
    /// référence + récapitulatif CSV).
    @FXML
    private void exporterBibliotheque() {
        ExportsAudioUI.exporterBibliotheque(viewModel, selecteur());
    }
}

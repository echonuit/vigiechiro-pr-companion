package fr.univ_amu.iut.audio.view;

import com.google.inject.Inject;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ComptageEnjeu;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.DemandeurDeChoixModifiable;
import fr.univ_amu.iut.commun.view.DialogueProgression;
import fr.univ_amu.iut.commun.view.DoubleClicLigne;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.MemoireFiltres;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.view.SelecteurFichierModifiable;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.Taxon;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
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
/// « ☰ » que pour la source concernée, et les colonnes de **contexte** (passage / carré / point)
/// sont
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
    private final MemoireFiltres memoire;

    /// Réglages réactifs (#1006) : câble les options de lecture du menu ☰ ([LecteurAudio]) aux mêmes
    /// Property que l'onglet « Audio » de l'écran Réglages (persistance + synchro).
    private final ReglagesReactifs reactifs;

    /// Source courante, mémorisée pour adapter colonnes / actions / fil d'Ariane.
    /// **Observable** depuis #3752 : la barre de statut la lit pour sa zone gauche, et un champ nu
    /// ne peut pas etre declare en dependance d'un binding. Sans elle, la barre restait a
    /// `ZonesStatut.VIDE` quand l'ouverture echouait - donc retiree du layout, pas seulement pale.
    private final ObjectProperty<SourceObservations> source = new SimpleObjectProperty<>(this, "source");

    /// Zones exposées à la **barre de statut** ([ResumeStatut], #495) : total d'observations en centre,
    /// avancement de la revue à droite, mis à jour en direct. La gauche reste au défaut du chrome
    /// (identité). Remplace l'ancien bandeau de titre (redondant avec le fil d'Ariane).
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    /// Les porteurs de dialogue de l'écran (#1431) : le oui/non et le choix de participation. Réunis dans
    /// [DialoguesAudio] - ils forment une unité (« ce que l'écran demande à l'utilisateur »), et le
    /// contrôleur touchait son plafond de taille.
    private final DialoguesAudio dialogues =
            new DialoguesAudio(() -> this.tableauController.table().getScene().getWindow());

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

    /// « Tout effacer » (#3098) : retire tous les filtres, remet le tri à plat, et **oublie la mémoire
    /// de session** de cet écran.
    ///
    /// Ce dernier point n'est pas accessoire. Sans lui, le geste viderait l'écran et la mémoire
    /// remettrait tout à la visite suivante : le bouton paraîtrait ne pas avoir pris.
    @FXML
    private void toutEffacer() {
        gestionnaireFiltres.reinitialiser();
        tableauController.table().getSortOrder().clear();
        memoire.oublier(FEATURE);
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
    private VBox zoneImportVigieChiro;

    @FXML
    private VBox zonePublierCorrections;

    @FXML
    private CheckMenuItem itemInclureMode;

    @FXML
    private MenuItem itemExporterVu;

    @FXML
    private MenuItem itemExporterObservations;

    @FXML
    private MenuItem itemExporterSons;

    @FXML
    private MenuItem itemExporterBiblio;

    /// Hôte du fil de discussion (#1417), à droite du lecteur : masqué tant qu'aucun message n'existe.
    @FXML
    private StackPane hoteDiscussion;

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
    private Label lblRetour;

    @FXML
    private Button btnFermerRetour;

    /// Bandeau de disponibilité de l'audio (#1301) : « passage archivé » ou « audio partiel n/total ».
    @FXML
    private Label lblBandeauArchive;

    /// Encart affiché à la place du lecteur quand le fichier de la séquence sélectionnée n'est plus
    /// sur disque (#1301) : explique au lieu de laisser un lecteur inerte.
    @FXML
    private VBox encartAudioManquant;

    @FXML
    private VBox encartAudioDivergent;

    @FXML
    private Label lblMotifDivergence;

    @Inject
    public SonsValidationController(
            AudioViewModel viewModel,
            ImportVigieChiroViewModel importVigieChiro,
            PublicationCorrectionsViewModel publicationCorrections,
            OuvrirSite ouvrirSite,
            OuvrirPassage ouvrirPassage,
            Optional<OuvrirAnalyse> ouvrirAnalyse,
            OuvrirMultisite ouvrirMultisite,
            MemoireFiltres memoire,
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

    /// Items du ☰ pilotés par le workflow / la source, regroupés ([MenuAudio.Items]).
    private MenuAudio.Items itemsMenu;

    /// Controller de la sous-vue `TableObservations.fxml`, injecté par le `fx:include` (#2745) : il
    /// possède la table, ses 23 colonnes et leur câblage. Le nom du champ est imposé par JavaFX, qui
    /// concatène le `fx:id` de l'inclusion (`tableau`) et le suffixe `Controller`.
    @FXML
    private TableObservationsController tableauController;

    @FXML
    private void initialize() {
        // La table, ses colonnes, son tri, sa multi-sélection et la revue au clavier vivent désormais
        // dans la sous-vue TableObservations.fxml (#2745). Ce controller n'en garde que ce qui a besoin
        // d'elle ET d'un nœud d'ici : panneau d'écoute, menu ☰, filtres, gestionnaire de colonnes.
        //
        // La sous-vue reçoit NOTRE modèle, elle ne se l'injecte pas : AudioViewModel est non-singleton
        // (« un VM frais par chargement d'écran »), et une injection lui en donnerait un second, vide.
        tableauController.installer(viewModel, appuis);
        TableView<LigneObservationAudio> table = tableauController.table();
        ActionsSelectionAudio actionsSelection = tableauController.actionsSelection();

        // « Voir sur la carte » rouvre l'analyse : masqué si la feature `analyse` est coupée (#1087).
        itemVoirCarte.setVisible(ouvrirAnalyse.isPresent());

        // Menu « Certitude » (#1139) : déclaration manuelle Sûr/Probable/Possible sur la sélection,
        // en miroir de la « Confiance observateur » du site (vide par défaut). Items et blocage câblés
        // dans MenuCertitude (classe dédiée, seuil de God Class).
        MenuCertitude.installer(menuCertitude, enveloppeCertitude, viewModel, actionsSelection);
        // Fil de discussion avec le validateur (#1417) : le panneau vit à droite du lecteur et suit la
        // sélection ; il ne s'ouvre que si la ligne porte réellement des messages. Câblage délégué
        // (PanneauDiscussion.installer), comme MenuCertitude : ce contrôleur est au plafond de NcssCount.
        PanneauDiscussion.installer(hoteDiscussion, table, viewModel, appuis.executeur());
        // Double-clic sur une observation → fiche de l'espèce (#1794), même cible que « Fiche de l'espèce »
        // du menu ☰. Le clic droit sélectionne la ligne survolée sans casser une sélection multiple.
        // Sur un taxon sans fiche (« Bruit », « Oiseau »), le motif part dans le bandeau plutôt que dans le
        // vide : le geste restait muet et passait pour cassé (#1834).
        DoubleClicLigne.installer(table, ligne -> actionsMenu.ouvrirFiche(ligne, viewModel::signaler));

        // Synchronisation de la sélection dans les deux sens, et items de fiche qui la suivent : déléguée
        // à SelectionTableAudio, comme MenuCertitude et PanneauDiscussion : ce contrôleur est au plafond
        // de NcssCount, et les deux écouteurs forment un tout (la garde d'égalité de l'un empêche la
        // boucle avec l'autre).
        SelectionTableAudio.installer(table, viewModel, actionsMenu, itemFicheEspece);

        // Barre de filtres « à la Notion » (#470/#471), mémoire de session (#484) et onglets de vues
        // mémorisées (#623) : assemblage délégué à FiltresVuesAudio, qui rend le gestionnaire (gardé pour
        // les navigations ciblées et le transport des filtres vers l'analyse).
        gestionnaireFiltres = FiltresVuesAudio.installer(
                new FiltresVuesAudio.Barre(champRecherche, menuAjoutFiltre, pucesFiltres, barreOnglets),
                table,
                viewModel,
                memoire,
                appuis.depotVues(),
                FEATURE,
                tableauController.marqueurEnjeu(),
                () -> tableauController.pourLeSelecteur());

        // #3752 : la liste enonce ce que `zonesStatutCourantes` lit VRAIMENT. Il manquait la source -
        // lue pour la zone gauche - et la liste filtree, lue pour le compteur d'especes a enjeu.
        // `bind()` evalue le calcul tout de suite, source encore nulle, donc `ZonesStatut.VIDE` : les
        // trois zones vides retirent la barre du layout, et rien ne la ramenait sur le chemin d'erreur
        // (`reinitialiser()` repose la MEME instance `ComptageAudio.VIDE`).
        //
        // C'est CE champ-ci qui est declare, et non `viewModel.sourceProperty()` : le ViewModel porte
        // un homonyme, pose dans le meme appel, qui aurait invalide au bon moment sans etre la valeur
        // lue. Un binding juste par coincidence est ce que le lot 3 passait son temps a defaire.
        zonesStatut.bind(Bindings.createObjectBinding(
                this::zonesStatutCourantes, source, viewModel.comptageProperty(), viewModel.observationsFiltrees()));

        // Panneau d'écoute : config AudioView (normalisations, expansion ×10, source, dispose) + repérage du
        // cri (#482) + métriques FME/fréq. terminale (#500) + options de lecture (#483). Détail dans le helper.
        PanneauEcouteAudio.installer(
                audioView,
                viewModel,
                table,
                tableauController.colonneFme(),
                tableauController.colonneFrequenceTerminale(),
                menuActions,
                reactifs);

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
                zoneImportVigieChiro,
                itemPublierCorrections,
                zonePublierCorrections,
                itemInclureMode,
                itemExporterVu,
                itemExporterObservations,
                itemExporterSons,
                itemExporterBiblio,
                itemOuvrirVigieChiro);
        MenuAudio.cabler(itemsMenu, viewModel, importVigieChiro, publicationCorrections, reactifs);

        // Ce que l'écran dit quand il n'a rien à montrer (état vide), quand une opération vient de se
        // terminer (bandeau de retour, #795) et quand l'audio n'est pas tout là (bandeau d'archive, #1301) :
        // trois messages de la même zone, câblés dans MessagesEcranAudio : ce contrôleur est au plafond
        // de NcssCount.
        MessagesEcranAudio.installer(
                tableauController.labelVide(), bandeauRetour, lblRetour, btnFermerRetour, lblBandeauArchive, viewModel);

        // Encart d'explication à la place du lecteur quand le fichier de la séquence sélectionnée n'est
        // plus sur disque (jamais un lecteur inerte, #1301).
        EncartsEcouteAudio.installer(
                audioView, encartAudioManquant, encartAudioDivergent, lblMotifDivergence, viewModel);

        // Glisser-déposer d'un CSV Tadarida sur l'écran : alternative au FileChooser natif (qui coince
        // parfois en devcontainer / bureau distant). Actif seulement pour la source workflow (ParPassage).
        DepotFichier.installer(
                racine, () -> source.get() != null && source.get().permetWorkflowTadarida(), this::deposerFichiers);

        // Gestion des colonnes (afficher/masquer + réordonner par glisser) : menu contextuel (clic droit)
        // et item « Colonnes… » du ☰ ouvrent le même panneau. La proposition Tadarida, colonne d'identité,
        // reste toujours affichée (visibilité verrouillée) mais peut être déplacée comme les autres.
        GestionnaireColonnes.installerEtPersister(
                table,
                menuActions,
                tableauController.pourLeSelecteur(),
                appuis.depotColonnes(),
                FEATURE,
                "principale",
                actionsMenu.itemOuvrirPassage(table),
                actionsMenu.itemFicheContexte(),
                new SeparatorMenuItem(),
                MenuValidationAudio.creer(table, actionsSelection, choixTaxon::getValue),
                actionsMenu.menuCopier(table));

        occupation = new IndicateurOccupation(hoteOccupation, appuis.executeur());
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
        this.source.set(Objects.requireNonNull(source, "source"));
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

    /// Adapte l'affichage à la source : colonnes de contexte masquées si la source est un unique
    /// passage
    /// ([ColonnesAudio#adapterAuContexte]) et items du menu « ☰ » propres à la source ([MenuAudio#adapter]).
    private void adapterAffichage(SourceObservations source) {
        ColonnesAudio.adapterAuContexte(tableauController.colonnes(), source.cibleUnPassageUnique());
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
                tableauController.table().getSelectionModel().select(ligne);
                tableauController.table().scrollTo(ligne);
                return;
            }
        }
    }

    /// Emplacement dans le fil d'Ariane, **piloté par la source**. Détail dans [ChromeAudio].
    @Override
    public List<Lieu> emplacement() {
        return ChromeAudio.emplacement(source.get(), ouvrirSite, ouvrirPassage, ouvrirAnalyse, ouvrirMultisite);
    }

    /// Zones de la **barre de statut**, dérivées de la source et du comptage. Détail dans [ChromeAudio].
    private ZonesStatut zonesStatutCourantes() {
        return ChromeAudio.zonesStatut(
                source.get(),
                viewModel.comptageProperty().get(),
                ComptageEnjeu.de(
                        viewModel.observationsFiltrees(),
                        ligne -> tableauController.marqueurEnjeu().aEnjeu(ligne.taxonRetenu())));
    }

    @Override
    public ReadOnlyObjectProperty<ZonesStatut> zonesStatutProperty() {
        return zonesStatut.getReadOnlyProperty();
    }

    @FXML
    private void valider() {
        tableauController.actionsSelection().valider();
    }

    @FXML
    private void corriger() {
        tableauController.actionsSelection().corriger(choixTaxon.getValue());
    }

    @FXML
    private void basculerReference() {
        tableauController.actionsSelection().basculerReference();
    }

    @FXML
    private void basculerDouteux() {
        tableauController.actionsSelection().basculerDouteux();
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
        actionsMenu.donneesVigieChiro().ouvrir(source.get());
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
                importVigieChiro,
                viewModel,
                source.get(),
                occupation,
                new DialogueProgression(appuis.executeur()),
                () -> tableauController.table().getScene().getWindow(),
                confirmateur(),
                demandeurParticipation());
    }

    /// Publie les corrections observateur du passage courant vers VigieChiro (#723). Délègue à
    /// [PublicationCorrectionsUI] (aperçu hors fil, confirmation récapitulative, envoi suivi, bilan).
    /// L'envoi peut commencer par rapatrier l'ancrage manquant (#1838) : il reçoit donc le même
    /// dialogue de progression annulable que l'import ci-dessus, et non le voile d'occupation.
    @FXML
    private void publierCorrections() {
        PublicationCorrectionsUI.lancer(
                publicationCorrections,
                source.get(),
                occupation,
                new DialogueProgression(appuis.executeur()),
                () -> tableauController.table().getScene().getWindow(),
                confirmateur());
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

    /// « Exporter les observations et les sons (ZIP) » (#2793) : sélecteur d'enregistrement, préparation
    /// sur le fil JavaFX (sonde de destination), puis écriture hors fil dans la modale de progression.
    @FXML
    private void exporterObservationsEtSons() {
        ExportSonsUI.lancer(
                viewModel,
                selecteur(),
                new DialogueProgression(appuis.executeur()),
                () -> tableauController.table().getScene().getWindow());
    }

    /// « Exporter la bibliothèque » : désignation de l'archive puis écriture dans la modale annulable,
    /// comme l'export « observations + sons » (harmonisation, clôture de l'EPIC #2790).
    @FXML
    private void exporterBibliotheque() {
        ExportSonsUI.lancerBibliotheque(
                viewModel,
                selecteur(),
                new DialogueProgression(appuis.executeur()),
                () -> tableauController.table().getScene().getWindow());
    }
}

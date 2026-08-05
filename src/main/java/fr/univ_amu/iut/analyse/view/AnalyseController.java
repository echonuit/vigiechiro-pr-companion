package fr.univ_amu.iut.analyse.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.analyse.viewmodel.Regroupement;
import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.EspeceIdentifiee;
import fr.univ_amu.iut.commun.view.ActionFicheEspece;
import fr.univ_amu.iut.commun.view.BandeauRetour;
import fr.univ_amu.iut.commun.view.ClesCriteres;
import fr.univ_amu.iut.commun.view.DescripteurFiltre;
import fr.univ_amu.iut.commun.view.DoubleClicLigne;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.GestionnaireVues;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.MemoireFiltres;
import fr.univ_amu.iut.commun.view.MenuCopier;
import fr.univ_amu.iut.commun.view.MenuLigne;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.view.SelecteurFichierJavaFx;
import fr.univ_amu.iut.commun.view.SelecteurFichierModifiable;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.ResteDeRestauration;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import fr.univ_amu.iut.validation.model.MarqueurEspecesAEnjeu;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

/// Controller de l'écran **« Espèces & observations »** (`Analyse.fxml`). Pur câblage : lie les deux
/// tables (inventaire par espèce / par carré), le sélecteur de regroupement et le filtre de statut à
/// l'[AnalyseViewModel]. La table affichée suit le regroupement ; le chargement initial est déclenché ici
/// (écran sans paramètre). Aucun accès base de données (règle ArchUnit `view_sans_jdbc`).
///
/// Implémente [RafraichirAuRetour] : l'écran reste vivant dans l'historique du [Navigateur] ; quand on y
/// revient après avoir modifié des observations ailleurs (validation d'un passage…), l'inventaire est
/// rechargé pour ne pas afficher des compteurs périmés.
public class AnalyseController implements RafraichirAuRetour, ResumeStatut {

    /// Zones de la barre de statut (#1023) : agrégat top-level → **centre** = résumé de l'inventaire,
    /// **droite** = état d'export quand un export a été produit ; la gauche reste au défaut du chrome.
    /// Mémoire de session (#3098) : les filtres et le tri survivent à une sortie de l'écran.
    private final MemoireFiltres memoire;

    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    /// Clé de la feature pour les vues mémorisées (`saved_filter_view.feature`) : isole les vues de cet écran.
    private static final String FEATURE = "analyse";

    private final AnalyseViewModel viewModel;
    private final OuvrirPassage ouvrirPassage;
    private final OuvrirAudio ouvrirAudio;
    private final DepotVues depotVues;
    private final DepotDispositionColonnes depotColonnes;

    /// Action réutilisable « Fiche de l'espèce » (#846) : configure l'item du menu contextuel de la table
    /// des espèces selon la ligne sélectionnée et ouvre la fiche dans le navigateur.
    private final ActionFicheEspece actionFicheEspece;
    private final ExecuteurTache executeur;
    private IndicateurOccupation occupation;

    /// Désignation du fichier d'export : porteur partagé injectable (#1431), double répondant en test.
    /// Un `FileChooser` en dur **figeait** tout test de l'export - ce que la Javadoc de [#exporter]
    /// avouait sans détour (« le dialog vit dans la vue, non testé en TestFX »).
    private final SelecteurFichierModifiable selecteur = new SelecteurFichierModifiable(
            // `this.boutonExporter` : le champ @FXML est déclaré plus bas (référence en avant interdite
            // dans un initialiseur). La fenêtre n'est lue qu'au clic.
            new SelecteurFichierJavaFx(() -> this.boutonExporter.getScene().getWindow()));

    /// Porteur de désignation exposé aux tests (#1431) : `selecteur().definir(double)`.
    SelecteurFichierModifiable selecteur() {
        return selecteur;
    }

    /// Item « Fiche de l'espèce » du menu contextuel de [#tableEspeces], reconfiguré à chaque sélection.
    private MenuItem itemFicheEspece;

    /// Item « Fiche de l'espèce » du menu contextuel de [#tableObservations] (#1795) : même cible (l'espèce
    /// sélectionnée), instance distincte car un [MenuItem] n'appartient qu'à un seul menu.
    private MenuItem itemFicheEspeceObs;

    /// Sélecteur de colonnes des trois tables (extrait, #914/#994) : câble clic droit + ☰ et fournit
    /// l'adaptateur qui capture/rejoue les colonnes dans les vues mémorisées.
    private SelecteurColonnesAnalyse selecteurColonnes;

    /// État de la bascule Tableau ⇄ Carte (vue, pas de domaine) ; la carte elle-même est gérée par
    /// [CarteRepartition], installée **paresseusement** au premier affichage (`null` tant qu'on reste en
    /// tableau).
    private final BooleanProperty carteAffichee = new SimpleBooleanProperty(this, "carteAffichee", false);

    /// Richesse (nombre d'espèces distinctes) par numéro de carré, tenue à jour depuis l'inventaire par
    /// carré, pour afficher la richesse du carré de chaque observation du détail (lien avec la carte).
    private final RichesseParCarre richesseParCarre = new RichesseParCarre();

    @FXML
    private StackPane zoneCarte;

    @FXML
    private Button boutonCarte;

    /// Icône du bouton de bascule : carte quand la table est affichée, table quand la carte l'est.
    @FXML
    private FontIcon iconeCarte;

    @FXML
    private Label lblResume;

    @FXML
    private Label lblMessage;

    @FXML
    private StackPane hoteOccupation;

    @FXML
    private ComboBox<Regroupement> choixRegroupement;

    @FXML
    private TextField champRecherche;

    @FXML
    private MenuButton menuAjoutFiltre;

    /// Menu ☰ « outils » (#916) : porte l'entrée « Colonnes… » (le clic droit de la table la porte aussi).
    @FXML
    private MenuButton menuOutils;

    @FXML
    private FlowPane pucesFiltres;

    /// Conteneur des onglets de vues mémorisées (`GestionnaireVues`, #623).
    @FXML
    private FlowPane barreOnglets;

    /// Barre de filtres « à la Notion » (#537, étape 6) : pilote le socle `Filtres` du ViewModel (statut,
    /// taxon parent #518, recherche texte). Construite dans [#initialize()].
    private GestionnaireFiltres<ObservationAnalyse> gestionnaireFiltres;

    @FXML
    private Button boutonExporter;

    @FXML
    private Label lblRetour;

    @FXML
    private HBox bandeauRetour;

    @FXML
    private Button btnFermerRetour;

    @FXML
    private TableView<EspeceAgregee> tableEspeces;

    @FXML
    private TableColumn<EspeceAgregee, String> colEspece;

    @FXML
    private TableColumn<EspeceAgregee, String> colGroupe;

    @FXML
    private TableColumn<EspeceAgregee, String> colDetections;

    @FXML
    private TableColumn<EspeceAgregee, String> colPassages;

    @FXML
    private TableColumn<EspeceAgregee, String> colCarres;

    @FXML
    private TableColumn<EspeceAgregee, String> colPoints;

    @FXML
    private TableColumn<EspeceAgregee, String> colPeriode;

    @FXML
    private TableView<CarreEspeces> tableCarres;

    @FXML
    private TableColumn<CarreEspeces, String> colCarre;

    @FXML
    private TableColumn<CarreEspeces, String> colSite;

    @FXML
    private TableColumn<CarreEspeces, String> colRichesse;

    @FXML
    private TableColumn<CarreEspeces, String> colDetectionsCarre;

    @FXML
    private TableColumn<CarreEspeces, String> colPeriodeCarre;

    @FXML
    private SplitPane separateur;

    @FXML
    private VBox panneauDetail;

    /// Controller de la sous-vue `DetailObservations.fxml`, injecté par le `fx:include` (#2745) : il
    /// possède les quinze champs du panneau et leur câblage. Le nom est imposé par JavaFX, qui
    /// concatène le `fx:id` de l'inclusion (`panneauDetail`) et le suffixe `Controller`.
    @FXML
    private DetailObservationsController panneauDetailController;

    /// Repère des **espèces à enjeu** (#2353) : lu une fois à la construction, le référentiel ne bougeant
    /// pas en cours de session. Partagé par la cellule d'espèce et le critère de filtre.
    private final MarqueurEspecesAEnjeu marqueurEnjeu;

    @Inject
    public AnalyseController(
            AnalyseViewModel viewModel,
            MemoireFiltres memoire,
            OuvrirPassage ouvrirPassage,
            OuvrirAudio ouvrirAudio,
            DepotVues depotVues,
            DepotDispositionColonnes depotColonnes,
            ActionFicheEspece actionFicheEspece,
            ExecuteurTache executeur,
            EspecesPrioritaires especesPrioritaires) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.memoire = Objects.requireNonNull(memoire, "memoire");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvrirAudio = Objects.requireNonNull(ouvrirAudio, "ouvrirAudio");
        this.depotVues = Objects.requireNonNull(depotVues, "depotVues");
        this.depotColonnes = Objects.requireNonNull(depotColonnes, "depotColonnes");
        this.actionFicheEspece = Objects.requireNonNull(actionFicheEspece, "actionFicheEspece");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
        this.marqueurEnjeu = new MarqueurEspecesAEnjeu(especesPrioritaires);
    }

    @Override
    public ReadOnlyObjectProperty<ZonesStatut> zonesStatutProperty() {
        return zonesStatut.getReadOnlyProperty();
    }

    @FXML
    private void initialize() {
        // Densité/habillage de table uniformes (#690). La table des observations est navigable (double-clic
        // → écoute, #792) ; les tables espèces/carrés ne servent qu'à la sélection.
        TableDonnees.uniformiser(tableEspeces);
        TableDonnees.uniformiser(tableCarres);
        TableDonnees.uniformiserNavigable(panneauDetailController.table());
        configurerColonnes();
        tableEspeces.setItems(viewModel.especes());
        tableCarres.setItems(viewModel.carres());

        // Sélecteur de colonnes des **trois** tables de l'analyse (EPIC #914), extrait dans
        // SelecteurColonnesAnalyse : clic droit « Colonnes… » par table (celui des espèces reçoit en plus
        // « Fiche de l'espèce », #848/#916, reconfiguré à chaque sélection plus bas), ☰ « outils » pilotant la
        // table maître visible, et adaptateur pour la capture dans les vues mémorisées (#994). Un clic droit
        // sélectionne d'abord la ligne visée pour que la fiche porte bien sur elle.
        itemFicheEspece = new MenuItem();
        itemFicheEspeceObs = new MenuItem();
        selecteurColonnes = new SelecteurColonnesAnalyse(
                tableEspeces,
                tableCarres,
                panneauDetailController.table(),
                menuOutils,
                () -> viewModel.regroupementProperty().get());
        selecteurColonnes.installer(
                List.of(
                        itemFicheEspece,
                        MenuCopier.creer(
                                tableEspeces,
                                new MenuCopier.Entree<>("Nom latin", EspeceAgregee::nomLatin),
                                new MenuCopier.Entree<>("Nom vernaculaire", EspeceAgregee::nomVernaculaireFr))),
                List.of(
                        MenuLigne.item("Écouter", panneauDetailController.table(), this::ecouter),
                        MenuLigne.item("Ouvrir le passage", panneauDetailController.table(), this::ouvrirPassageDe),
                        itemFicheEspeceObs,
                        MenuCopier.creer(
                                panneauDetailController.table(),
                                new MenuCopier.Entree<>("Carré", ObservationEspece::numeroCarre))));
        selecteurColonnes.persister(depotColonnes, FEATURE);
        // Clic droit : sélectionne la ligne (cible du menu contextuel). Double-clic : ouvre la fiche de
        // l'espèce, même cible que l'item « Fiche de l'espèce » du menu (#1794).
        // Sur un taxon sans fiche (« Bruit », « Oiseau », couple sans binôme), le motif part dans le bandeau
        // plutôt que dans le vide : le geste restait muet et passait pour cassé (#1837).
        DoubleClicLigne.installer(
                tableEspeces, espece -> actionFicheEspece.ouvrirOuSignaler(especeDe(espece), viewModel::signaler));
        configurerFiches(especeDe(null));

        // Sélecteur de regroupement (pivot espèce ↔ lieu).
        choixRegroupement.getItems().setAll(Regroupement.values());
        choixRegroupement.setConverter(convertisseur(r -> r == null ? "" : r.libelle()));
        choixRegroupement.valueProperty().bindBidirectional(viewModel.regroupementProperty());

        // Barre de filtres « à la Notion » (#537, étape 6) : Statut et Taxon parent (#518) en puces
        // ajoutables, recherche texte permanente. La barre pilote directement le socle Filtres du ViewModel
        // (le regroupement, lui, reste un contrôle fixe : c'est un pivot d'agrégation, pas un filtre).
        gestionnaireFiltres = new GestionnaireFiltres<>(
                champRecherche,
                menuAjoutFiltre,
                pucesFiltres,
                viewModel.filtres(),
                List.of(
                        CriteresAnalyse.statut(),
                        // Cascadage (#3095) : le domaine se calcule sur les lignes que les AUTRES
                        // criteres laissent passer. Lire la liste deja filtree ferait s auto-effondrer
                        // la puce, qui n offrirait plus que la valeur deja retenue.
                        CriteresAnalyse.groupe(() ->
                                CriteresAnalyse.groupesDe(viewModel.filtres().saufLui(ClesCriteres.GROUPE))),
                        CriteresAnalyse.natureNuit(viewModel::nuitsOpportunistes),
                        CriteresAnalyse.lieu(() -> viewModel.filtres().saufLui(ClesCriteres.LIEU)),
                        CriteresAnalyse.aEnjeu(observation -> marqueurEnjeu.aEnjeu(observation.taxonRetenu()))),
                CriteresAnalyse.rechercheTexte());
        // Onglets de vues mémorisées (#623) : vues par défaut (lecture seule) + vues de l'utilisateur. La vue
        // capture aussi la disposition des colonnes des trois tables (#994), via l'adaptateur du sélecteur.
        GestionnaireVues.avecDialogue(
                        barreOnglets,
                        gestionnaireFiltres,
                        depotVues,
                        FEATURE,
                        CriteresAnalyse.vuesParDefaut(),
                        selecteurColonnes.adaptateur())
                // Une vue rejouée amputée de valeurs disparues filtre moins large qu'annoncé (#3056).
                .surRestauration(viewModel::signalerVueAmputee);

        // Mémoire de session (#484, étendue à cet écran en #3098) : les filtres, plus le tri de CHACUNE
        // des trois tables. Une mémoire qui n'en aurait retenu qu'une aurait choisi laquelle sans le dire.
        memoire.installer(FEATURE, tableEspeces, gestionnaireFiltres, viewModel::signalerFiltresDeSessionAmputes);
        memoire.memoriserTri(FEATURE, tableEspeces);
        memoire.memoriserTri(FEATURE, tableCarres);
        memoire.memoriserTri(FEATURE, panneauDetailController.table());

        // Bandeau de retour (export, échec de chargement, action refusée), mutualisé avec Sons & validation
        // (#1837) : libellé, visibilité, couleur de sévérité et croix de fermeture.
        BandeauRetour.installer(
                bandeauRetour, lblRetour, btnFermerRetour, viewModel.retourProperty(), viewModel::effacerRetour);

        // En mode Tableau, la table visible suit le regroupement ; en mode Carte, les deux tables
        // s'effacent au profit de la carte de répartition.
        var parEspece = viewModel.regroupementProperty().isEqualTo(Regroupement.PAR_ESPECE);
        var tableauAffiche = carteAffichee.not();
        lierVisibilite(tableEspeces, parEspece.and(tableauAffiche));
        lierVisibilite(tableCarres, parEspece.not().and(tableauAffiche));
        lierVisibilite(zoneCarte, carteAffichee);
        configurerCarte();

        lblResume.textProperty().bind(viewModel.resumeProperty());
        // Barre de statut (#1023) : centre = résumé de l'inventaire ; droite = état d'export (message
        // présent seulement après une génération d'export). Agrégat top-level → gauche au défaut.
        zonesStatut.bind(Bindings.createObjectBinding(
                () -> ZonesStatut.centreEtDroite(
                        viewModel.resumeProperty().get(),
                        viewModel.retourProperty().get().texte()),
                viewModel.resumeProperty(),
                viewModel.retourProperty()));

        // Message d'état vide : ni espèce ni carré (aucune observation exploitable).
        var vide = Bindings.createBooleanBinding(
                () -> viewModel.especes().isEmpty() && viewModel.carres().isEmpty(),
                viewModel.especes(),
                viewModel.carres());
        lblMessage.setText("Aucune observation à analyser pour le moment. Importez et validez des nuits"
                + " (résultats Tadarida) pour voir apparaître vos espèces ici.");
        lblMessage.visibleProperty().bind(vide);
        lblMessage.managedProperty().bind(vide);

        configurerDetail();

        // La colonne « Espèces du carré » du détail lit la richesse depuis l'inventaire par carré : le
        // collaborateur se tient à jour à chaque changement de cet inventaire (chargement, filtre statut).
        richesseParCarre.brancher(viewModel.carresCarte(), panneauDetailController.table());

        occupation = new IndicateurOccupation(hoteOccupation, executeur);
        chargerObservations();
    }

    /// Charge l'inventaire **hors du fil JavaFX** (#1208) : la requête base part en arrière-plan sous
    /// l'overlay « … en cours », puis l'application des résultats (ou de l'erreur, filet #795) revient
    /// sur le fil JavaFX. Utilisé au premier affichage et à chaque retour sur l'écran.
    private void chargerObservations() {
        occupation.occuper(
                "Chargement des observations…",
                viewModel::chargerObservations,
                viewModel::appliquer,
                viewModel::signalerErreur);
    }

    /// Câble le panneau **détail** (maître-détail) : la sélection d'une espèce dans l'inventaire charge ses
    /// observations à travers les passages ; double-clic ou bouton « Ouvrir le passage » navigue vers
    /// M-Passage (contrat socle [OuvrirPassage], aucune dépendance vers `passage.view`).
    private void configurerDetail() {
        // Le panneau lui-même est une sous-vue (#2745) : titre, placeholder, colonnes et activation des
        // deux boutons y vivent. Elle reçoit NOS appuis et n'injecte rien, le ViewModel étant
        // non-singleton. Les trois gestes lui arrivent en fonctions : ils ont besoin de la sélection de
        // l'inventaire et de la source audio, qui se décident ici.
        panneauDetailController.installer(
                viewModel,
                this::richesseDuCarre,
                this::ecouter,
                this::ouvrirPassageDe,
                () -> actionFicheEspece.ouvrirOuSignaler(
                        especeDe(tableEspeces.getSelectionModel().getSelectedItem()), viewModel::signaler));

        // Le panneau détail n'a de sens qu'en regroupement Par espèce : on le retire du SplitPane en Par
        // carré pour rendre toute la hauteur à la table des carrés (plutôt qu'un placeholder inutile).
        viewModel.regroupementProperty().addListener((obs, ancien, regroupement) -> afficherDetail(regroupement));
        afficherDetail(viewModel.regroupementProperty().get());

        // La ligne sélectionnée de l'inventaire pilote le détail (null en Par carré → détail vidé) et la
        // cible du menu contextuel « Fiche de l'espèce » (#848).
        tableEspeces.getSelectionModel().selectedItemProperty().addListener((obs, ancien, espece) -> {
            viewModel.selectionnerEspece(espece, statutCourant());
            configurerFiches(especeDe(espece));
        });
    }

    /// L'espèce ciblée par « Fiche de l'espèce » : code, nom latin et nom vernaculaire de la ligne
    /// d'inventaire. La projection portant le nom latin, le repli GBIF s'applique aussi aux taxons hors
    /// PNA (oiseaux, orthoptères…). `null` (aucune ligne sélectionnée) → espèce vide, item désactivé.
    private static EspeceIdentifiee especeDe(EspeceAgregee espece) {
        return espece == null
                ? new EspeceIdentifiee(null, null, null)
                : new EspeceIdentifiee(espece.code(), espece.nomLatin(), espece.nomVernaculaireFr());
    }

    /// Reconfigure les deux items « Fiche de l'espèce » (table des espèces et détail des observations, #1795)
    /// sur la même `espece` : un seul point pour garder les deux menus contextuels alignés sur la sélection.
    private void configurerFiches(EspeceIdentifiee espece) {
        actionFicheEspece.configurer(itemFicheEspece, espece);
        actionFicheEspece.configurer(itemFicheEspeceObs, espece);
    }

    /// Affiche le panneau détail (et restaure la position du séparateur) en regroupement **Par espèce**,
    /// le retire du `SplitPane` sinon : la table des carrés récupère alors toute la hauteur.
    private void afficherDetail(Regroupement regroupement) {
        boolean parEspece = regroupement == Regroupement.PAR_ESPECE;
        if (parEspece && !separateur.getItems().contains(panneauDetail)) {
            separateur.getItems().add(panneauDetail);
            separateur.setDividerPositions(0.58);
        } else if (!parEspece) {
            separateur.getItems().remove(panneauDetail);
        }
    }

    /// Câble la **carte de répartition** (déléguée à [CarteRepartition]) de façon **paresseuse** : le
    /// composant carte (et sa dépendance Gluon Maps) n'est créé/installé qu'au **premier** passage en mode
    /// Carte, pour garder l'écran d'inventaire léger tant qu'on reste en tableau.
    private void configurerCarte() {
        BasculeCarteUI.cabler(
                boutonCarte,
                iconeCarte,
                carteAffichee,
                zoneCarte,
                () -> new CarteRepartition(
                        viewModel.carresCarte(), viewModel.carresEspeceSelectionnee(), carteAffichee));
    }

    /// « 🗺️ Carte » / « 📋 Tableau » : bascule l'affichage de la zone maître entre l'inventaire et la carte
    /// (le libellé du bouton est **lié** à [#carteAffichee], cf. [#configurerCarte]).
    @FXML
    private void basculerCarte() {
        carteAffichee.set(!carteAffichee.get());
    }

    /// Rechargé par le [fr.univ_amu.iut.commun.view.Navigateur] au **retour** sur l'écran : des
    /// observations ont pu être validées/corrigées entre-temps, l'inventaire est donc ré-interrogé.
    @Override
    public void rafraichirAuRetour() {
        chargerObservations();
    }

    /// Rejoue un descripteur de filtres transporté depuis une autre vue (« Voir sur la carte » depuis
    /// l'audio, #476) et bascule éventuellement sur la carte. Le socle
    /// [GestionnaireFiltres#restaurer(DescripteurFiltre)] ne pose que les critères que l'analyse
    /// **offre** : les critères partagés (statut, groupe, lieu, espèces à enjeu) et la recherche texte.
    ///
    /// Ce qu'il ne sait pas reprendre est **annoncé** (#3093) et non plus jeté en silence. C'est ici que
    /// l'écart est le plus large de toute l'application : Sons & validation offre dix critères, l'analyse
    /// cinq. Resserrer sur « les Rhinolophes au-dessus de 90 % » puis basculer sur la carte donne donc
    /// une carte de **toutes** les probabilités, et rien ne le disait.
    ///
    /// @param filtres descripteur à rejouer, ou `null` pour ne rien changer aux filtres
    /// @param afficherCarte `true` pour basculer sur la carte de répartition
    public void appliquer(DescripteurFiltre filtres, boolean afficherCarte) {
        if (filtres != null) {
            ResteDeRestauration reste = gestionnaireFiltres.restaurer(filtres);
            if (!reste.estVide()) {
                viewModel.signalerFiltresNonRepris(reste);
            }
        }
        carteAffichee.set(afficherCarte);
    }

    /// « Tout effacer » (#3098) : retire tous les filtres, remet le tri à plat, et **oublie la mémoire
    /// de session** de cet écran.
    ///
    /// Ce dernier point n'est pas accessoire. Sans lui, le geste viderait l'écran et la mémoire
    /// remettrait tout à la visite suivante : le bouton paraîtrait ne pas avoir pris.
    @FXML
    private void toutEffacer() {
        gestionnaireFiltres.reinitialiser();
        tableEspeces.getSortOrder().clear();
        tableCarres.getSortOrder().clear();
        panneauDetailController.table().getSortOrder().clear();
        memoire.oublier(FEATURE);
    }
    /// « 📤 Exporter… » : demande où écrire, puis délègue au ViewModel l'écriture CSV de l'inventaire
    /// **affiché** (la liste filtrée courante, pas l'inventaire complet). La désignation passe par le port
    /// [SelecteurFichier] (#1431) : le geste est donc **jouable** dans un test, ce qu'il n'était pas.
    @FXML
    private void exporter() {
        selecteur
                .enregistrerFichier(
                        "Exporter l'inventaire des espèces en CSV", "inventaire-especes.csv", FiltreFichier.csv())
                .ifPresent(viewModel::exporter);
    }

    private void ouvrirPassageDe(ObservationEspece observation) {
        ouvrirPassage.ouvrir(
                observation.idPassage(),
                new ContexteSite(observation.numeroCarre(), observation.codePoint(), observation.nomSite()));
    }

    /// Ouvre la vue audio sur `observation` (écoute + valider/corriger/référence). Partagé par le bouton
    /// « Écouter » et le double-clic sur une ligne. Sans effet si `observation` est nulle.
    private void ecouter(ObservationEspece observation) {
        if (observation != null) {
            // L'espèce de la source est l'espèce sélectionnée (détenue par le ViewModel) : le détail, donc
            // l'observation cliquée, n'existe que pour une espèce sélectionnée.
            ouvrirAudio.ouvrir(viewModel.sourceAudioEspece(statutCourant()), observation.idObservation());
        }
    }

    private void configurerColonnes() {
        ColonnesAnalyse.especes(
                new ColonnesAnalyse.Especes(
                        colEspece, colGroupe, colDetections, colPassages, colCarres, colPoints, colPeriode),
                marqueurEnjeu);
        ColonnesAnalyse.carres(
                new ColonnesAnalyse.Carres(colCarre, colSite, colRichesse, colDetectionsCarre, colPeriodeCarre));
        // Les colonnes du détail sont câblées par sa sous-vue, qui les possède (#2745).
    }

    /// Libellé du passage d'une observation : date d'enregistrement et n° de passage (`2026-06-22 · n°2`).
    /// Richesse (nb d'espèces distinctes) du carré `numeroCarre`, ou `—` si inconnue de l'inventaire.
    private String richesseDuCarre(String numeroCarre) {
        return richesseParCarre.libelle(numeroCarre);
    }

    /// Statut de revue actuellement filtré par la barre à puces (`null` si aucune puce « Statut » active),
    /// lu sur le **descripteur** de la barre. Garde le détail et la source audio cohérents avec l'inventaire
    /// (#537, étape 6) sans exposer de propriété de filtre côté ViewModel : la barre est l'unique source.
    private StatutObservation statutCourant() {
        return gestionnaireFiltres.decrire().criteres().stream()
                .filter(critere -> "statut".equals(critere.nom()))
                .flatMap(critere -> critere.valeurs().stream())
                .findFirst()
                .map(StatutObservation::valueOf)
                .orElse(null);
    }

    private static ObservableValue<String> texte(Object valeur) {
        return new ReadOnlyStringWrapper(String.valueOf(valeur));
    }

    private static void lierVisibilite(Node noeud, ObservableValue<Boolean> visible) {
        noeud.visibleProperty().bind(visible);
        noeud.managedProperty().bind(visible);
    }

    private static <T> StringConverter<T> convertisseur(Function<T, String> versTexte) {
        return new StringConverter<>() {
            @Override
            public String toString(T valeur) {
                return versTexte.apply(valeur);
            }

            @Override
            public T fromString(String libelle) {
                return null;
            }
        };
    }
}

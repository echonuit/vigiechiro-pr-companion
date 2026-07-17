package fr.univ_amu.iut.analyse.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.analyse.viewmodel.Regroupement;
import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.EspeceIdentifiee;
import fr.univ_amu.iut.commun.view.ActionFicheEspece;
import fr.univ_amu.iut.commun.view.ColonneBadge;
import fr.univ_amu.iut.commun.view.DescripteurFiltre;
import fr.univ_amu.iut.commun.view.DoubleClicLigne;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.GestionnaireVues;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.view.SelecteurFichierJavaFx;
import fr.univ_amu.iut.commun.view.SelecteurFichierModifiable;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

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

    /// Sélecteur de colonnes des trois tables (extrait, #914/#994) : câble clic droit + ☰ et fournit
    /// l'adaptateur qui capture/rejoue les colonnes dans les vues mémorisées.
    private SelecteurColonnesAnalyse selecteurColonnes;

    /// État de la bascule Tableau ⇄ Carte (vue, pas de domaine) ; la carte elle-même est gérée par
    /// [CarteRepartition], installée **paresseusement** au premier affichage (`null` tant qu'on reste en
    /// tableau).
    private final BooleanProperty carteAffichee = new SimpleBooleanProperty(this, "carteAffichee", false);
    private CarteRepartition carteRepartition;

    /// Richesse (nombre d'espèces distinctes) par numéro de carré, tenue à jour depuis l'inventaire par
    /// carré, pour afficher la richesse du carré de chaque observation du détail (lien avec la carte).
    private final RichesseParCarre richesseParCarre = new RichesseParCarre();

    @FXML
    private StackPane zoneCarte;

    @FXML
    private Button boutonCarte;

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
    private Label lblExport;

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

    @FXML
    private Label lblDetailTitre;

    @FXML
    private Label lblDetailVide;

    @FXML
    private Button boutonOuvrirPassage;

    @FXML
    private Button boutonEcouter;

    /// Enveloppes (non désactivées) des actions du détail : portent le tooltip d'explication du blocage,
    /// qu'un Button désactivé n'affiche pas. Cf. [IndicateurBlocage] (#789).
    @FXML
    private StackPane enveloppeEcouter;

    @FXML
    private StackPane enveloppeOuvrirPassage;

    @FXML
    private TableView<ObservationEspece> tableObservations;

    @FXML
    private TableColumn<ObservationEspece, String> colObsPassage;

    @FXML
    private TableColumn<ObservationEspece, String> colObsCarre;

    @FXML
    private TableColumn<ObservationEspece, String> colObsRichesse;

    @FXML
    private TableColumn<ObservationEspece, String> colObsPoint;

    @FXML
    private TableColumn<ObservationEspece, String> colObsTadarida;

    @FXML
    private TableColumn<ObservationEspece, String> colObsObservateur;

    @FXML
    private TableColumn<ObservationEspece, String> colObsStatut;

    @Inject
    public AnalyseController(
            AnalyseViewModel viewModel,
            OuvrirPassage ouvrirPassage,
            OuvrirAudio ouvrirAudio,
            DepotVues depotVues,
            DepotDispositionColonnes depotColonnes,
            ActionFicheEspece actionFicheEspece,
            ExecuteurTache executeur) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvrirAudio = Objects.requireNonNull(ouvrirAudio, "ouvrirAudio");
        this.depotVues = Objects.requireNonNull(depotVues, "depotVues");
        this.depotColonnes = Objects.requireNonNull(depotColonnes, "depotColonnes");
        this.actionFicheEspece = Objects.requireNonNull(actionFicheEspece, "actionFicheEspece");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
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
        TableDonnees.uniformiserNavigable(tableObservations);
        configurerColonnes();
        tableEspeces.setItems(viewModel.especes());
        tableCarres.setItems(viewModel.carres());

        // Sélecteur de colonnes des **trois** tables de l'analyse (EPIC #914), extrait dans
        // SelecteurColonnesAnalyse : clic droit « Colonnes… » par table (celui des espèces reçoit en plus
        // « Fiche de l'espèce », #848/#916, reconfiguré à chaque sélection plus bas), ☰ « outils » pilotant la
        // table maître visible, et adaptateur pour la capture dans les vues mémorisées (#994). Un clic droit
        // sélectionne d'abord la ligne visée pour que la fiche porte bien sur elle.
        itemFicheEspece = new MenuItem();
        selecteurColonnes = new SelecteurColonnesAnalyse(
                tableEspeces,
                tableCarres,
                tableObservations,
                menuOutils,
                () -> viewModel.regroupementProperty().get());
        selecteurColonnes.installer(itemFicheEspece);
        selecteurColonnes.persister(depotColonnes, FEATURE);
        // Clic droit : sélectionne la ligne (cible du menu contextuel). Double-clic : ouvre la fiche de
        // l'espèce, même cible que l'item « Fiche de l'espèce » du menu (#1794).
        DoubleClicLigne.installer(tableEspeces, espece -> actionFicheEspece.ouvrir(especeDe(espece)));
        actionFicheEspece.configurer(itemFicheEspece, especeDe(null));

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
                List.of(CriteresAnalyse.statut(), CriteresAnalyse.groupe(viewModel::groupesDisponibles)),
                CriteresAnalyse.rechercheTexte());
        // Onglets de vues mémorisées (#623) : vues par défaut (lecture seule) + vues de l'utilisateur. La vue
        // capture aussi la disposition des colonnes des trois tables (#994), via l'adaptateur du sélecteur.
        GestionnaireVues.avecDialogue(
                barreOnglets,
                gestionnaireFiltres,
                depotVues,
                FEATURE,
                CriteresAnalyse.vuesParDefaut(),
                selecteurColonnes.adaptateur());

        // Message d'export.
        var exportPresent = viewModel.messageProperty().isNotEmpty();
        lblExport.textProperty().bind(viewModel.messageProperty());
        lblExport.visibleProperty().bind(exportPresent);
        lblExport.managedProperty().bind(exportPresent);

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
                        viewModel.messageProperty().get()),
                viewModel.resumeProperty(),
                viewModel.messageProperty()));

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
        richesseParCarre.brancher(viewModel.carresCarte(), tableObservations);

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
        tableObservations.setItems(viewModel.observations());

        // Le panneau détail n'a de sens qu'en regroupement Par espèce : on le retire du SplitPane en Par
        // carré pour rendre toute la hauteur à la table des carrés (plutôt qu'un placeholder inutile).
        viewModel.regroupementProperty().addListener((obs, ancien, regroupement) -> afficherDetail(regroupement));
        afficherDetail(viewModel.regroupementProperty().get());

        // La ligne sélectionnée de l'inventaire pilote le détail (null en Par carré → détail vidé) et la
        // cible du menu contextuel « Fiche de l'espèce » (#848).
        tableEspeces.getSelectionModel().selectedItemProperty().addListener((obs, ancien, espece) -> {
            viewModel.selectionnerEspece(espece, statutCourant());
            actionFicheEspece.configurer(itemFicheEspece, especeDe(espece));
        });

        lblDetailTitre.textProperty().bind(viewModel.detailTitreProperty());

        // Placeholder tant qu'aucune observation n'est listée (aucune espèce sélectionnée).
        var detailVide = Bindings.isEmpty(viewModel.observations());
        lblDetailVide.visibleProperty().bind(detailVide);
        lblDetailVide.managedProperty().bind(detailVide);

        // Actions du détail actives seulement quand une observation est sélectionnée.
        var selection = tableObservations.getSelectionModel().selectedItemProperty();
        boutonOuvrirPassage.disableProperty().bind(selection.isNull());
        boutonEcouter.disableProperty().bind(selection.isNull());
        // Explique le grisage (#789) sur les enveloppes (un Button désactivé n'affiche pas de tooltip).
        IndicateurBlocage.expliquer(
                enveloppeEcouter,
                Bindings.when(selection.isNull())
                        .then("Sélectionnez une observation dans le tableau pour l'écouter et la valider.")
                        .otherwise("Écouter l'observation sélectionnée et la valider."));
        IndicateurBlocage.expliquer(
                enveloppeOuvrirPassage,
                Bindings.when(selection.isNull())
                        .then("Sélectionnez une observation dans le tableau pour ouvrir son passage.")
                        .otherwise("Ouvrir le passage de l'observation sélectionnée."));

        // Double-clic sur une observation → fiche de l'espèce (#1794). Toutes les observations du détail
        // portent la même espèce ; seule l'agrégée sélectionnée porte le nom latin/vernaculaire, donc c'est
        // elle qu'on ouvre. L'écoute d'une détection reste le bouton « Écouter » et « Ouvrir le passage » le
        // sien.
        DoubleClicLigne.installer(
                tableObservations,
                observation -> actionFicheEspece.ouvrir(
                        especeDe(tableEspeces.getSelectionModel().getSelectedItem())));
    }

    /// L'espèce ciblée par « Fiche de l'espèce » : code, nom latin et nom vernaculaire de la ligne
    /// d'inventaire. La projection portant le nom latin, le repli GBIF s'applique aussi aux taxons hors
    /// PNA (oiseaux, orthoptères…). `null` (aucune ligne sélectionnée) → espèce vide, item désactivé.
    private static EspeceIdentifiee especeDe(EspeceAgregee espece) {
        return espece == null
                ? new EspeceIdentifiee(null, null, null)
                : new EspeceIdentifiee(espece.code(), espece.nomLatin(), espece.nomVernaculaireFr());
    }

    /// Affiche le panneau détail (et restaure la position du séparateur) en regroupement **Par espèce**,
    /// le retire du `SplitPane` sinon — la table des carrés récupère alors toute la hauteur.
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
        // Le libellé du bouton suit l'état d'affichage : robuste à une bascule **programmatique** (« Voir sur
        // la carte » depuis l'audio, #476), pas seulement au clic sur le bouton lui-même.
        boutonCarte
                .textProperty()
                .bind(Bindings.when(carteAffichee).then("📋 Tableau").otherwise("🗺️ Carte"));
        carteAffichee.addListener((obs, ancien, affichee) -> {
            if (Boolean.TRUE.equals(affichee) && carteRepartition == null) {
                carteRepartition = new CarteRepartition(
                        viewModel.carresCarte(), viewModel.carresEspeceSelectionnee(), carteAffichee);
                carteRepartition.installerDans(zoneCarte);
            }
        });
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
    /// [GestionnaireFiltres#restaurer(DescripteurFiltre)] **ignore les critères inconnus** de l'analyse :
    /// seuls les critères partagés (statut, groupe) et la recherche texte sont réappliqués.
    ///
    /// @param filtres descripteur à rejouer, ou `null` pour ne rien changer aux filtres
    /// @param afficherCarte `true` pour basculer sur la carte de répartition
    public void appliquer(DescripteurFiltre filtres, boolean afficherCarte) {
        if (filtres != null) {
            gestionnaireFiltres.restaurer(filtres);
        }
        carteAffichee.set(afficherCarte);
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

    /// « Ouvrir le passage → » : ouvre M-Passage pour l'observation sélectionnée du détail.
    @FXML
    private void ouvrirPassage() {
        ObservationEspece observation = tableObservations.getSelectionModel().getSelectedItem();
        if (observation != null) {
            ouvrirPassageDe(observation);
        }
    }

    private void ouvrirPassageDe(ObservationEspece observation) {
        ouvrirPassage.ouvrir(
                observation.idPassage(),
                new ContexteSite(observation.numeroCarre(), observation.codePoint(), observation.nomSite()));
    }

    /// « 🎧 Écouter / valider » : ouvre la **vue audio unifiée** sur **toutes les observations de l'espèce
    /// sélectionnée** (source `ParEspece`, à travers les passages, avec le filtre de statut courant),
    /// **pré-focalisée sur la détection cliquée** (écoute + valider/corriger/référence), via le contrat
    /// socle [OuvrirAudio]. Au retour, [#rafraichirAuRetour()] met l'inventaire à jour.
    @FXML
    private void ecouterValider() {
        ecouter(tableObservations.getSelectionModel().getSelectedItem());
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
        colEspece.setCellValueFactory(c -> texte(FormatAnalyse.libelleEspece(c.getValue())));
        colGroupe.setCellValueFactory(
                c -> texte(FormatAnalyse.ouTiret(c.getValue().groupe())));
        colDetections.setCellValueFactory(c -> texte(c.getValue().nbObservations()));
        colPassages.setCellValueFactory(c -> texte(c.getValue().nbPassages()));
        colCarres.setCellValueFactory(c -> texte(c.getValue().nbCarres()));
        colPoints.setCellValueFactory(c -> texte(c.getValue().nbPoints()));
        colPeriode.setCellValueFactory(c -> texte(
                FormatAnalyse.periode(c.getValue().anneeMin(), c.getValue().anneeMax())));

        colCarre.setCellValueFactory(c -> texte(c.getValue().numeroCarre()));
        colSite.setCellValueFactory(
                c -> texte(FormatAnalyse.ouTiret(c.getValue().nomSite())));
        colRichesse.setCellValueFactory(c -> texte(c.getValue().richesse()));
        colDetectionsCarre.setCellValueFactory(c -> texte(c.getValue().nbObservations()));
        colPeriodeCarre.setCellValueFactory(c -> texte(
                FormatAnalyse.periode(c.getValue().anneeMin(), c.getValue().anneeMax())));

        // Colonnes du détail (observations de l'espèce sélectionnée).
        colObsPassage.setCellValueFactory(c -> texte(FormatAnalyse.libellePassage(c.getValue())));
        colObsCarre.setCellValueFactory(c -> texte(c.getValue().numeroCarre()));
        colObsRichesse.setCellValueFactory(
                c -> texte(richesseDuCarre(c.getValue().numeroCarre())));
        colObsPoint.setCellValueFactory(c -> texte(c.getValue().codePoint()));
        colObsTadarida.setCellValueFactory(c -> texte(FormatAnalyse.taxonEtProb(
                c.getValue().taxonTadarida(), c.getValue().probTadarida())));
        colObsObservateur.setCellValueFactory(c -> texte(FormatAnalyse.taxonEtProb(
                c.getValue().taxonObservateur(), c.getValue().probObservateur())));
        colObsStatut.setCellValueFactory(
                c -> texte(FormatAnalyse.libelleStatut(c.getValue().statut())));
        // Statut de revue en badge (#691), cohérent avec les autres tables de données.
        colObsStatut.setCellFactory(colonne -> ColonneBadge.cellule(obs -> FormatAnalyse.classeStatut(obs.statut())));
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

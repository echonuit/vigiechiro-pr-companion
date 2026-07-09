package fr.univ_amu.iut.multisite.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.view.ColonneBadge;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.GestionnaireVues;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.commun.view.carte.CarteSites;
import fr.univ_amu.iut.commun.view.carte.DonneesCarte;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import fr.univ_amu.iut.multisite.viewmodel.SourcesAudioMultisite;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/// Controller de l'écran **M-Multisite** (`Multisite.fxml`).
///
/// Pur câblage (patron CM4) : lie le tableau des passages agrégés, la **barre de filtres à puces**
/// (#537 étape 6b : carré, statut, verdict, année + recherche), les **onglets de vues mémorisées**
/// (`GestionnaireVues`), le tri et l'export au [MultisiteViewModel]. Le **double-clic** sur une ligne
/// ouvre l'écran M-Passage via le contrat socle [OuvrirPassage] (inversion de dépendance : la
/// feature ne dépend pas de `passage.view`). Le chargement initial est déclenché ici (écran sans
/// paramètre). Aucun accès base de données ni logique métier (règle ArchUnit `view_sans_jdbc`).
///
/// Implémente [RafraichirAuRetour] : quand on revient sur l'agrégat après avoir ouvert un passage et
/// l'avoir fait avancer (vérification, dépôt, validation), le tableau est rechargé pour refléter le
/// nouveau statut/verdict (sinon il afficherait un état périmé, l'écran restant vivant dans la pile).
public class MultisiteController implements RafraichirAuRetour {

    /// Clé de la feature pour les vues mémorisées (`saved_filter_view.feature`).
    private static final String FEATURE = "multisite";

    private final MultisiteViewModel viewModel;
    private final OuvrirPassage ouvrirPassage;
    private final OuvrirAudio ouvrirAudio;
    private final DepotVues depotVues;

    @FXML
    private Label lblResume;

    /// Barre de filtres « à la Notion » (#537 étape 6b) : recherche + « + Filtre » + puces actives.
    @FXML
    private TextField champRecherche;

    @FXML
    private MenuButton menuAjoutFiltre;

    @FXML
    private FlowPane pucesFiltres;

    /// Conteneur des onglets de vues mémorisées (`GestionnaireVues`).
    @FXML
    private FlowPane barreOnglets;

    @FXML
    private ComboBox<TriMultisite> choixTri;

    /// Menu « ☰ » regroupant les actions secondaires (Exporter, Écouter) pour alléger la barre (#370).
    @FXML
    private MenuButton menuActions;

    @FXML
    private MenuItem itemExporter;

    @FXML
    private MenuItem itemEcouterPassage;

    @FXML
    private MenuItem itemEcouterLot;

    @FXML
    private TableView<LignePassage> tableLignes;

    @FXML
    private TableColumn<LignePassage, String> colCarre;

    @FXML
    private TableColumn<LignePassage, String> colPoint;

    @FXML
    private TableColumn<LignePassage, String> colAnnee;

    @FXML
    private TableColumn<LignePassage, String> colNumero;

    @FXML
    private TableColumn<LignePassage, String> colDate;

    @FXML
    private TableColumn<LignePassage, String> colStatut;

    @FXML
    private TableColumn<LignePassage, String> colVerdict;

    @FXML
    private Label lblMessage;

    @FXML
    private StackPane zoneCarte;

    @FXML
    private VBox panneauTableau;

    @FXML
    private SplitPane splitCarteTableau;

    @FXML
    private Button boutonReplierCarte;

    @FXML
    private Button boutonReplierTableau;

    /// Toggle « ✎ » et bouton « 💾 » d'édition des positions : créés en code et superposés à la carte
    /// (#154 → overlay). « 💾 » n'est visible qu'en mode édition (géré par [EditionPositionsCarte]).
    private ToggleButton boutonEditerPositions;

    private Button boutonEnregistrerPositions;

    /// Barre de filtres à puces (#537 étape 6b) : la carte y pose une puce « carré » au clic.
    private GestionnaireFiltres<LignePassage> gestionnaireFiltres;

    /// Composant carte réutilisable (#152), rempli à partir de l'agrégat carte du ViewModel.
    private final CarteSites carte = new CarteSites();

    /// Mode édition des positions (#154) : toute la logique (clamp, file en attente, alerte) est déléguée.
    private EditionPositionsCarte edition;

    /// Dernière position du diviseur quand carte ET tableau sont visibles, restaurée à la réouverture
    /// d'un panneau replié (un `SplitPane` réinitialise ses diviseurs quand on retire/rajoute un item).
    private double derniereDivision = 0.42;

    /// Chevrons des poignées de repli (pointent vers le panneau qui va se replier / se rouvrir).
    private static final String FLECHE_GAUCHE = "◀";

    private static final String FLECHE_DROITE = "▶";

    /// Focalisation « voir sur la carte » (carré ou point) déléguée, pour garder le controller mince.
    private final FocalisationCarte focalisation = new FocalisationCarte(carte, this::degagerLaCarte);

    @Inject
    public MultisiteController(
            MultisiteViewModel viewModel, OuvrirPassage ouvrirPassage, OuvrirAudio ouvrirAudio, DepotVues depotVues) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvrirAudio = Objects.requireNonNull(ouvrirAudio, "ouvrirAudio");
        this.depotVues = Objects.requireNonNull(depotVues, "depotVues");
    }

    @FXML
    private void initialize() {
        // Densite et habillage de table uniformes (#690).
        TableDonnees.uniformiser(tableLignes);
        configurerColonnes();
        // #145 : tri par clic en-tête. Un SortedList lié au comparateur de la table s'applique par-dessus
        // la liste (déjà filtrée/ordonnée par le VM) ; performant (~4000 lignes) et le tri colonne
        // persiste à travers les rafraîchissements de filtres.
        SortedList<LignePassage> lignesTriees = new SortedList<>(viewModel.lignes());
        lignesTriees.comparatorProperty().bind(tableLignes.comparatorProperty());
        tableLignes.setItems(lignesTriees);
        // Double-clic sur une ligne → ouvre M-Passage (contrat socle, aucune dépendance vers passage.view).
        tableLignes.setRowFactory(tableau -> {
            TableRow<LignePassage> ligne = new TableRow<>();
            ligne.setOnMouseClicked(evenement -> {
                if (evenement.getButton() == MouseButton.PRIMARY
                        && evenement.getClickCount() == 2
                        && !ligne.isEmpty()) {
                    ouvrirPassageDeLaLigne(ligne.getItem());
                }
            });
            return ligne;
        });

        // Barre de filtres à puces (#537 étape 6b) : Carré / Statut / Verdict / Année + recherche. Le tri
        // (choixTri) reste un contrôle fixe : c'est un axe d'ordonnancement, pas un filtre.
        gestionnaireFiltres = new GestionnaireFiltres<>(
                champRecherche,
                menuAjoutFiltre,
                pucesFiltres,
                viewModel.filtres(),
                List.of(
                        CriteresMultisite.carre(),
                        CriteresMultisite.statut(),
                        CriteresMultisite.verdict(),
                        CriteresMultisite.annee()),
                CriteresMultisite.rechercheTexte());
        // Onglets de vues mémorisées (#623) : vues par défaut (lecture seule) + vues de l'utilisateur.
        GestionnaireVues.avecDialogue(
                barreOnglets, gestionnaireFiltres, depotVues, FEATURE, CriteresMultisite.vuesParDefaut());

        choixTri.getItems().setAll(TriMultisite.values());
        choixTri.setConverter(Convertisseurs.parLibelle(tri -> tri == null ? "" : tri.libelle()));
        // #370 : sans étiquette « Tri : » avant la liste, on préfixe l'intitulé DANS la cellule-bouton (la
        // valeur sélectionnée affichée), sans toucher aux items du menu déroulant qui restent bruts.
        choixTri.setButtonCell(new CelluleTri());
        choixTri.valueProperty().bindBidirectional(viewModel.triProperty());
        // Choisir un ordre nommé (combo) réinitialise le tri par colonne, pour que l'ordre nommé soit
        // visible (sinon le comparateur de colonne masquerait le tri du VM). #145.
        viewModel
                .triProperty()
                .addListener(
                        (obs, ancien, nouveau) -> tableLignes.getSortOrder().clear());

        lblResume.textProperty().bind(viewModel.resumeProperty());
        itemExporter.disableProperty().bind(viewModel.nonVideProperty().not());
        // Écoute : le lot suit la présence de lignes filtrées ; un passage exige une ligne sélectionnée.
        itemEcouterLot.disableProperty().bind(viewModel.nonVideProperty().not());
        itemEcouterPassage
                .disableProperty()
                .bind(tableLignes.getSelectionModel().selectedItemProperty().isNull());

        lblMessage.textProperty().bind(viewModel.messageProperty());
        var messagePresent = viewModel.messageProperty().isNotEmpty();
        lblMessage.visibleProperty().bind(messagePresent);
        lblMessage.managedProperty().bind(messagePresent);

        // Carte (#152) : le composant réutilisable affiche sites + points. On le remplit en traduisant
        // l'agrégat carte (non filtré) en DonneesCarte à chaque mise à jour. La carte ne dépend pas des
        // filtres/tri du tableau, d'où un rafraîchissement DÉDIÉ (rafraichirCarte), au chargement et au retour.
        zoneCarte.getChildren().add(carte);
        // Overlays superposés à la carte (légende, « recadrer », contrôles d'édition) : extraits pour
        // garder initialize() concis.
        installerOverlaysCarte();
        viewModel.carresCarte().addListener((ListChangeListener<CarreAgrege>) changement -> rafraichirTracesCarte());

        // Édition des positions (#154) : déléguée à EditionPositionsCarte (clamp au carré, file en attente,
        // alerte de sortie). Le controller ne fait que la brancher et relayer les actions.
        edition = new EditionPositionsCarte(carte, viewModel, boutonEditerPositions, boutonEnregistrerPositions);
        edition.brancher();

        // Liaisons carte ↔ tableau (#152) :
        // - clic d'un carré sur la carte → pose une puce « carré » qui filtre le tableau par ce carré ;
        carte.setOnCarreClic(carreGeo -> gestionnaireFiltres.poser("carre", List.of(carreGeo.numeroCarre())));
        // - sélection d'une ligne du tableau → met le carré correspondant en surbrillance sur la carte.
        tableLignes
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (obs, ancienne, ligne) -> carte.surbrillanceCarre(ligne == null ? null : ligne.numeroCarre()));

        majPoignees();

        viewModel.rafraichir();
        viewModel.rafraichirCarte();
    }

    /// Superpose à la carte ses contrôles : la **légende** (bas-gauche, #152), le bouton **« ⤢ recadrer »**
    /// (haut-droite, #339) et les **contrôles d'édition des positions** (haut-gauche, #154) — le toggle
    /// « ✎ » (toujours visible) et « 💾 » (visible en mode édition seulement, géré par
    /// [EditionPositionsCarte]). Icônes seules, à portée de la carte qu'ils pilotent ; les `id` sont
    /// conservés pour les tests/CSS.
    private void installerOverlaysCarte() {
        Node legende = LegendeCarte.creer();
        StackPane.setAlignment(legende, Pos.BOTTOM_LEFT);
        StackPane.setMargin(legende, new Insets(8));
        zoneCarte.getChildren().add(legende);

        Button recadrer = new Button("⤢");
        StyleControlesCarte.overlay(recadrer, "bouton-recadrer", "Recadrer la carte sur les éléments visibles");
        recadrer.setOnAction(evenement -> carte.recadrer());
        StackPane.setAlignment(recadrer, Pos.TOP_RIGHT);
        StackPane.setMargin(recadrer, new Insets(8));
        zoneCarte.getChildren().add(recadrer);

        boutonEditerPositions = new ToggleButton("✎");
        boutonEditerPositions.setId("boutonEditerPositions");
        StyleControlesCarte.overlay(
                boutonEditerPositions, "bouton-editer-positions", "Éditer les positions des points");
        boutonEditerPositions.setOnAction(evenement -> basculerEdition());
        boutonEnregistrerPositions = new Button("💾");
        boutonEnregistrerPositions.setId("boutonEnregistrerPositions");
        StyleControlesCarte.overlay(
                boutonEnregistrerPositions, "bouton-editer-positions", "Enregistrer les positions déplacées");
        boutonEnregistrerPositions.setOnAction(evenement -> enregistrerPositions());
        VBox controlesEdition = new VBox(6, boutonEditerPositions, boutonEnregistrerPositions);
        controlesEdition.setPickOnBounds(false);
        StackPane.setAlignment(controlesEdition, Pos.TOP_LEFT);
        StackPane.setMargin(controlesEdition, new Insets(8));
        zoneCarte.getChildren().add(controlesEdition);
    }

    /// Replie (ou rouvre) la **carte** : le tableau prend alors toute la largeur. On ne peut pas replier
    /// les deux panneaux à la fois (cf. [#majPoignees]).
    @FXML
    private void basculerCarte() {
        if (estVisible(zoneCarte)) {
            replier(zoneCarte);
        } else {
            rouvrir(zoneCarte, 0);
        }
        majPoignees();
    }

    /// Replie (ou rouvre) le **tableau** : la carte prend alors toute la largeur.
    @FXML
    private void basculerTableau() {
        if (estVisible(panneauTableau)) {
            replier(panneauTableau);
        } else {
            rouvrir(panneauTableau, splitCarteTableau.getItems().size());
        }
        majPoignees();
    }

    private boolean estVisible(Node panneau) {
        return splitCarteTableau.getItems().contains(panneau);
    }

    /// Retire un panneau du `SplitPane` (repli complet), après avoir mémorisé la position du diviseur
    /// pour pouvoir la restaurer à la réouverture.
    private void replier(Node panneau) {
        if (splitCarteTableau.getDividerPositions().length > 0) {
            derniereDivision = splitCarteTableau.getDividerPositions()[0];
        }
        splitCarteTableau.getItems().remove(panneau);
    }

    /// Réinsère un panneau à sa place canonique (carte en 0, tableau en fin) et restaure le diviseur.
    private void rouvrir(Node panneau, int index) {
        if (!splitCarteTableau.getItems().contains(panneau)) {
            splitCarteTableau
                    .getItems()
                    .add(Math.min(index, splitCarteTableau.getItems().size()), panneau);
            splitCarteTableau.setDividerPositions(derniereDivision);
        }
    }

    /// Met à jour le libellé, l'info-bulle, le texte accessible (#163) et l'état activé des deux poignées
    /// selon ce qui est visible. La poignée d'un panneau **déjà seul** est désactivée (interdit de tout
    /// replier), celle du panneau replié invite à le rouvrir.
    private void majPoignees() {
        boolean carteVisible = estVisible(zoneCarte);
        boolean tableauVisible = estVisible(panneauTableau);

        StyleControlesCarte.poignee(
                boutonReplierCarte,
                (carteVisible ? FLECHE_GAUCHE : FLECHE_DROITE) + " Carte",
                carteVisible ? "Masquer la carte" : "Afficher la carte",
                tableauVisible);
        StyleControlesCarte.poignee(
                boutonReplierTableau,
                "Tableau " + (tableauVisible ? FLECHE_DROITE : FLECHE_GAUCHE),
                tableauVisible ? "Masquer le tableau" : "Afficher le tableau",
                carteVisible);
    }

    /// Rechargé par le [fr.univ_amu.iut.commun.view.Navigateur] quand on **revient** sur l'agrégat
    /// (← Retour ou fil d'Ariane) : un passage ouvert depuis le tableau a pu avancer pendant qu'on
    /// était dessus. On rejoue le chargement (filtres et tri courants préservés) pour réafficher les
    /// statuts/verdicts réels plutôt qu'un état périmé.
    @Override
    public void rafraichirAuRetour() {
        viewModel.rafraichir();
        viewModel.rafraichirCarte(); // un passage modifié peut changer le statut dominant d'un point (#152)
    }

    /// Focalise la carte sur un **carré** (« voir sur la carte » d'un site/passage). Délégué à
    /// [FocalisationCarte].
    public void focaliserSur(String numeroCarre) {
        focalisation.surCarre(numeroCarre);
    }

    /// Focalise la carte sur un **point précis** (« voir sur la carte » d'un point GPS, #154). Délégué à
    /// [FocalisationCarte] ; l'édition des positions (toggle) permet alors de corriger ce point.
    public void focaliserSurPoint(String numeroCarre, double latitude, double longitude) {
        focalisation.surPoint(numeroCarre, latitude, longitude);
    }

    /// « Placer sur la carte » d'un point **sans GPS** : on focalise sur son carré ET on entre directement
    /// en mode édition, pour glisser le marqueur approximatif (au centre du carré) vers sa vraie position.
    public void focaliserSurCarrePourPlacer(String numeroCarre) {
        edition.activer();
        focalisation.surCarre(numeroCarre);
    }

    /// Replie le tableau (#338) pour donner toute la largeur à la carte : c'est le but du clic « Voir sur
    /// la carte ». L'utilisateur le rouvre au besoin via la poignée « Tableau ◀ ».
    private void degagerLaCarte() {
        if (estVisible(panneauTableau)) {
            replier(panneauTableau);
            majPoignees();
        }
    }

    /// Retrace la carte depuis l'agrégat (traduction domaine → [DonneesCarte]) **et** réindexe l'édition
    /// (libellé → idPoint, carré → emprise) pour que glisser/clamper retrouve la bonne donnée.
    private void rafraichirTracesCarte() {
        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(viewModel.carresCarte());
        carte.setDonnees(donnees);
        edition.indexer(donnees, viewModel.carresCarte());
    }

    /// Toggle « ✎ Éditer les positions » (overlay de la carte, délégué à [EditionPositionsCarte]).
    private void basculerEdition() {
        edition.basculer();
    }

    /// Bouton « 💾 » Enregistrer les positions (overlay de la carte, délégué à [EditionPositionsCarte]).
    private void enregistrerPositions() {
        edition.enregistrer();
    }

    private void configurerColonnes() {
        colCarre.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().numeroCarre()));
        colPoint.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().codePoint()));
        colAnnee.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().annee())));
        colNumero.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().numeroPassage())));
        // #145 : tri NUMÉRIQUE (et non alphabétique) au clic d'en-tête sur Année et N° de passage.
        colAnnee.setComparator(Comparator.comparingInt(Integer::parseInt));
        colNumero.setComparator(Comparator.comparingInt(Integer::parseInt));
        colDate.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().dateEnregistrement()));
        colStatut.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(c.getValue().statut().libelle()));
        colVerdict.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().verdict() == null ? "" : c.getValue().verdict().libelle()));
        // Statut / verdict en badges (#691), comme la table de la fiche site.
        colStatut.setCellFactory(colonne -> ColonneBadge.cellule(ligne -> ColonneBadge.classe(ligne.statut())));
        colVerdict.setCellFactory(colonne -> ColonneBadge.cellule(ligne -> ColonneBadge.classe(ligne.verdict())));
    }

    private void ouvrirPassageDeLaLigne(LignePassage ligne) {
        // Le nom convivial du site n'est pas porté par la vue agrégée : carré + point suffisent au
        // fil d'Ariane de M-Passage (nomSite n'y est pas utilisé).
        ouvrirPassage.ouvrir(ligne.idPassage(), new ContexteSite(ligne.numeroCarre(), ligne.codePoint(), null));
    }

    /// « Réinitialiser » : retire tous les filtres (recherche + puces) via le gestionnaire, et efface le
    /// tri par clic d'en-tête (#145).
    @FXML
    private void reinitialiser() {
        gestionnaireFiltres.reinitialiser();
        tableLignes.getSortOrder().clear();
    }

    /// « Exporter » : ouvre le sélecteur de fichier natif (enregistrement) puis délègue au VM.
    /// Le dialog vit dans la vue (non testé en TestFX) ; l'écriture est testée côté ViewModel.
    @FXML
    private void exporter() {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Exporter les passages en CSV");
        selecteur.setInitialFileName("vue-multisite.csv");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File fichier = selecteur.showSaveDialog(menuActions.getScene().getWindow());
        if (fichier != null) {
            // #291 : on exporte l'ordre RÉELLEMENT affiché (tri par clic d'en-tête inclus), donc un
            // instantané des items de la table (SortedList), et non l'ordre interne du ViewModel.
            viewModel.exporter(fichier.toPath(), new ArrayList<>(tableLignes.getItems()));
        }
    }

    /// « 🎧 Écouter le passage sélectionné » : ouvre la vue audio unifiée sur les observations de ce
    /// passage (source `ParPassage`). L'item de menu est désactivé sans sélection (binding), donc la ligne
    /// est ici toujours présente.
    @FXML
    private void ecouterPassage() {
        ouvrirAudio.ouvrir(
                SourcesAudioMultisite.parPassage(tableLignes.getSelectionModel().getSelectedItem()));
    }

    /// « 🎧 Écouter le lot filtré » : ouvre la vue audio unifiée sur les observations de **tous** les
    /// passages affichés (source `ParPassages`) - écoute / validation en lot à travers plusieurs passages.
    /// On part de l'**instantané réellement affiché** (`tableLignes.getItems()`, tri colonne inclus), comme
    /// l'export (#291) : « le lot filtré » = exactement ce qui est dans le tableau. L'ordre de revue est de
    /// toute façon ré-appliqué côté vue audio (`ORDRE_AUDIO`), mais on garde un contrat cohérent.
    @FXML
    private void ecouterLot() {
        ouvrirAudio.ouvrir(SourcesAudioMultisite.parLot(new ArrayList<>(tableLignes.getItems())));
    }
}

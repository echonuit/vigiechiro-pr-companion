package fr.univ_amu.iut.multisite.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.GestionnaireVues;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.view.SelecteurFichierJavaFx;
import fr.univ_amu.iut.commun.view.SelecteurFichierModifiable;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.commun.view.carte.CarteSites;
import fr.univ_amu.iut.commun.view.carte.DonneesCarte;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import fr.univ_amu.iut.multisite.viewmodel.ReconstructionViewModel;
import fr.univ_amu.iut.multisite.viewmodel.SourcesAudioMultisite;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
public class MultisiteController implements RafraichirAuRetour, ResumeStatut {

    /// Zones de la barre de statut (#1023) : cet agrégat top-level ne renseigne que le **centre** (résumé
    /// « N sites… ») ; la gauche (identité) reste au défaut du chrome.
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    /// Clé de la feature pour les vues mémorisées (`saved_filter_view.feature`).
    private static final String FEATURE = "multisite";

    private final MultisiteViewModel viewModel;

    /// ViewModel de la modale de reconstruction (#1396) : le controller ne s'en sert que pour **savoir**
    /// si l'action a un sens ici (connexion VigieChiro présente) ; la modale reçoit le sien.
    private final ReconstructionViewModel reconstruction;

    private final NavigationMultisite navigation;
    private final OuvrirPassage ouvrirPassage;
    private final OuvrirAudio ouvrirAudio;
    private final DepotVues depotVues;
    private final DepotDispositionColonnes depotColonnes;
    private final ExecuteurTache executeur;
    private IndicateurOccupation occupation;

    /// Désignation du fichier d'export : porteur partagé injectable (#1431), double répondant en test.
    /// Le `FileChooser` en dur **figeait** tout test du geste.
    private final SelecteurFichierModifiable selecteur = new SelecteurFichierModifiable(
            // `this.menuActions` : le champ @FXML est déclaré plus bas (référence en avant interdite dans
            // un initialiseur). La fenêtre n'est lue qu'au clic.
            new SelecteurFichierJavaFx(() -> this.menuActions.getScene().getWindow()));

    /// Porteur de désignation exposé aux tests (#1431) : `selecteur().definir(double)`.
    SelecteurFichierModifiable selecteur() {
        return selecteur;
    }

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
    private MenuItem itemReconstruire;

    /// « ☁ Relever l'état des analyses » (#1338) : retiré hors connexion (il interroge la plateforme).
    @FXML
    private MenuItem itemReleverAnalyses;

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

    /// Où en est l'analyse Tadarida de la nuit (#1338) : câblage délégué à [ColonnesMultisite].
    @FXML
    private TableColumn<LignePassage, String> colAnalyse;

    @FXML
    private Label lblMessage;

    @FXML
    private StackPane hoteOccupation;

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

    /// Édition des positions exposée aux tests (#1431) : `edition().demandeur().definir(double)` remplace
    /// le choix « enregistrer / abandonner » de la sortie d'édition, qui figeait tout test headless.
    EditionPositionsCarte edition() {
        return edition;
    }

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
            MultisiteViewModel viewModel,
            ReconstructionViewModel reconstruction,
            NavigationMultisite navigation,
            OuvrirPassage ouvrirPassage,
            OuvrirAudio ouvrirAudio,
            DepotVues depotVues,
            DepotDispositionColonnes depotColonnes,
            ExecuteurTache executeur) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.reconstruction = Objects.requireNonNull(reconstruction, "reconstruction");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvrirAudio = Objects.requireNonNull(ouvrirAudio, "ouvrirAudio");
        this.depotVues = Objects.requireNonNull(depotVues, "depotVues");
        this.depotColonnes = Objects.requireNonNull(depotColonnes, "depotColonnes");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
    }

    @Override
    public ReadOnlyObjectProperty<ZonesStatut> zonesStatutProperty() {
        return zonesStatut.getReadOnlyProperty();
    }

    @FXML
    private void initialize() {
        // Densité/habillage de table uniformes (#690) + table navigable au double-clic (#792).
        TableDonnees.uniformiserNavigable(tableLignes);
        ColonnesMultisite.configurer(
                colCarre, colPoint, colAnnee, colNumero, colDate, colStatut, colVerdict, colAnalyse);
        // Sélecteur de colonnes (#919) : clic droit + ☰ « outils » (réutilise le menu existant). La
        // disposition (ordre + visibilité) est retenue par écran et restaurée à la réouverture (#994).
        var colonnes = colonnesLignes();
        GestionnaireColonnes.installer(tableLignes, menuActions, colonnes);
        GestionnaireColonnes.persister(tableLignes, colonnes, depotColonnes, FEATURE, "principale");
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
                        CriteresMultisite.annee(),
                        CriteresMultisite.analyse()),
                CriteresMultisite.rechercheTexte());
        // Onglets de vues mémorisées (#623) : vues par défaut (lecture seule) + vues de l'utilisateur. La vue
        // capture aussi la disposition des colonnes du tableau (#994), via l'adaptateur mono-table.
        GestionnaireVues.avecDialogue(
                barreOnglets,
                gestionnaireFiltres,
                depotVues,
                FEATURE,
                CriteresMultisite.vuesParDefaut(),
                GestionnaireColonnes.adaptateurMonoTable("principale", tableLignes, this::colonnesLignes));

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
        // Barre de statut (#1023) : le résumé « N sites… » occupe la zone centre (agrégat top-level, pas
        // d'identité propre → la gauche reste au défaut du chrome).
        zonesStatut.bind(Bindings.createObjectBinding(
                () -> ZonesStatut.centre(viewModel.resumeProperty().get()), viewModel.resumeProperty()));
        // État des entrées du menu ☰ (grisage parlant #789, retrait de la reconstruction hors connexion
        // #1396) : déporté dans MenuActionsMultisite, le controller était au plafond de taille.
        MenuActionsMultisite.installer(
                itemExporter,
                itemEcouterLot,
                itemEcouterPassage,
                itemReconstruire,
                itemReleverAnalyses,
                viewModel.nonVideProperty(),
                tableLignes.getSelectionModel().selectedItemProperty(),
                reconstruction.disponible(),
                viewModel.releveAnalysesDisponible());

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

        occupation = new IndicateurOccupation(hoteOccupation, executeur);
        chargerDonnees();
    }

    /// Charge passages + carte **hors du fil JavaFX** (#1209) : les deux requêtes base partent en
    /// arrière-plan sous l'overlay « … en cours », puis l'application (ou l'erreur, filet #795) revient
    /// sur le fil JavaFX. Utilisé au premier affichage et à chaque retour sur l'écran.
    private void chargerDonnees() {
        occupation.occuper(
                "Chargement des passages…", viewModel::charger, viewModel::appliquer, viewModel::signalerErreur);
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
        // Un passage modifié ailleurs peut changer le statut dominant d'un point (#152) : on recharge tout.
        chargerDonnees();
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

    /// Colonnes du tableau multi-sites proposées au sélecteur (#919). « Carré » est l'identité (verrouillée).
    private List<GestionnaireColonnes.Colonne> colonnesLignes() {
        return List.of(
                new GestionnaireColonnes.Colonne(colCarre, "Carré", true),
                new GestionnaireColonnes.Colonne(colPoint, "Point", false),
                new GestionnaireColonnes.Colonne(colAnnee, "Année", false),
                new GestionnaireColonnes.Colonne(colNumero, "N° passage", false),
                new GestionnaireColonnes.Colonne(colDate, "Date", false),
                new GestionnaireColonnes.Colonne(colStatut, "Statut", false),
                new GestionnaireColonnes.Colonne(colVerdict, "Verdict", false),
                new GestionnaireColonnes.Colonne(colAnalyse, "Analyse", false));
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

    /// « ☁ Reconstruire un passage manquant… » (#1396) : ouvre la modale qui liste les nuits déposées sur
    /// VigieChiro et absentes d'ici. À sa fermeture, si une nuit a été reconstruite, la table est
    /// rechargée : la nuit rapatriée **apparaît**, ce qui est la preuve visible de la reconstruction.
    @FXML
    private void reconstruirePassage() {
        navigation.ouvrirModaleReconstruction(menuActions.getScene().getWindow(), this::chargerDonnees);
    }

    /// « ☁ Relever l'état des analyses » (#1338) : interroge VigieChiro pour **toutes les nuits déposées**,
    /// à la demande. L'instantané des nuits déposées est capturé **sur le fil JavaFX** (`nuitsDeposees`),
    /// puis le relevé (une requête réseau par nuit) part **hors du fil JavaFX** sous le voile d'occupation.
    /// Au retour, on **recharge** l'écran : les badges de la colonne « Analyse » reflètent le nouvel état,
    /// et le compte rendu part dans le bandeau de message.
    @FXML
    private void releverAnalyses() {
        List<Long> nuitsDeposees = viewModel.nuitsDeposees();
        occupation.occuper(
                "Relevé de l'état des analyses…",
                () -> viewModel.releverPuisCharger(nuitsDeposees),
                viewModel::appliquerReleve,
                viewModel::signalerErreur);
    }

    /// « Exporter » : demande où écrire, puis écriture par le ViewModel dans **l'ordre affiché** (#291).
    /// La désignation passe par le porteur de l'écran (#1431).
    @FXML
    private void exporter() {
        MenuActionsMultisite.exporter(
                selecteur,
                tableLignes,
                chemin -> viewModel.exporter(chemin, MenuActionsMultisite.lignesAffichees(tableLignes)));
    }

    /// « 🎧 Écouter le passage sélectionné » : ouvre la vue audio unifiée sur les observations de ce
    /// passage (source `ParPassage`). L'item de menu est désactivé sans sélection (binding), donc la ligne
    /// est ici toujours présente.
    @FXML
    private void ecouterPassage() {
        ouvrirAudio.ouvrir(
                SourcesAudioMultisite.parPassage(tableLignes.getSelectionModel().getSelectedItem()));
    }

    /// « 🎧 Écouter la sélection filtrée » : ouvre la vue audio unifiée sur les observations de **tous** les
    /// passages affichés (source `ParPassages`) - écoute / validation groupée à travers plusieurs passages.
    /// On part de l'**instantané réellement affiché** (`tableLignes.getItems()`, tri colonne inclus), comme
    /// l'export (#291) : « la sélection filtrée » = exactement ce qui est dans le tableau. L'ordre de revue est de
    /// toute façon ré-appliqué côté vue audio (`ORDRE_AUDIO`), mais on garde un contrat cohérent.
    @FXML
    private void ecouterLot() {
        ouvrirAudio.ouvrir(SourcesAudioMultisite.parLot(new ArrayList<>(tableLignes.getItems())));
    }
}

package fr.univ_amu.iut.multisite.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.view.carte.CarteSites;
import fr.univ_amu.iut.commun.view.carte.FournisseurEmpriseCarre;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
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
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/// Controller de l'écran **M-Multisite** (`Multisite.fxml`).
///
/// Pur câblage (patron CM4) : lie le tableau des passages agrégés, les filtres (carré, statut,
/// verdict, année), le tri et l'export au [MultisiteViewModel]. Le **double-clic** sur une ligne
/// ouvre l'écran M-Passage via le contrat socle [OuvrirPassage] (inversion de dépendance : la
/// feature ne dépend pas de `passage.view`). Le chargement initial est déclenché ici (écran sans
/// paramètre). Aucun accès base de données ni logique métier (règle ArchUnit `view_sans_jdbc`).
///
/// Implémente [RafraichirAuRetour] : quand on revient sur l'agrégat après avoir ouvert un passage et
/// l'avoir fait avancer (vérification, dépôt, validation), le tableau est rechargé pour refléter le
/// nouveau statut/verdict (sinon il afficherait un état périmé, l'écran restant vivant dans la pile).
public class MultisiteController implements RafraichirAuRetour {

    private final MultisiteViewModel viewModel;
    private final OuvrirPassage ouvrirPassage;
    private final NavigationMultisite navigation;

    @FXML
    private Label lblResume;

    @FXML
    private TextField champCarre;

    @FXML
    private ComboBox<StatutWorkflow> choixStatut;

    @FXML
    private ComboBox<Verdict> choixVerdict;

    @FXML
    private TextField champAnnee;

    @FXML
    private ComboBox<TriMultisite> choixTri;

    @FXML
    private Button boutonExporter;

    @FXML
    private Button boutonGererVues;

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

    /// Composant carte réutilisable (#152), rempli à partir de l'agrégat carte du ViewModel.
    private final CarteSites carte = new CarteSites();

    /// Dernière position du diviseur quand carte ET tableau sont visibles, restaurée à la réouverture
    /// d'un panneau replié (un `SplitPane` réinitialise ses diviseurs quand on retire/rajoute un item).
    private double derniereDivision = 0.42;

    /// Chevrons des poignées de repli (pointent vers le panneau qui va se replier / se rouvrir).
    private static final String FLECHE_GAUCHE = "◀";

    private static final String FLECHE_DROITE = "▶";

    @Inject
    public MultisiteController(
            MultisiteViewModel viewModel, OuvrirPassage ouvrirPassage, NavigationMultisite navigation) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    @FXML
    private void initialize() {
        configurerColonnes();
        // #145 : tri par clic en-tête. Un SortedList lié au comparateur de la table s'applique par-dessus
        // la liste (déjà filtrée/ordonnée côté service) ; performant (~4000 lignes) et le tri colonne
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

        configurerFiltres();
        choixTri.getItems().setAll(TriMultisite.values());
        choixTri.setConverter(convertisseur(MultisiteController::libelleTri));
        choixTri.valueProperty().bindBidirectional(viewModel.triProperty());
        // Choisir un ordre nommé (combo) réinitialise le tri par colonne, pour que l'ordre nommé soit
        // visible (sinon le comparateur de colonne masquerait le tri serveur). #145.
        viewModel
                .triProperty()
                .addListener(
                        (obs, ancien, nouveau) -> tableLignes.getSortOrder().clear());

        lblResume.textProperty().bind(viewModel.resumeProperty());
        boutonExporter.disableProperty().bind(viewModel.nonVideProperty().not());

        lblMessage.textProperty().bind(viewModel.messageProperty());
        var messagePresent = viewModel.messageProperty().isNotEmpty();
        lblMessage.visibleProperty().bind(messagePresent);
        lblMessage.managedProperty().bind(messagePresent);

        // Carte (#152) : le composant réutilisable affiche sites + points. On le remplit en traduisant
        // l'agrégat carte (non filtré) en DonneesCarte à chaque mise à jour. La carte ne dépend pas des
        // filtres/tri du tableau, d'où un rafraîchissement DÉDIÉ (rafraichirCarte), au chargement et au retour.
        zoneCarte.getChildren().add(carte);
        // Légende superposée en bas à gauche (#152) : code couleur des statuts + échelle de densité.
        Node legende = LegendeCarte.creer();
        StackPane.setAlignment(legende, Pos.BOTTOM_LEFT);
        StackPane.setMargin(legende, new Insets(8));
        zoneCarte.getChildren().add(legende);
        // Bouton « recadrer » superposé en haut à droite (#339) : recentre/zoome la carte sur tous les
        // carrés/points affichés (utile après un zoom ou un déplacement manuel).
        Button recadrer = new Button("⤢");
        recadrer.getStyleClass().add("bouton-recadrer");
        recadrer.setAccessibleText("Recadrer la carte sur les éléments visibles");
        recadrer.setTooltip(new Tooltip("Recadrer sur les éléments visibles"));
        recadrer.setOnAction(evenement -> carte.recadrer());
        StackPane.setAlignment(recadrer, Pos.TOP_RIGHT);
        StackPane.setMargin(recadrer, new Insets(8));
        zoneCarte.getChildren().add(recadrer);
        viewModel.carresCarte().addListener((ListChangeListener<CarreAgrege>)
                changement -> carte.setDonnees(ConstructeurDonneesCarte.depuis(viewModel.carresCarte())));

        // Liaisons carte ↔ tableau (#152) :
        // - clic d'un carré sur la carte → filtre le tableau par ce carré (met aussi à jour le champ) ;
        carte.setOnCarreClic(carreGeo -> viewModel.filtreNumeroCarreProperty().set(carreGeo.numeroCarre()));
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

        configurerPoignee(
                boutonReplierCarte,
                (carteVisible ? FLECHE_GAUCHE : FLECHE_DROITE) + " Carte",
                carteVisible ? "Masquer la carte" : "Afficher la carte",
                tableauVisible);
        configurerPoignee(
                boutonReplierTableau,
                "Tableau " + (tableauVisible ? FLECHE_DROITE : FLECHE_GAUCHE),
                tableauVisible ? "Masquer le tableau" : "Afficher le tableau",
                carteVisible);
    }

    private static void configurerPoignee(Button poignee, String libelle, String description, boolean actif) {
        poignee.setText(libelle);
        poignee.setAccessibleText(description);
        poignee.setTooltip(new Tooltip(description));
        poignee.setDisable(!actif);
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

    /// Focalise la carte sur un carré (« voir sur la carte » depuis un autre écran) : surbrillance + recentrage
    /// sur l'emprise officielle du carré, **et repli du tableau** pour dégager la carte (#338). Sans effet
    /// si le numéro est vide ou hors carroyage.
    public void focaliserSur(String numeroCarre) {
        if (numeroCarre == null || numeroCarre.isBlank()) {
            return;
        }
        carte.surbrillanceCarre(numeroCarre);
        FournisseurEmpriseCarre.parDefaut().emprise(numeroCarre, List.of()).ifPresent(carte::centrerSurCarre);
        // #338 : on arrive par « Voir sur la carte » → la carte est le but du clic. On replie le tableau
        // pour lui donner toute la largeur ; l'utilisateur le rouvre au besoin via la poignée « Tableau ◀ ».
        if (estVisible(panneauTableau)) {
            replier(panneauTableau);
            majPoignees();
        }
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
    }

    private void configurerFiltres() {
        // Statut / verdict : la 1re entrée (null) lève le filtre ; les suivantes restreignent.
        choixStatut.getItems().add(null);
        choixStatut.getItems().addAll(StatutWorkflow.values());
        choixStatut.setConverter(convertisseur(s -> s == null ? "Tous les statuts" : s.libelle()));
        choixStatut.valueProperty().bindBidirectional(viewModel.filtreStatutProperty());

        choixVerdict.getItems().add(null);
        choixVerdict.getItems().addAll(Verdict.values());
        choixVerdict.setConverter(convertisseur(v -> v == null ? "Tous les verdicts" : v.libelle()));
        choixVerdict.valueProperty().bindBidirectional(viewModel.filtreVerdictProperty());

        // Champs texte : appliqués à la validation (Entrée) pour ne pas ré-interroger à chaque frappe ;
        // les champs reflètent la propriété (vidés lors de la réinitialisation).
        champCarre.setOnAction(
                evenement -> viewModel.filtreNumeroCarreProperty().set(champCarre.getText()));
        viewModel
                .filtreNumeroCarreProperty()
                .addListener((obs, ancien, nouveau) -> champCarre.setText(nouveau == null ? "" : nouveau));

        champAnnee.setOnAction(evenement -> appliquerAnnee());
        viewModel
                .filtreAnneeProperty()
                .addListener(
                        (obs, ancien, nouveau) -> champAnnee.setText(nouveau == null ? "" : String.valueOf(nouveau)));
    }

    /// Parse l'année saisie : un champ vide lève le filtre, une valeur non numérique est ignorée
    /// (le champ est restauré à la valeur courante du filtre).
    private void appliquerAnnee() {
        String saisie = champAnnee.getText() == null ? "" : champAnnee.getText().trim();
        if (saisie.isEmpty()) {
            viewModel.filtreAnneeProperty().set(null);
            return;
        }
        try {
            viewModel.filtreAnneeProperty().set(Integer.valueOf(saisie));
        } catch (NumberFormatException invalide) {
            Integer courant = viewModel.filtreAnneeProperty().get();
            champAnnee.setText(courant == null ? "" : String.valueOf(courant));
        }
    }

    private void ouvrirPassageDeLaLigne(LignePassage ligne) {
        // Le nom convivial du site n'est pas porté par la vue agrégée : carré + point suffisent au
        // fil d'Ariane de M-Passage (nomSite n'y est pas utilisé).
        ouvrirPassage.ouvrir(ligne.idPassage(), new ContexteSite(ligne.numeroCarre(), ligne.codePoint(), null));
    }

    /// « Vues enregistrées… » : ouvre la modale de gestion, branchée sur ce même ViewModel
    /// (appliquer une vue met donc à jour les filtres et le tableau de cet écran).
    @FXML
    private void gererVues() {
        navigation.ouvrirModaleVues(boutonGererVues.getScene().getWindow(), viewModel);
    }

    @FXML
    private void reinitialiser() {
        // Vide d'abord les champs texte : une saisie non validée (sans Entrée) n'a pas mis à jour le
        // VM, donc reinitialiserFiltres seul laisserait ce texte affiché (P3 revue codex).
        champCarre.clear();
        champAnnee.clear();
        tableLignes.getSortOrder().clear(); // #145 : la réinitialisation efface aussi le tri par colonne.
        viewModel.reinitialiserFiltres();
    }

    /// « Exporter » : ouvre le sélecteur de fichier natif (enregistrement) puis délègue au VM.
    /// Le dialog vit dans la vue (non testé en TestFX) ; l'écriture est testée côté ViewModel.
    @FXML
    private void exporter() {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Exporter la vue multi-sites en CSV");
        selecteur.setInitialFileName("vue-multisite.csv");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File fichier = selecteur.showSaveDialog(boutonExporter.getScene().getWindow());
        if (fichier != null) {
            // #291 : on exporte l'ordre RÉELLEMENT affiché (tri par clic d'en-tête inclus), donc un
            // instantané des items de la table (SortedList), et non l'ordre interne du ViewModel.
            viewModel.exporter(fichier.toPath(), new ArrayList<>(tableLignes.getItems()));
        }
    }

    private static <T> StringConverter<T> convertisseur(java.util.function.Function<T, String> versTexte) {
        return new StringConverter<>() {
            @Override
            public String toString(T valeur) {
                return versTexte.apply(valeur);
            }

            @Override
            public T fromString(String libelle) {
                return null; // ComboBox non éditable : conversion inverse inutile
            }
        };
    }

    private static String libelleTri(TriMultisite tri) {
        if (tri == null) {
            return "";
        }
        return switch (tri) {
            case PAR_SITE -> "Par site";
            case PAR_ANNEE -> "Par année";
            case PAR_STATUT -> "Par statut";
            case PAR_VERDICT -> "Par verdict";
        };
    }
}

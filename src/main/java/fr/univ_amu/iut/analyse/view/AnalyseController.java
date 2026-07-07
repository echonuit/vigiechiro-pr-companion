package fr.univ_amu.iut.analyse.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.analyse.viewmodel.Regroupement;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/// Controller de l'écran **« Espèces & observations »** (`Analyse.fxml`). Pur câblage : lie les deux
/// tables (inventaire par espèce / par carré), le sélecteur de regroupement et le filtre de statut à
/// l'[AnalyseViewModel]. La table affichée suit le regroupement ; le chargement initial est déclenché ici
/// (écran sans paramètre). Aucun accès base de données (règle ArchUnit `view_sans_jdbc`).
///
/// Implémente [RafraichirAuRetour] : l'écran reste vivant dans l'historique du [Navigateur] ; quand on y
/// revient après avoir modifié des observations ailleurs (validation d'un passage…), l'inventaire est
/// rechargé pour ne pas afficher des compteurs périmés.
public class AnalyseController implements RafraichirAuRetour {

    private final AnalyseViewModel viewModel;
    private final OuvrirPassage ouvrirPassage;
    private final OuvrirAudio ouvrirAudio;

    /// État de la bascule Tableau ⇄ Carte (vue, pas de domaine) ; la carte elle-même est gérée par
    /// [CarteRepartition], installée **paresseusement** au premier affichage (`null` tant qu'on reste en
    /// tableau).
    private final BooleanProperty carteAffichee = new SimpleBooleanProperty(this, "carteAffichee", false);
    private CarteRepartition carteRepartition;

    /// Richesse (nombre d'espèces distinctes) par numéro de carré, tenue à jour depuis l'inventaire par
    /// carré, pour afficher la richesse du carré de chaque observation du détail (lien avec la carte).
    private final Map<String, Integer> richesseParCarre = new HashMap<>();

    @FXML
    private StackPane zoneCarte;

    @FXML
    private Button boutonCarte;

    @FXML
    private Label lblResume;

    @FXML
    private Label lblMessage;

    @FXML
    private ComboBox<Regroupement> choixRegroupement;

    @FXML
    private TextField champRecherche;

    @FXML
    private MenuButton menuAjoutFiltre;

    @FXML
    private FlowPane pucesFiltres;

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
    public AnalyseController(AnalyseViewModel viewModel, OuvrirPassage ouvrirPassage, OuvrirAudio ouvrirAudio) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvrirAudio = Objects.requireNonNull(ouvrirAudio, "ouvrirAudio");
    }

    @FXML
    private void initialize() {
        configurerColonnes();
        tableEspeces.setItems(viewModel.especes());
        tableCarres.setItems(viewModel.carres());

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

        // La colonne « Espèces du carré » du détail lit la richesse depuis l'inventaire par carré : on la
        // tient à jour quand cet inventaire change (chargement, filtre statut).
        viewModel.carresCarte().addListener((InvalidationListener) observable -> majRichesseParCarre());

        viewModel.rafraichir();
    }

    /// Reconstruit la table de correspondance carré → richesse (nb d'espèces distinctes) depuis l'inventaire
    /// par carré, et rafraîchit le détail pour que sa colonne « Espèces du carré » se mette à jour.
    private void majRichesseParCarre() {
        richesseParCarre.clear();
        for (CarreEspeces carre : viewModel.carresCarte()) {
            richesseParCarre.put(carre.numeroCarre(), carre.richesse());
        }
        tableObservations.refresh();
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

        // La ligne sélectionnée de l'inventaire pilote le détail (null en Par carré → détail vidé).
        tableEspeces
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, ancien, espece) -> viewModel.selectionnerEspece(espece, statutCourant()));

        lblDetailTitre.textProperty().bind(viewModel.detailTitreProperty());

        // Placeholder tant qu'aucune observation n'est listée (aucune espèce sélectionnée).
        var detailVide = Bindings.isEmpty(viewModel.observations());
        lblDetailVide.visibleProperty().bind(detailVide);
        lblDetailVide.managedProperty().bind(detailVide);

        // Actions du détail actives seulement quand une observation est sélectionnée.
        var selection = tableObservations.getSelectionModel().selectedItemProperty();
        boutonOuvrirPassage.disableProperty().bind(selection.isNull());
        boutonEcouter.disableProperty().bind(selection.isNull());

        // Double-clic sur une observation → ouvre son passage.
        tableObservations.setRowFactory(tableau -> {
            TableRow<ObservationEspece> ligne = new TableRow<>();
            ligne.setOnMouseClicked(evenement -> {
                if (evenement.getButton() == MouseButton.PRIMARY
                        && evenement.getClickCount() == 2
                        && !ligne.isEmpty()) {
                    ouvrirPassageDe(ligne.getItem());
                }
            });
            return ligne;
        });
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
        carteAffichee.addListener((obs, ancien, affichee) -> {
            if (Boolean.TRUE.equals(affichee) && carteRepartition == null) {
                carteRepartition = new CarteRepartition(
                        viewModel.carresCarte(), viewModel.carresEspeceSelectionnee(), carteAffichee);
                carteRepartition.installerDans(zoneCarte);
            }
        });
    }

    /// « 🗺️ Carte » / « 📋 Tableau » : bascule l'affichage de la zone maître entre l'inventaire et la carte.
    @FXML
    private void basculerCarte() {
        carteAffichee.set(!carteAffichee.get());
        boutonCarte.setText(carteAffichee.get() ? "📋 Tableau" : "🗺️ Carte");
    }

    /// Rechargé par le [fr.univ_amu.iut.commun.view.Navigateur] au **retour** sur l'écran : des
    /// observations ont pu être validées/corrigées entre-temps, l'inventaire est donc ré-interrogé.
    @Override
    public void rafraichirAuRetour() {
        viewModel.rafraichir();
    }

    /// « 📤 Exporter… » : sélecteur de fichier natif (enregistrement) puis délègue au ViewModel l'écriture
    /// CSV de l'inventaire **affiché** (liste filtrée courante). Le dialog vit dans la vue (non testé en
    /// TestFX) ; l'écriture est testée côté ViewModel/service.
    @FXML
    private void exporter() {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Exporter l'inventaire des espèces en CSV");
        selecteur.setInitialFileName("inventaire-especes.csv");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File fichier = selecteur.showSaveDialog(boutonExporter.getScene().getWindow());
        if (fichier != null) {
            viewModel.exporter(fichier.toPath());
        }
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
        ObservationEspece observation = tableObservations.getSelectionModel().getSelectedItem();
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
    }

    /// Libellé du passage d'une observation : date d'enregistrement et n° de passage (`2026-06-22 · n°2`).
    /// Richesse (nb d'espèces distinctes) du carré `numeroCarre`, ou `—` si inconnue de l'inventaire.
    private String richesseDuCarre(String numeroCarre) {
        Integer richesse = richesseParCarre.get(numeroCarre);
        return richesse == null ? "—" : richesse.toString();
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

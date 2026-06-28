package fr.univ_amu.iut.sites.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.view.OuvrirImportation;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.viewmodel.CartePoint;
import fr.univ_amu.iut.sites.viewmodel.LignePassage;
import fr.univ_amu.iut.sites.viewmodel.SiteDetailViewModel;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/// Controller de l'écran de détail **M-Site-detail** (`SiteDetail.fxml`).
///
/// Câble la fiche d'identité (bandeau), les cartes de points et le tableau des passages aux
/// propriétés du [SiteDetailViewModel]. Les points (nombre variable) sont reconstruits en code à
/// chaque changement de la liste observable ; le tableau des passages est piloté par les
/// `cellValueFactory`, avec un badge coloré (couleur **dérivée** du statut/verdict) pour les
/// colonnes Statut et Verdict.
///
/// L'écran délègue toute navigation à [NavigationSites] : retour à l'accueil (fil d'Ariane),
/// ouverture des modales de point, retour à l'accueil après suppression du site.
///
/// Implémente [RafraichirAuRetour] : quand on revient sur la fiche après avoir ouvert un passage et
/// l'avoir fait avancer (vérification, dépôt, validation), le tableau des passages est rechargé pour
/// refléter le nouveau statut/verdict (sinon il afficherait un état périmé, l'écran restant vivant).
public class SiteDetailController implements RafraichirAuRetour {

    private final SiteDetailViewModel viewModel;
    private final NavigationSites navigation;
    private final OuvrirPassage ouvrirPassage;
    private final OuvrirImportation ouvrirImportation;
    private final OuvrirMultisite ouvrirMultisite;

    @FXML
    private Label titre;

    @FXML
    private Label sousTitre;

    @FXML
    private Label valNumeroCarre;

    @FXML
    private Label valDepartement;

    @FXML
    private Label valProtocole;

    @FXML
    private Label valDateCreation;

    @FXML
    private Label valDerniereNuit;

    @FXML
    private Label valPassages;

    @FXML
    private Button boutonModifier;

    @FXML
    private Button boutonSupprimer;

    @FXML
    private StackPane enveloppeSupprimer;

    @FXML
    private FlowPane cartesPoints;

    @FXML
    private TableView<LignePassage> tablePassages;

    @FXML
    private TableColumn<LignePassage, String> colDate;

    @FXML
    private TableColumn<LignePassage, String> colPoint;

    @FXML
    private TableColumn<LignePassage, String> colNumero;

    @FXML
    private TableColumn<LignePassage, String> colStatut;

    @FXML
    private TableColumn<LignePassage, String> colVerdict;

    @FXML
    private TableColumn<LignePassage, String> colEnregistreur;

    @FXML
    private TableColumn<LignePassage, String> colDepose;

    @Inject
    public SiteDetailController(
            SiteDetailViewModel viewModel,
            NavigationSites navigation,
            OuvrirPassage ouvrirPassage,
            OuvrirImportation ouvrirImportation,
            OuvrirMultisite ouvrirMultisite) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvrirImportation = Objects.requireNonNull(ouvrirImportation, "ouvrirImportation");
        this.ouvrirMultisite = Objects.requireNonNull(ouvrirMultisite, "ouvrirMultisite");
    }

    /// Charge le site à afficher (appelée par [NavigationSites] juste après le chargement FXML).
    public void afficher(Site site) {
        viewModel.chargerSite(site);
    }

    /// Rechargé par le [fr.univ_amu.iut.commun.view.Navigateur] quand on **revient** sur la fiche
    /// (← Retour ou fil d'Ariane) : un passage ouvert depuis le tableau a pu avancer pendant qu'on
    /// était dessus. On recharge points et passages du site courant pour réafficher les statuts/
    /// verdicts réels plutôt qu'un état périmé.
    @Override
    public void rafraichirAuRetour() {
        if (viewModel.siteCourant() != null) {
            viewModel.rafraichir();
        }
    }

    @FXML
    private void initialize() {
        titre.textProperty().bind(viewModel.titreProperty());
        sousTitre.textProperty().bind(viewModel.sousTitreProperty());
        valNumeroCarre.textProperty().bind(viewModel.numeroCarreProperty());
        valDepartement.textProperty().bind(viewModel.departementProperty());
        valProtocole.textProperty().bind(viewModel.protocoleProperty());
        valDateCreation.textProperty().bind(viewModel.dateCreationProperty());
        valDerniereNuit.textProperty().bind(viewModel.derniereNuitProperty());
        valPassages.textProperty().bind(viewModel.passagesDeLAnneeProperty());
        boutonSupprimer
                .disableProperty()
                .bind(viewModel.suppressionPossibleProperty().not());
        // Un Tooltip ne s'affiche pas sur un Button désactivé : on l'installe sur l'enveloppe (qui
        // reçoit le survol) et on lie son texte à l'état pour expliquer le blocage (règle métier R…).
        Tooltip infoSupprimer = new Tooltip();
        infoSupprimer
                .textProperty()
                .bind(Bindings.when(viewModel.suppressionPossibleProperty())
                        .then("Supprimer ce site et ses points d'écoute.")
                        .otherwise("Suppression impossible : ce site porte des passages."
                                + " Supprimez d'abord les passages rattachés."));
        Tooltip.install(enveloppeSupprimer, infoSupprimer);
        boutonModifier.setTooltip(new Tooltip("Modifier la fiche du site (carré, nom, protocole…)."));
        configurerColonnes();
        tablePassages.setItems(viewModel.passages());
        tablePassages.setRowFactory(tableau -> {
            TableRow<LignePassage> ligne = new TableRow<>();
            ligne.setOnMouseClicked(evenement -> {
                if (evenement.getButton() == MouseButton.PRIMARY
                        && evenement.getClickCount() == 2
                        && !ligne.isEmpty()) {
                    ouvrirPassage.ouvrir(ligne.getItem().idPassage(), contexteSite(ligne.getItem()));
                }
            });
            return ligne;
        });
        viewModel.points().addListener((ListChangeListener<CartePoint>) changement -> reconstruirePoints());
        reconstruirePoints();
    }

    /// Contexte d'identité (carré/code/nom) transmis à M-Passage pour éviter une dépendance
    /// `passage → sites` : la vue passage affiche ces libellés sans rejoindre les tables `sites`.
    private ContexteSite contexteSite(LignePassage ligne) {
        Site site = viewModel.siteCourant();
        return new ContexteSite(site.numeroCarre(), ligne.codePoint(), site.nomConvivial());
    }

    @FXML
    private void ajouterPoint() {
        navigation.ouvrirModaleCreationPoint(fenetre(), viewModel.siteCourant(), viewModel::rafraichir);
    }

    /// Ouvre l'assistant « Importer une nuit » avec ce site déjà pré-rattaché (raccourci contextuel).
    @FXML
    private void importerNuit() {
        ouvrirImportation.ouvrirPourSite(viewModel.siteCourant().id());
    }

    /// « Voir sur la carte » : ouvre la vue multi-sites centrée et surlignée sur le carré de ce site.
    @FXML
    private void voirSurCarte() {
        Site site = viewModel.siteCourant();
        if (site != null) {
            ouvrirMultisite.ouvrirSurCarre(site.numeroCarre());
        }
    }

    /// Ouvre la boîte d'édition pré-remplie ; à la validation, applique la modification via le
    /// ViewModel (qui recharge la fiche). Un refus métier (carré déjà pris) ou un format invalide
    /// (R1) est rapporté sans quitter l'écran.
    @FXML
    private void modifierSite() {
        demanderModificationSite(viewModel.siteCourant()).ifPresent(saisie -> {
            try {
                viewModel.modifierSite(saisie.numeroCarre(), saisie.nom(), saisie.protocole(), saisie.commentaire());
            } catch (RegleMetierException | IllegalArgumentException refus) {
                alerteErreur(refus.getMessage());
            }
        });
    }

    @FXML
    private void supprimerSite() {
        if (!confirmer("Supprimer ce site et ses points d'écoute ?")) {
            return;
        }
        try {
            viewModel.supprimerSite();
            navigation.ouvrirAccueil();
        } catch (RegleMetierException refus) {
            alerteErreur(refus.getMessage());
        }
    }

    private void configurerColonnes() {
        colDate.setCellValueFactory(cd -> valeur(cd.getValue().date()));
        colPoint.setCellValueFactory(cd -> valeur(cd.getValue().codePoint()));
        colNumero.setCellValueFactory(cd -> valeur(cd.getValue().numeroPassage()));
        colStatut.setCellValueFactory(cd -> valeur(cd.getValue().statutLibelle()));
        colVerdict.setCellValueFactory(cd -> valeur(cd.getValue().verdictLibelle()));
        colEnregistreur.setCellValueFactory(cd -> valeur(cd.getValue().enregistreur()));
        colDepose.setCellValueFactory(cd -> valeur(cd.getValue().deposeLe()));
        colStatut.setCellFactory(colonne -> celluleBadge(LignePassage::statutClasseCss));
        colVerdict.setCellFactory(colonne -> celluleBadge(LignePassage::verdictClasseCss));
    }

    private void reconstruirePoints() {
        cartesPoints.getChildren().clear();
        for (CartePoint carte : viewModel.points()) {
            cartesPoints.getChildren().add(construireCartePoint(carte));
        }
    }

    private VBox construireCartePoint(CartePoint carte) {
        PointDEcoute point = carte.point();
        Label code = new Label(point.code());
        code.getStyleClass().add("carte-point-code");
        Label description = new Label(libelleDescription(point));
        description.getStyleClass().add("carte-point-desc");
        Node gps = construireBadgeGps(carte);
        Label passages = new Label(carte.nombrePassages() + " passage(s) rattaché(s)");
        passages.getStyleClass().add("carte-point-desc");
        VBox boite = new VBox(code, description, gps, passages, actionsPoint(carte));
        boite.getStyleClass().add("carte-point");
        return boite;
    }

    /// Badge GPS de la carte de point : un [Hyperlink] qui, quand les coordonnées sont présentes, ouvre
    /// **LA carte multi-sites centrée sur ce point** (#154) ; sinon un simple libellé « manquant ». On
    /// renvoie vers la carte de référence (qui montre déjà le fond OSM et permet de corriger la position
    /// en mode édition) plutôt que vers un OpenStreetMap externe.
    private Node construireBadgeGps(CartePoint carte) {
        PointDEcoute point = carte.point();
        if (!carte.gpsPresent()) {
            Label manquant = new Label("⚠ GPS manquant");
            manquant.getStyleClass().add("gps-manquant");
            return manquant;
        }
        Hyperlink lien = new Hyperlink("✓ GPS — voir sur la carte");
        lien.getStyleClass().add("gps-ok");
        lien.setOnAction(evenement -> ouvrirMultisite.ouvrirSurPoint(
                viewModel.siteCourant().numeroCarre(), point.latitude(), point.longitude()));
        lien.setTooltip(
                new Tooltip("Voir " + point.latitude() + ", " + point.longitude() + " sur la carte multi-sites"));
        return lien;
    }

    private HBox actionsPoint(CartePoint carte) {
        Hyperlink editer = new Hyperlink("✏ Modifier");
        editer.setOnAction(evenement -> navigation.ouvrirModaleEditionPoint(
                fenetre(), viewModel.siteCourant(), carte.point(), viewModel::rafraichir));
        Hyperlink supprimer = new Hyperlink("🗑 Supprimer");
        supprimer.setOnAction(evenement -> supprimerPoint(carte));
        HBox actions = new HBox(editer, supprimer);
        actions.getStyleClass().add("carte-point-actions");
        return actions;
    }

    private void supprimerPoint(CartePoint carte) {
        if (carte.aDesPassages()) {
            alerteErreur("Le point « " + carte.point().code() + " » porte des passages : suppression bloquée.");
            return;
        }
        if (confirmer("Supprimer le point « " + carte.point().code() + " » ?")) {
            viewModel.supprimerPoint(carte.point());
        }
    }

    private TableCell<LignePassage, String> celluleBadge(Function<LignePassage, String> classeCss) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String valeur, boolean vide) {
                super.updateItem(valeur, vide);
                getStyleClass().removeIf(classe -> classe.startsWith("badge"));
                if (vide
                        || valeur == null
                        || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    setText(valeur);
                    getStyleClass()
                            .addAll("badge", classeCss.apply(getTableRow().getItem()));
                }
            }
        };
    }

    /// Boîte de dialogue d'édition de la fiche, **pré-remplie** avec les valeurs courantes (carré,
    /// nom, protocole, commentaire). Le protocole est choisi dans une liste affichant son libellé
    /// métier. Renvoie la saisie validée, ou vide si l'utilisateur annule.
    private Optional<SaisieEditionSite> demanderModificationSite(Site site) {
        Dialog<SaisieEditionSite> dialogue = new Dialog<>();
        dialogue.setTitle("Modifier le site");
        dialogue.setHeaderText("Fiche du carré " + site.numeroCarre() + ".");
        ButtonType valider = new ButtonType("Enregistrer", ButtonType.OK.getButtonData());
        dialogue.getDialogPane().getButtonTypes().addAll(valider, ButtonType.CANCEL);

        TextField champCarre = new TextField(site.numeroCarre());
        champCarre.setPromptText("640380");
        TextField champNom = new TextField(ouVide(site.nomConvivial()));
        champNom.setPromptText("Étang de la Tuilière (optionnel)");
        ComboBox<Protocole> champProtocole = new ComboBox<>();
        champProtocole.getItems().setAll(Protocole.values());
        champProtocole.setValue(site.protocole());
        champProtocole.setConverter(new StringConverter<>() {
            @Override
            public String toString(Protocole protocole) {
                return protocole == null ? "" : protocole.libelle();
            }

            @Override
            public Protocole fromString(String libelle) {
                return Protocole.parLibelle(libelle);
            }
        });
        TextField champCommentaire = new TextField(ouVide(site.commentaire()));
        champCommentaire.setPromptText("Commentaire (optionnel)");

        GridPane grille = new GridPane();
        grille.setHgap(8);
        grille.setVgap(8);
        grille.addRow(0, new Label("N° de carré"), champCarre);
        grille.addRow(1, new Label("Nom convivial"), champNom);
        grille.addRow(2, new Label("Protocole"), champProtocole);
        grille.addRow(3, new Label("Commentaire"), champCommentaire);
        dialogue.getDialogPane().setContent(grille);

        dialogue.setResultConverter(bouton -> bouton == valider
                ? new SaisieEditionSite(
                        champCarre.getText(),
                        vide(champNom.getText()),
                        champProtocole.getValue(),
                        vide(champCommentaire.getText()))
                : null);
        return dialogue.showAndWait();
    }

    /// Texte saisi → `null` si vide (champ optionnel non renseigné).
    private static String vide(String texte) {
        return texte == null || texte.isBlank() ? null : texte;
    }

    /// Valeur de champ → chaîne vide si `null` (pour pré-remplir un `TextField`).
    private static String ouVide(String texte) {
        return texte == null ? "" : texte;
    }

    /// Valeurs saisies dans la boîte d'édition d'un site (carré requis ; nom et commentaire
    /// optionnels ; protocole choisi dans la liste).
    private record SaisieEditionSite(String numeroCarre, String nom, Protocole protocole, String commentaire) {}

    private Window fenetre() {
        return cartesPoints.getScene().getWindow();
    }

    private boolean confirmer(String message) {
        Alert alerte = new Alert(AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        return alerte.showAndWait().filter(bouton -> bouton == ButtonType.OK).isPresent();
    }

    private void alerteErreur(String message) {
        Alert alerte = new Alert(AlertType.WARNING, message, ButtonType.OK);
        alerte.setHeaderText("Action impossible");
        alerte.showAndWait();
    }

    private static ReadOnlyStringWrapper valeur(String texte) {
        return new ReadOnlyStringWrapper(texte);
    }

    private static String libelleDescription(PointDEcoute point) {
        return point.description() == null ? "(pas de description)" : point.description();
    }
}

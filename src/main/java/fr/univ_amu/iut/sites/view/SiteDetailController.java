package fr.univ_amu.iut.sites.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.viewmodel.CartePoint;
import fr.univ_amu.iut.sites.viewmodel.LignePassage;
import fr.univ_amu.iut.sites.viewmodel.SiteDetailViewModel;
import java.util.Objects;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

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
public class SiteDetailController {

  private final SiteDetailViewModel viewModel;
  private final NavigationSites navigation;

  @FXML private Label titre;
  @FXML private Label sousTitre;
  @FXML private Label valNumeroCarre;
  @FXML private Label valDepartement;
  @FXML private Label valProtocole;
  @FXML private Label valDateCreation;
  @FXML private Label valDerniereNuit;
  @FXML private Label valPassages;
  @FXML private Button boutonModifier;
  @FXML private Button boutonSupprimer;
  @FXML private FlowPane cartesPoints;
  @FXML private TableView<LignePassage> tablePassages;
  @FXML private TableColumn<LignePassage, String> colDate;
  @FXML private TableColumn<LignePassage, String> colPoint;
  @FXML private TableColumn<LignePassage, String> colNumero;
  @FXML private TableColumn<LignePassage, String> colStatut;
  @FXML private TableColumn<LignePassage, String> colVerdict;
  @FXML private TableColumn<LignePassage, String> colEnregistreur;
  @FXML private TableColumn<LignePassage, String> colDepose;

  @Inject
  public SiteDetailController(SiteDetailViewModel viewModel, NavigationSites navigation) {
    this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    this.navigation = Objects.requireNonNull(navigation, "navigation");
  }

  /// Charge le site à afficher (appelée par [NavigationSites] juste après le chargement FXML).
  public void afficher(Site site) {
    viewModel.chargerSite(site);
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
    boutonSupprimer.disableProperty().bind(viewModel.suppressionPossibleProperty().not());
    boutonModifier.setDisable(true);
    boutonModifier.setTooltip(new Tooltip("Édition de la fiche site : à venir."));
    configurerColonnes();
    tablePassages.setItems(viewModel.passages());
    viewModel
        .points()
        .addListener((ListChangeListener<CartePoint>) changement -> reconstruirePoints());
    reconstruirePoints();
  }

  @FXML
  private void retour() {
    navigation.ouvrirAccueil();
  }

  @FXML
  private void ajouterPoint() {
    navigation.ouvrirModaleCreationPoint(fenetre(), viewModel.siteCourant(), viewModel::rafraichir);
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
    Label gps = new Label(carte.gpsPresent() ? "✓ GPS" : "⚠ GPS manquant");
    gps.getStyleClass().add(carte.gpsPresent() ? "gps-ok" : "gps-manquant");
    Label passages = new Label(carte.nombrePassages() + " passage(s) rattaché(s)");
    passages.getStyleClass().add("carte-point-desc");
    VBox boite = new VBox(code, description, gps, passages, actionsPoint(carte));
    boite.getStyleClass().add("carte-point");
    return boite;
  }

  private HBox actionsPoint(CartePoint carte) {
    Hyperlink editer = new Hyperlink("✏ Modifier");
    editer.setOnAction(
        evenement ->
            navigation.ouvrirModaleEditionPoint(
                fenetre(), viewModel.siteCourant(), carte.point(), viewModel::rafraichir));
    Hyperlink supprimer = new Hyperlink("🗑 Supprimer");
    supprimer.setOnAction(evenement -> supprimerPoint(carte));
    HBox actions = new HBox(editer, supprimer);
    actions.getStyleClass().add("carte-point-actions");
    return actions;
  }

  private void supprimerPoint(CartePoint carte) {
    if (carte.aDesPassages()) {
      alerteErreur(
          "Le point « " + carte.point().code() + " » porte des passages : suppression bloquée.");
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
        if (vide || valeur == null || getTableRow() == null || getTableRow().getItem() == null) {
          setText(null);
        } else {
          setText(valeur);
          getStyleClass().addAll("badge", classeCss.apply(getTableRow().getItem()));
        }
      }
    };
  }

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

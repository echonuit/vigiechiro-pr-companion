package fr.univ_amu.iut.sites.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.viewmodel.CarteSite;
import fr.univ_amu.iut.sites.viewmodel.SitesViewModel;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/// Controller de l'écran d'accueil **M-Sites** (`MesSites.fxml`).
///
/// Rôle de pur câblage (patron CM4) : il lie l'état vide aux propriétés du [SitesViewModel] et
/// reconstruit la liste de cartes à chaque changement de la liste observable. Les cartes sont bâties en
/// code (et non en FXML) car leur nombre est dynamique ; chaque carte est cliquable et délègue
/// l'ouverture du détail à [NavigationSites]. Aucun accès base de données ici : tout passe par le
/// ViewModel (règle ArchUnit `view_sans_jdbc`).
///
/// Implémente [ResumeStatut] (#693) : le résumé de l'écran (« N sites déclarés · N passages ») est
/// affiché dans la **barre de statut** plutôt qu'en sous-titre, le titre étant redondant avec le fil
/// d'Ariane.
public class MesSitesController implements ResumeStatut {

    private static final String STYLE_STAT_NOMBRE = "carte-stat-nombre";
    private static final String STYLE_STAT_LIBELLE = "carte-stat-libelle";
    private static final String STYLE_CARTE_DETAIL = "carte-detail";

    private final SitesViewModel viewModel;
    private final NavigationSites navigation;

    /// Résumé de l'écran (« N sites déclarés · N passages »), déporté en zone centre de la barre de
    /// statut (#693) au lieu d'un sous-titre.
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    @FXML
    private ScrollPane zoneListe;

    @FXML
    private VBox listeCartes;

    @FXML
    private VBox etatVide;

    @Inject
    public MesSitesController(SitesViewModel viewModel, NavigationSites navigation) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    @Override
    public ReadOnlyObjectProperty<ZonesStatut> zonesStatutProperty() {
        return zonesStatut.getReadOnlyProperty();
    }

    @FXML
    private void initialize() {
        // Le résumé de l'écran occupe la zone centre de la barre de statut (#693), plus de sous-titre.
        zonesStatut.bind(Bindings.createObjectBinding(
                () -> ZonesStatut.centre(viewModel.sousTitreProperty().get()), viewModel.sousTitreProperty()));
        zoneListe.visibleProperty().bind(viewModel.videProperty().not());
        zoneListe.managedProperty().bind(viewModel.videProperty().not());
        etatVide.visibleProperty().bind(viewModel.videProperty());
        etatVide.managedProperty().bind(viewModel.videProperty());
        viewModel.cartes().addListener((ListChangeListener<CarteSite>) changement -> reconstruire());
        viewModel.rafraichir();
    }

    /// Action des boutons « + Nouveau site » (bandeau et état vide).
    @FXML
    private void nouveauSite() {
        demanderCreationSite().ifPresent(this::creerSite);
    }

    private void creerSite(SaisieSite saisie) {
        try {
            viewModel.creerSite(saisie.numeroCarre(), saisie.nom(), Protocole.STANDARD, null);
        } catch (IllegalArgumentException | RegleMetierException refus) {
            alerteErreur(refus.getMessage());
        }
    }

    private void reconstruire() {
        listeCartes.getChildren().clear();
        for (CarteSite carte : viewModel.cartes()) {
            listeCartes.getChildren().add(construireCarte(carte));
        }
    }

    private HBox construireCarte(CarteSite carte) {
        HBox boite = new HBox();
        boite.getStyleClass().add("carte-site");
        boite.getChildren()
                .addAll(
                        colonneIdentite(carte),
                        separateur(),
                        colonneStatsPoints(carte),
                        separateur(),
                        colonneStatsPassages(carte),
                        separateur(),
                        colonneActions());
        boite.setOnMouseClicked(evenement -> navigation.ouvrirDetail(carte.site()));
        // Accessibilité clavier : la carte (HBox, pas un Control) doit être atteignable au Tab et
        // activable à Entrée/Espace, comme un bouton (opérabilité ISO 25010).
        boite.setFocusTraversable(true);
        boite.setOnKeyPressed(evenement -> {
            if (evenement.getCode() == KeyCode.ENTER || evenement.getCode() == KeyCode.SPACE) {
                navigation.ouvrirDetail(carte.site());
            }
        });
        return boite;
    }

    private VBox colonneIdentite(CarteSite carte) {
        Site site = carte.site();
        Label numero = new Label("Carré " + site.numeroCarre());
        numero.getStyleClass().add("carte-titre");
        Label nom = new Label(libelleNom(site));
        nom.getStyleClass().add("carte-sous-titre");
        Label badge = new Label(carte.libelleFraicheur());
        badge.getStyleClass().addAll("badge", carte.fraicheur().classeBadge());
        VBox colonne = colonne(numero, nom, badge);
        HBox.setHgrow(colonne, Priority.ALWAYS);
        return colonne;
    }

    private VBox colonneStatsPoints(CarteSite carte) {
        Label nombre = new Label(Integer.toString(carte.nombrePoints()));
        nombre.getStyleClass().add(STYLE_STAT_NOMBRE);
        Label libelle = new Label("points d'écoute");
        libelle.getStyleClass().add(STYLE_STAT_LIBELLE);
        Label codes = new Label(carte.codesPoints());
        codes.getStyleClass().add(STYLE_CARTE_DETAIL);
        return colonne(nombre, libelle, codes);
    }

    private VBox colonneStatsPassages(CarteSite carte) {
        Label nombre = new Label(Integer.toString(carte.passagesDeLAnnee()));
        nombre.getStyleClass().add(STYLE_STAT_NOMBRE);
        Label libelle = new Label("passages en " + carte.anneeReference());
        libelle.getStyleClass().add(STYLE_STAT_LIBELLE);
        Label complement = new Label(libelleComplementPassages(carte));
        complement.getStyleClass().add(STYLE_CARTE_DETAIL);
        return colonne(nombre, libelle, complement);
    }

    private VBox colonneActions() {
        // Invite visuelle (chevron) : la carte entière est cliquable/focusable et ouvre la fiche du
        // site, où se trouve l'action « Importer une nuit » (pré-rattachée au site). Pas de handler
        // propre sur le chevron (la propagation au parent déclencherait une double navigation).
        Label chevron = new Label("›");
        chevron.getStyleClass().add("carte-chevron");
        return colonne(chevron);
    }

    private Optional<SaisieSite> demanderCreationSite() {
        Dialog<SaisieSite> dialogue = new Dialog<>();
        dialogue.setTitle("Nouveau site de suivi");
        dialogue.setHeaderText("Déclarez un carré Vigie-Chiro (6 chiffres).");
        ButtonType valider = new ButtonType("Créer", ButtonType.OK.getButtonData());
        dialogue.getDialogPane().getButtonTypes().addAll(valider, ButtonType.CANCEL);
        TextField champCarre = new TextField();
        champCarre.setPromptText("640380");
        TextField champNom = new TextField();
        champNom.setPromptText("Étang de la Tuilière (optionnel)");
        GridPane grille = new GridPane();
        grille.setHgap(8);
        grille.setVgap(8);
        grille.addRow(0, new Label("N° de carré"), champCarre);
        grille.addRow(1, new Label("Nom convivial"), champNom);
        dialogue.getDialogPane().setContent(grille);
        dialogue.setResultConverter(
                bouton -> bouton == valider ? new SaisieSite(champCarre.getText(), vide(champNom.getText())) : null);
        return dialogue.showAndWait();
    }

    private void alerteErreur(String message) {
        Alert alerte = new Alert(AlertType.ERROR, message, ButtonType.OK);
        alerte.setHeaderText("Création impossible");
        alerte.showAndWait();
    }

    private static VBox colonne(Node... enfants) {
        VBox colonne = new VBox(enfants);
        colonne.getStyleClass().add("carte-colonne");
        return colonne;
    }

    private static Region separateur() {
        Region region = new Region();
        region.getStyleClass().add("carte-separateur");
        return region;
    }

    private static String libelleNom(Site site) {
        String localisation = site.commentaire() == null ? "" : " (" + site.commentaire() + ")";
        return "📍 " + (site.nomConvivial() == null ? "Sans nom" : site.nomConvivial()) + localisation;
    }

    private static String libelleComplementPassages(CarteSite carte) {
        if (carte.aDesPassagesAVerifier()) {
            return "dont " + carte.passagesAVerifier() + " à vérifier ⚠";
        }
        return carte.passagesDeLAnnee() == 0 ? "jamais utilisé" : "tous vérifiés";
    }

    private static String vide(String texte) {
        return texte == null || texte.isBlank() ? null : texte;
    }

    /// Valeurs saisies dans la modale de création de site (carré requis, nom optionnel).
    private record SaisieSite(String numeroCarre, String nom) {}
}

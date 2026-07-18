package fr.univ_amu.iut.sites.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.viewmodel.CarteSite;
import fr.univ_amu.iut.sites.viewmodel.SitesViewModel;
import fr.univ_amu.iut.sites.viewmodel.StatutPlateforme;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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

    private static final String STYLE_STAT_NOMBRE = "carte-site-nombre";
    private static final String STYLE_STAT_LIBELLE = "carte-site-libelle";
    private static final String STYLE_CARTE_DETAIL = "carte-detail";

    private final SitesViewModel viewModel;
    private final NavigationSites navigation;
    private final ExecuteurTache executeur;

    /// Résumé de l'écran (« N sites déclarés · N passages »), déporté en zone centre de la barre de
    /// statut (#693) au lieu d'un sous-titre.
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    /// Voile « … en cours » de l'écran (#1212) : chargement des cartes et synchronisation tournent
    /// hors du fil JavaFX ([IndicateurOccupation], patron #1014).
    private IndicateurOccupation occupation;

    @FXML
    private StackPane hoteOccupation;

    @FXML
    private ScrollPane zoneListe;

    @FXML
    private VBox listeCartes;

    @FXML
    private VBox etatVide;

    @FXML
    private Label lblErreur;

    @FXML
    private Button btnSyncVigieChiro;

    @FXML
    private Label lblSynchro;

    @Inject
    public MesSitesController(SitesViewModel viewModel, NavigationSites navigation, ExecuteurTache executeur) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
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
        // Erreur de chargement (#795) : rendue visible seulement quand un message est présent.
        lblErreur.textProperty().bind(viewModel.messageErreurProperty());
        lblErreur.visibleProperty().bind(viewModel.messageErreurProperty().isNotEmpty());
        lblErreur.managedProperty().bind(viewModel.messageErreurProperty().isNotEmpty());
        // Synchronisation à la demande (#1045) : bouton masqué quand la passerelle est absente (#937),
        // message de résultat rendu visible seulement quand il est présent.
        boolean peutRecuperer = viewModel.peutRecuperer();
        btnSyncVigieChiro.setVisible(peutRecuperer);
        btnSyncVigieChiro.setManaged(peutRecuperer);
        lblSynchro.textProperty().bind(viewModel.messageSynchroProperty());
        lblSynchro.visibleProperty().bind(viewModel.messageSynchroProperty().isNotEmpty());
        lblSynchro.managedProperty().bind(viewModel.messageSynchroProperty().isNotEmpty());
        viewModel.cartes().addListener((ListChangeListener<CarteSite>) changement -> reconstruire());
        occupation = new IndicateurOccupation(hoteOccupation, executeur);
        // Bouton relâché par binding sur l'occupation (#1254) : plus de setDisable posé à la main de
        // part et d'autre du travail, plus de bouton figé si le travail échoue.
        btnSyncVigieChiro.disableProperty().bind(occupation.enCoursProperty());
        // Chargement initial hors du fil JavaFX (#1212) : lectures base sous voile, erreur routée vers
        // le filet de l'écran (#795), application des cartes sur le fil JavaFX.
        occupation.occuper(
                "Chargement de vos sites…", viewModel::charger, viewModel::appliquer, viewModel::signalerErreur);
    }

    /// Action « Récupérer depuis VigieChiro » (#1045, déportée #1212) : pull best-effort puis
    /// relecture des cartes hors du fil JavaFX, sous le voile d'occupation ; le résultat (cartes +
    /// message) s'applique sur le fil JavaFX, l'échec rejoint le message de synchronisation.
    @FXML
    private void synchroniserVigieChiro() {
        occupation.occuper(
                "Synchronisation Vigie-Chiro en cours…",
                viewModel::synchroniserEtRecharger,
                viewModel::appliquerSynchro,
                viewModel::signalerErreurSynchro);
    }

    /// Action des boutons « + Nouveau site » (bandeau et état vide).
    @FXML
    private void nouveauSite() {
        // La modale porte la saisie, la validation en direct et le refus métier (#1431). Le Dialog bâti
        // ici se terminait par un showAndWait : déclarer un site - l'entrée du produit - n'était jouable
        // dans aucun test.
        navigation.ouvrirModaleCreationSite(listeCartes.getScene().getWindow(), viewModel::rafraichir);
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
        // Un lecteur d'écran doit l'annoncer comme un bouton et lire l'identité du site (#799).
        boite.setAccessibleRole(AccessibleRole.BUTTON);
        boite.setAccessibleText("Site carré " + carte.site().numeroCarre() + ", ouvrir le détail");
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
        VBox colonne = colonne(numero, nom, rangeeBadges(carte, badge));
        HBox.setHgrow(colonne, Priority.ALWAYS);
        return colonne;
    }

    /// Rangée de badges de la carte : la fraîcheur, suivie du badge de statut plateforme (#718, #734) —
    /// « Enregistré » (bleu) ou « Verrouillé » (vert, dépôt possible). Un site **absent** de la plateforme
    /// n'en porte pas ici : sur une liste, une pastille grise par carte ferait du bruit sans rien dire de
    /// plus. Le **détail** du site, lui, l'affiche : c'est là qu'on se demande pourquoi on ne peut pas
    /// déposer.
    ///
    /// Le libellé, la couleur et l'infobulle appartiennent à [StatutPlateforme] : cet écran ne les choisit
    /// plus (#734).
    private static Node rangeeBadges(CarteSite carte, Label fraicheur) {
        StatutPlateforme statut = carte.statutPlateforme();
        if (statut == StatutPlateforme.ABSENT) {
            return fraicheur;
        }
        return new HBox(8.0, fraicheur, BadgeStatutPlateforme.creer(statut));
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
}

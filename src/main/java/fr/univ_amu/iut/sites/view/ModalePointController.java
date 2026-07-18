package fr.univ_amu.iut.sites.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.BandeauRetour;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.carte.CarreGeo;
import fr.univ_amu.iut.commun.view.carte.CarteSites;
import fr.univ_amu.iut.commun.view.carte.DonneesCarte;
import fr.univ_amu.iut.commun.view.carte.EmpriseCarre;
import fr.univ_amu.iut.commun.view.carte.FournisseurEmpriseCarre;
import fr.univ_amu.iut.commun.view.carte.PointGeo;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.VerdictCarre;
import fr.univ_amu.iut.sites.viewmodel.PointEditViewModel;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/// Controller de la **modale d'ajout / d'édition d'un point d'écoute** (`ModalePoint.fxml`).
///
/// Lie les champs (code, descriptif, latitude, longitude) en bidirectionnel au
/// [PointEditViewModel], et reflète son état de présentation : titre et libellé du bouton (selon
/// création/édition), activation du bouton de validation ([PointEditViewModel#peutEnregistrer()]),
/// surlignage du code invalide (R2) et message d'erreur métier.
///
/// La modale se ferme elle-même via sa propre fenêtre ; après un enregistrement réussi, elle
/// exécute le `Runnable` fourni par l'appelant (typiquement le rafraîchissement de M-Site-detail).
public class ModalePointController {

    private static final String STYLE_CHAMP_INVALIDE = "champ-invalide";

    /// Message du carré STOC (#733) : alerte (divergence, hors grille) ou confirmation discrète.
    private static final String STYLE_CARRE_ALERTE = "message-carre-alerte";

    private static final String STYLE_CARRE_CONFIRME = "message-carre-confirme";

    /// Couleur du marqueur du point en cours d'édition (indigo de l'application).
    private static final Color COULEUR_POINT = Color.web("#3f51b5");

    /// Remplissage translucide du carré du site (repère, ne masque pas le fond de carte).
    private static final Color COULEUR_CARRE = Color.web("#3f51b5", 0.12);

    private final PointEditViewModel viewModel;

    /// Exécuteur du socle (#1014) : le contrôle du carré STOC est un appel **réseau**, il ne doit pas
    /// tourner sur le fil JavaFX. Synchrone en test (déterministe), en arrière-plan en production.
    private final ExecuteurTache executeur;

    private final CarteSites carte = new CarteSites();
    private Runnable apresSucces = () -> {};

    /// Numéro du carré du site courant (sert à centrer la carte et à placer le marqueur par défaut).
    private String numeroCarre;

    /// Garde anti-réentrance : vrai pendant qu'un déplacement du marqueur écrit les champs lat/lon, pour
    /// ne pas relancer un rendu de carte à chaque champ modifié (un seul rendu suit le déplacement).
    private boolean synchronisationDepuisCarte;

    @FXML
    private VBox racine;

    @FXML
    private Label titreModale;

    @FXML
    private TextField champCode;

    @FXML
    private HBox bandeauRetour;

    @FXML
    private Label messageErreur;

    @FXML
    private Button btnFermerRetour;

    @FXML
    private TextArea champDescription;

    @FXML
    private TextField champLatitude;

    @FXML
    private TextField champLongitude;

    @FXML
    private Button boutonValider;

    @FXML
    private StackPane zoneCarte;

    @FXML
    private Label messageCarre;

    @Inject
    public ModalePointController(PointEditViewModel viewModel, ExecuteurTache executeur) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
    }

    @FXML
    private void initialize() {
        titreModale.textProperty().bind(viewModel.titreProperty());
        champCode.textProperty().bindBidirectional(viewModel.codeProperty());
        champDescription.textProperty().bindBidirectional(viewModel.descriptionProperty());
        champLatitude.textProperty().bindBidirectional(viewModel.latitudeProperty());
        champLongitude.textProperty().bindBidirectional(viewModel.longitudeProperty());
        boutonValider.textProperty().bind(viewModel.libelleBoutonProperty());
        boutonValider.disableProperty().bind(viewModel.peutEnregistrer().not());
        // #1917 : bandeau partagé (ADR 0023). Le libellé s'appelait « messageErreur » et ne pouvait
        // donc rien porter d'autre qu'un échec ; la sévérité vit maintenant dans la valeur.
        BandeauRetour.installer(
                bandeauRetour, messageErreur, btnFermerRetour, viewModel.retourProperty(), viewModel::effacerRetour);
        viewModel.codeValide().addListener((observable, avant, valide) -> majStyleCode());
        viewModel.codeProperty().addListener((observable, avant, apres) -> majStyleCode());

        // Carte-outil de saisie GPS (#153), synchronisée dans les deux sens avec les champs lat/lon.
        zoneCarte.getChildren().add(carte);
        carte.setEditionActive(true);
        carte.setOnPointDeplace((point, lat, lon) -> {
            // Glisser/clavier → on écrit les champs (donc le ViewModel), sans relancer un rendu par champ.
            synchronisationDepuisCarte = true;
            viewModel.latitudeProperty().set(formatCoordonnee(lat));
            viewModel.longitudeProperty().set(formatCoordonnee(lon));
            synchronisationDepuisCarte = false;
            majMarqueur();
        });
        viewModel.latitudeProperty().addListener((observable, avant, apres) -> majMarqueurSiSaisie());
        viewModel.longitudeProperty().addListener((observable, avant, apres) -> majMarqueurSiSaisie());
        viewModel.codeProperty().addListener((observable, avant, apres) -> majMarqueur());

        // Contrôle du carré STOC (#733) : ce que la grille officielle dit de la position saisie. Message
        // affiché seulement quand il y a quelque chose à dire (hors ligne, on se tait).
        messageCarre.textProperty().bind(viewModel.messageCarreProperty());
        messageCarre.visibleProperty().bind(viewModel.messageCarreProperty().isNotEmpty());
        messageCarre.managedProperty().bind(viewModel.messageCarreProperty().isNotEmpty());
        viewModel
                .alerteCarreProperty()
                .addListener((observable, avant, alerte) -> majStyleMessageCarre(Boolean.TRUE.equals(alerte)));
        viewModel.latitudeProperty().addListener((observable, avant, apres) -> controlerCarre());
        viewModel.longitudeProperty().addListener((observable, avant, apres) -> controlerCarre());
    }

    /// Demande à la grille STOC ce qu'elle sait de la position saisie, **hors du fil JavaFX** (c'est un appel
    /// réseau : le faire ici gèlerait la modale à chaque frappe).
    ///
    /// Le contrôle ne **remplit** rien et ne **bloque** rien : le n° de carré appartient au site, pas au
    /// point, et l'observateur peut avoir de bonnes raisons de s'écarter de la grille. On dit ce qu'on
    /// sait ; il décide. En cas d'échec réseau on ne **sait plus** : on repasse à « indisponible » (message
    /// effacé) pour ne pas laisser traîner un verdict périmé - la trace de l'échec est, elle, journalisée au
    /// point de passage (#1523).
    private void controlerCarre() {
        executeur.executer(
                viewModel::controlerCarre,
                viewModel::appliquerControleCarre,
                echec -> viewModel.appliquerControleCarre(new VerdictCarre.Indisponible()));
    }

    /// Colore le message du carré : alerte (divergence, hors grille) ou simple confirmation.
    private void majStyleMessageCarre(boolean alerte) {
        messageCarre.getStyleClass().removeAll(STYLE_CARRE_ALERTE, STYLE_CARRE_CONFIRME);
        messageCarre.getStyleClass().add(alerte ? STYLE_CARRE_ALERTE : STYLE_CARRE_CONFIRME);
    }

    /// Prépare la modale en mode création et mémorise l'action de succès.
    public void demarrerCreation(Site site, Runnable apresSucces) {
        this.apresSucces = Objects.requireNonNull(apresSucces, "apresSucces");
        viewModel.preparerCreation(site);
        majStyleCode();
        preparerCarte(site);
    }

    /// Prépare la modale en mode édition (champs pré-remplis) et mémorise l'action de succès.
    public void demarrerEdition(Site site, PointDEcoute point, Runnable apresSucces) {
        this.apresSucces = Objects.requireNonNull(apresSucces, "apresSucces");
        viewModel.preparerEdition(site, point);
        majStyleCode();
        preparerCarte(site);
    }

    /// Centre la carte-outil sur le carré du site (une seule fois), puis pose le marqueur. Le carré vient
    /// du carroyage officiel, ou du repli autour du GPS existant s'il y en a un (carré hors référentiel).
    private void preparerCarte(Site site) {
        this.numeroCarre = site.numeroCarre();
        empriseDuCarre().ifPresent(carte::centrerSurCarre);
        majMarqueur();
    }

    /// Ré-affiche le marqueur seulement si la modification des champs vient d'une **saisie** (pas d'un
    /// déplacement du marqueur déjà en train d'écrire les champs).
    private void majMarqueurSiSaisie() {
        if (!synchronisationDepuisCarte) {
            majMarqueur();
        }
    }

    /// (Re)pose le carré-repère et le marqueur du point sur la carte, **sans recadrer** la vue (elle
    /// reste calée sur le carré). Marqueur à la position GPS si elle est saisie et valide, sinon au
    /// centre du carré en **position approximative** (à caler par glisser ou saisie — #153).
    private void majMarqueur() {
        if (numeroCarre == null) {
            return;
        }
        Optional<EmpriseCarre> emprise = empriseDuCarre();
        Optional<double[]> gps = viewModel.coordonneesValides();
        List<CarreGeo> carres = emprise.map(e -> List.of(new CarreGeo(numeroCarre, e, COULEUR_CARRE)))
                .orElse(List.of());
        List<PointGeo> points;
        if (gps.isPresent()) {
            points = List.of(new PointGeo(libelleMarqueur(), gps.get()[0], gps.get()[1], COULEUR_POINT));
        } else {
            points = emprise.map(e -> List.of(
                            new PointGeo(libelleMarqueur(), e.latCentre(), e.lonCentre(), COULEUR_POINT, null, true)))
                    .orElse(List.of());
        }
        carte.setDonnees(new DonneesCarte(carres, points), false);
    }

    /// Emprise (et centre) du carré du site, pour le repère et le centrage : **carroyage officiel** si le
    /// carré y figure ; sinon **repli autour du GPS saisi** (s'il y en a un), pour tracer tout de même un
    /// carré-repère (#153). Vide seulement si le carré est hors référentiel **et** sans GPS exploitable
    /// (impossible à situer). Le repère suit alors le GPS pendant la saisie : c'est voulu, faute de grille.
    private Optional<EmpriseCarre> empriseDuCarre() {
        if (numeroCarre == null) {
            return Optional.empty();
        }
        List<PointGeo> reperes = viewModel
                .coordonneesValides()
                .map(gps -> List.of(new PointGeo(libelleMarqueur(), gps[0], gps[1], COULEUR_POINT)))
                .orElseGet(List::of);
        return FournisseurEmpriseCarre.parDefaut().emprise(numeroCarre, reperes);
    }

    /// Libellé du marqueur : le code saisi, ou « Point » tant qu'aucun code n'est entré.
    private String libelleMarqueur() {
        String code = viewModel.codeProperty().get();
        return code == null || code.isBlank() ? "Point" : code;
    }

    /// Formate une coordonnée issue du glisser : 6 décimales (~0,1 m), point décimal.
    private static String formatCoordonnee(double valeur) {
        return String.format(Locale.ROOT, "%.6f", valeur);
    }

    @FXML
    private void valider() {
        if (viewModel.enregistrer()) {
            apresSucces.run();
            fermer();
        }
    }

    @FXML
    private void annuler() {
        fermer();
    }

    /// Surligne le champ code uniquement quand il est non vide et invalide (R2).
    private void majStyleCode() {
        boolean afficherErreur =
                !viewModel.codeValide().get() && !champCode.getText().isBlank();
        champCode.getStyleClass().remove(STYLE_CHAMP_INVALIDE);
        if (afficherErreur) {
            champCode.getStyleClass().add(STYLE_CHAMP_INVALIDE);
        }
    }

    private void fermer() {
        ((Stage) racine.getScene().getWindow()).close();
    }
}

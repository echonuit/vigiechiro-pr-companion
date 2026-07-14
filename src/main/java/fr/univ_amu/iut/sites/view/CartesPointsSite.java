package fr.univ_amu.iut.sites.view;

import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.NotificateurModifiable;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.viewmodel.CartePoint;
import fr.univ_amu.iut.sites.viewmodel.SiteDetailViewModel;
import java.util.Locale;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/// Câblage des **cartes de points d'écoute** de la fiche site (`cartesPoints`), extrait de
/// [SiteDetailController] pour l'alléger (pur câblage, seuil de cohésion PMD, #1087). Reconstruit une
/// carte par point d'écoute (code, description, badge GPS, compteur de passages, proximité, actions
/// Modifier/Supprimer) à chaque changement de [SiteDetailViewModel#points()], et pilote le repli d'état
/// vide (`lblAucunPoint`, #791) tant qu'aucune carte n'est affichée.
final class CartesPointsSite {

    /// Classe de style des lignes secondaires d'une carte de point (description, compteur, distance).
    private static final String STYLE_DESC = "carte-point-desc";

    private final FlowPane cartesPoints;
    private final SiteDetailViewModel viewModel;
    private final NavigationSites navigation;
    private final OuvrirMultisite ouvrirMultisite;

    /// Confirmation d'action destructive : le porteur **de l'écran** (#1013), et non plus un porteur
    /// fabriqué ici. Les cartes en construisaient un que rien n'exposait : il ne pouvait donc jamais être
    /// remplacé, et « Supprimer ce point » restait hors de portée d'un test (#1405).
    private final ConfirmateurModifiable confirmateur;

    /// Compte rendu : le porteur **de l'écran** (#1405), double capturant en test.
    private final NotificateurModifiable notificateur;

    private CartesPointsSite(
            FlowPane cartesPoints,
            SiteDetailViewModel viewModel,
            NavigationSites navigation,
            OuvrirMultisite ouvrirMultisite,
            ConfirmateurModifiable confirmateur,
            NotificateurModifiable notificateur) {
        this.cartesPoints = cartesPoints;
        this.viewModel = viewModel;
        this.navigation = navigation;
        this.ouvrirMultisite = ouvrirMultisite;
        this.confirmateur = confirmateur;
        this.notificateur = notificateur;
    }

    /// Installe le rendu des cartes sur `cartesPoints` : repli d'état vide lié à `lblAucunPoint` (#791,
    /// la liaison suit la liste vivante), reconstruction à chaque changement de la liste observable de
    /// `viewModel`, actions Modifier (modale d'édition via `navigation`) et Supprimer (confirmation puis
    /// appel au viewModel), lien GPS vers la carte multi-sites (`ouvrirMultisite`).
    static void installer(
            FlowPane cartesPoints,
            Label lblAucunPoint,
            SiteDetailViewModel viewModel,
            NavigationSites navigation,
            OuvrirMultisite ouvrirMultisite,
            ConfirmateurModifiable confirmateur,
            NotificateurModifiable notificateur) {
        var aucunPoint = Bindings.isEmpty(cartesPoints.getChildren());
        lblAucunPoint.visibleProperty().bind(aucunPoint);
        lblAucunPoint.managedProperty().bind(aucunPoint);
        CartesPointsSite cartes =
                new CartesPointsSite(cartesPoints, viewModel, navigation, ouvrirMultisite, confirmateur, notificateur);
        viewModel.points().addListener((ListChangeListener<CartePoint>) changement -> cartes.reconstruire());
        cartes.reconstruire();
    }

    private void reconstruire() {
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
        description.getStyleClass().add(STYLE_DESC);
        Node gps = construireBadgeGps(carte);
        Label passages = new Label(carte.nombrePassages() + " passage(s) rattaché(s)");
        passages.getStyleClass().add(STYLE_DESC);
        VBox boite = new VBox(code, description, gps, passages);
        carte.distanceProche()
                .ifPresent(distance -> boite.getChildren().add(etiquetteProximite(distance, carte.tropProche())));
        boite.getChildren().add(actionsPoint(carte));
        boite.getStyleClass().add("carte-point");
        return boite;
    }

    /// Étiquette « à … du point le plus proche » (#154). Passe en **alerte** (⚠, style dédié) quand la
    /// distance est sous le seuil de proximité, pour signaler des points anormalement rapprochés.
    private static Label etiquetteProximite(double metres, boolean tropProche) {
        Label proximite =
                new Label((tropProche ? "⚠ " : "") + "à " + distanceLisible(metres) + " du point le plus proche");
        proximite.getStyleClass().add(tropProche ? "carte-point-alerte" : STYLE_DESC);
        proximite.setWrapText(true);
        return proximite;
    }

    /// Distance lisible : mètres arrondis en deçà de 1 km, kilomètres à une décimale au-delà.
    private static String distanceLisible(double metres) {
        return metres >= 1000
                ? String.format(Locale.FRENCH, "%.1f km", metres / 1000)
                : String.format(Locale.FRENCH, "%.0f m", metres);
    }

    /// Badge GPS de la carte de point : un [Hyperlink] qui, quand les coordonnées sont présentes, ouvre
    /// **LA carte multi-sites centrée sur ce point** (#154) ; sinon un simple libellé « manquant ». On
    /// renvoie vers la carte de référence (qui montre déjà le fond OSM et permet de corriger la position
    /// en mode édition) plutôt que vers un OpenStreetMap externe.
    private Node construireBadgeGps(CartePoint carte) {
        PointDEcoute point = carte.point();
        if (!carte.gpsPresent()) {
            // Sans GPS : le point est affiché au centre de son carré sur LA carte de référence. Le lien y
            // mène, mode édition activé, pour le glisser à sa vraie position (comme un point géolocalisé).
            Hyperlink placer = new Hyperlink("⚠ GPS manquant — placer sur la carte");
            placer.getStyleClass().add("gps-manquant");
            placer.setOnAction(evenement -> ouvrirMultisite.ouvrirSurCarrePourPlacer(
                    viewModel.siteCourant().numeroCarre()));
            placer.setTooltip(new Tooltip("Ouvrir la carte multi-sites pour placer ce point (mode édition)"));
            return placer;
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
        // Gating destructif (#789) : un point qui porte des passages n'est pas supprimable (le service le
        // refuse). On grise le lien et on l'enrobe d'une enveloppe porteuse du tooltip d'explication, au lieu
        // de laisser l'utilisateur découvrir le refus après le clic. La carte est reconstruite à chaque
        // rafraîchissement, donc l'état de blocage est figé ici (texte fixe).
        supprimer.setDisable(carte.aDesPassages());
        Node actionSupprimer = IndicateurBlocage.enrober(
                supprimer,
                carte.aDesPassages()
                        ? "Suppression impossible : ce point porte des passages."
                                + " Supprimez d'abord les passages rattachés."
                        : "Supprimer ce point d'écoute.");
        HBox actions = new HBox(editer, actionSupprimer);
        actions.getStyleClass().add("carte-point-actions");
        return actions;
    }

    private void supprimerPoint(CartePoint carte) {
        if (carte.aDesPassages()) {
            alerteErreur("Le point « " + carte.point().code() + " » porte des passages : suppression bloquée.");
            return;
        }
        if (confirmateur.confirmer("Supprimer le point « " + carte.point().code() + " » ?")) {
            viewModel.supprimerPoint(carte.point());
        }
    }

    private Window fenetre() {
        return cartesPoints.getScene().getWindow();
    }

    /// Le point porte des passages : la suppression n'aura pas lieu, et l'utilisateur sait pourquoi.
    private void alerteErreur(String message) {
        notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Action impossible", message);
    }

    private static String libelleDescription(PointDEcoute point) {
        return point.description() == null ? "(pas de description)" : point.description();
    }
}

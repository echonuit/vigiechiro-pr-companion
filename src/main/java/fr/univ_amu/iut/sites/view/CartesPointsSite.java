package fr.univ_amu.iut.sites.view;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.IconesSeverite;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.NotificateurModifiable;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.PublicationPoint;
import fr.univ_amu.iut.sites.viewmodel.CartePoint;
import fr.univ_amu.iut.sites.viewmodel.SiteDetailViewModel;
import java.util.Locale;
import java.util.Optional;
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
import org.kordamp.ikonli.javafx.FontIcon;

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

    /// Publier appelle le **réseau** : le travail part hors du fil JavaFX, sans quoi le clic ne ferait
    /// rien pendant plusieurs secondes (#1014). Synchrone en test, donc déterministe.
    private final ExecuteurTache executeur;

    private CartesPointsSite(
            FlowPane cartesPoints,
            SiteDetailViewModel viewModel,
            NavigationSites navigation,
            OuvrirMultisite ouvrirMultisite,
            ConfirmateurModifiable confirmateur,
            NotificateurModifiable notificateur,
            ExecuteurTache executeur) {
        this.cartesPoints = cartesPoints;
        this.viewModel = viewModel;
        this.navigation = navigation;
        this.ouvrirMultisite = ouvrirMultisite;
        this.confirmateur = confirmateur;
        this.notificateur = notificateur;
        this.executeur = executeur;
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
            NotificateurModifiable notificateur,
            ExecuteurTache executeur) {
        var aucunPoint = Bindings.isEmpty(cartesPoints.getChildren());
        lblAucunPoint.visibleProperty().bind(aucunPoint);
        lblAucunPoint.managedProperty().bind(aucunPoint);
        CartesPointsSite cartes = new CartesPointsSite(
                cartesPoints, viewModel, navigation, ouvrirMultisite, confirmateur, notificateur, executeur);
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

    /// Étiquette « à … du point le plus proche » (#154). Passe en **alerte** quand la distance est sous le
    /// seuil de proximité, pour signaler des points anormalement rapprochés.
    ///
    /// L'alerte est une **icône** (#2221), plus un « ⚠ » écrit dans le texte : la sévérité se pose, elle ne
    /// s'écrit pas ([IconesSeverite]), et la couleur de l'icône vient de la même classe que le texte.
    private static Label etiquetteProximite(double metres, boolean tropProche) {
        Label proximite = new Label(libelleProximite(metres, tropProche));
        proximite.getStyleClass().add(tropProche ? "carte-point-alerte" : STYLE_DESC);
        if (tropProche) {
            proximite.setGraphic(IconesSeverite.icone(Severite.AVERTISSEMENT, "carte-point-alerte"));
        }
        proximite.setWrapText(true);
        return proximite;
    }

    /// Texte de l'étiquette de proximité (#1379).
    ///
    /// ## Pourquoi l'alerte porte une phrase et pas seulement un chiffre
    ///
    /// « à 120 m du point le plus proche » avec une icône d'avertissement **signale** sans **expliquer** :
    /// l'utilisateur voit qu'on lui reproche quelque chose, sans savoir quoi ni s'il doit agir. C'est le
    /// même défaut que le « ⚠ » écrit dans le texte que l'icône a remplacé (#2221), sous une forme plus
    /// polie.
    ///
    /// La règle existait, mais seulement dans le code : [CartePoint#SEUIL_PROXIMITE_METRES] est un
    /// « garde-fou de protocole », et [CartePoint#tropProche] documente la double cause - des points
    /// réellement trop rapprochés, **ou** une coordonnée saisie de travers. C'est cette phrase-là qu'il
    /// fallait porter à l'écran.
    ///
    /// Le cas neutre reste nu : accrocher la règle au cas nominal en ferait du bruit permanent, et
    /// l'alerte cesserait de se distinguer.
    static String libelleProximite(double metres, boolean tropProche) {
        String base = "à " + distanceLisible(metres) + " du point le plus proche";
        if (!tropProche) {
            return base;
        }
        return base + " : trop rapprochés pour le protocole, vérifiez la position ou la saisie GPS.";
    }

    /// L'écart à un homonyme distant, dit sans prétendre le mesurer quand on ne le peut pas (#3458).
    ///
    /// `NaN` signale une géométrie distante illisible : annoncer « à 0 m » y serait un mensonge
    /// rassurant, et c'est précisément le sens où il ne faut pas se tromper.
    private static String ecartLisible(double metres) {
        return Double.isNaN(metres)
                ? "à une position que Companion n'a pas su lire"
                : "à " + distanceLisible(metres) + " de celui-ci";
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
            Hyperlink placer = new Hyperlink("GPS manquant : placer sur la carte");
            placer.getStyleClass().add("gps-manquant");
            placer.setGraphic(IconesSeverite.icone(Severite.AVERTISSEMENT, "gps-manquant"));
            placer.setOnAction(evenement -> ouvrirMultisite.ouvrirSurCarrePourPlacer(
                    viewModel.siteCourant().numeroCarre()));
            placer.setTooltip(new Tooltip("Ouvrir la carte multi-sites pour placer ce point (mode édition)"));
            return placer;
        }
        Hyperlink lien = new Hyperlink("GPS : voir sur la carte");
        lien.getStyleClass().add("gps-ok");
        lien.setGraphic(IconesSeverite.icone(Severite.SUCCES, "gps-ok"));
        lien.setOnAction(evenement -> ouvrirMultisite.ouvrirSurPoint(
                viewModel.siteCourant().numeroCarre(), point.latitude(), point.longitude()));
        lien.setTooltip(
                new Tooltip("Voir " + point.latitude() + ", " + point.longitude() + " sur la carte multi-sites"));
        return lien;
    }

    private HBox actionsPoint(CartePoint carte) {
        Hyperlink editer = new Hyperlink("Modifier");
        editer.setGraphic(new FontIcon("fas-pen"));
        editer.setOnAction(evenement -> navigation.ouvrirModaleEditionPoint(
                fenetre(), viewModel.siteCourant(), carte.point(), viewModel::rafraichir));
        Hyperlink supprimer = new Hyperlink("Supprimer");
        supprimer.setGraphic(new FontIcon("fas-trash"));
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
        actionPublier(carte).ifPresent(actions.getChildren()::add);
        actions.getStyleClass().add("carte-point-actions");
        return actions;
    }

    /// Action **« Publier sur Vigie-Chiro »** (#3458), ou l'état qui la remplace.
    ///
    /// Trois cas la font disparaître plutôt que griser : la publication n'est pas installée (injecteur
    /// sans connexion), le point **vient de** la plateforme (l'y renvoyer n'a pas de sens), ou il y a
    /// **déjà été poussé** - et c'est alors un état qui s'affiche, pas une action.
    ///
    /// ⚠️ **Le carré verrouillé n'est pas grisé**, alors que c'est lui qui refusera son propriétaire.
    /// `PUT /sites/{id}/localites` accepte un participant validé sur le protocole même verrouillé, et
    /// refuse le propriétaire dès qu'il l'est ; les liens de site venant de `GET /moi/participations`
    /// (#718), Companion ne sait pas dans quel cas il se trouve. Le refus est donc **rendu compte**,
    /// avec son geste, plutôt que deviné.
    private Optional<Node> actionPublier(CartePoint carte) {
        if (!viewModel.publicationInstallee() || carte.venuDeLaPlateforme()) {
            return Optional.empty();
        }
        if (carte.publie()) {
            return Optional.of(etiquettePubliee());
        }
        Hyperlink publier = new Hyperlink("Publier sur Vigie-Chiro");
        publier.setGraphic(new FontIcon("fas-cloud-upload-alt"));
        Optional<String> empechement = viewModel.empechementPublication(carte);
        publier.setDisable(empechement.isPresent());
        publier.setOnAction(evenement -> publierPoint(carte, publier));
        return Optional.of(IndicateurBlocage.enrober(
                publier, empechement.orElse("Ajouter ce point aux localités du carré sur Vigie-Chiro.")));
    }

    /// État « déjà en ligne » : un libellé, pas un lien. Le geste n'a plus lieu d'être, et le proposer
    /// encore ferait cliquer pour apprendre qu'il n'y a rien à faire.
    private static Label etiquettePubliee() {
        Label publie = new Label("Publié sur Vigie-Chiro");
        publie.getStyleClass().add(STYLE_DESC);
        publie.setGraphic(IconesSeverite.icone(Severite.SUCCES, STYLE_DESC));
        publie.setTooltip(new Tooltip("Ce point a été ajouté aux localités du carré depuis Companion."));
        return publie;
    }

    /// Lance la publication **hors du fil JavaFX**, puis rend compte et recharge la fiche.
    ///
    /// Le lien se désactive pendant l'appel : sans cela, deux clics rapides enverraient deux fois le
    /// même point. Il n'est réarmé qu'en cas d'échec technique ; dans tous les autres cas la fiche est
    /// reconstruite, et la carte avec.
    private void publierPoint(CartePoint carte, Hyperlink lien) {
        lien.setDisable(true);
        executeur.executer(
                () -> viewModel.publier(carte),
                resultat -> {
                    rendreCompte(carte, resultat);
                    viewModel.rafraichir();
                },
                erreur -> {
                    lien.setDisable(false);
                    alerteErreur("La publication du point « " + carte.point().code() + " » a échoué : "
                            + erreur.getMessage());
                });
    }

    /// Rend compte des **quatre** issues possibles. Un refus porte son `geste` et pas seulement sa
    /// cause (ADR 2635) : « accès refusé » n'apprend rien à qui doit agir.
    private void rendreCompte(CartePoint carte, PublicationPoint.Resultat resultat) {
        String code = carte.point().code();
        switch (resultat) {
            case PublicationPoint.Resultat.Publie ignore ->
                notificateur.notifier(
                        NiveauNotification.INFORMATION,
                        "Point publié",
                        "Le point « " + code + " » a été ajouté aux localités du carré sur Vigie-Chiro.");
            case PublicationPoint.Resultat.DejaPresent(String nom) ->
                notificateur.notifier(
                        NiveauNotification.INFORMATION,
                        "Déjà sur Vigie-Chiro",
                        "Une localité « " + nom + " » existe déjà sur ce carré : rien n'a été envoyé."
                                + " Le point est désormais suivi comme publié.");
            case PublicationPoint.Resultat.AilleursSurLaPlateforme(String nom, double distance) ->
                notificateur.notifier(
                        NiveauNotification.AVERTISSEMENT,
                        "Un point « " + nom + " » existe déjà, ailleurs",
                        "Vigie-Chiro connaît un point « " + nom + " » " + ecartLisible(distance)
                                + ". Rien n'a été envoyé : déplacer le point de la plateforme"
                                + " déplacerait toutes les nuits qui s'y rattachent, y compris celles"
                                + " d'autres observateurs. Donnez un autre code à votre point, ou"
                                + " alignez sa position sur celle de la plateforme.");
            case PublicationPoint.Resultat.ModifieEntreTemps ignore ->
                notificateur.notifier(
                        NiveauNotification.AVERTISSEMENT,
                        "Le carré a changé entre-temps",
                        "Quelqu'un a modifié les points de ce carré pendant l'envoi. Rien n'a été"
                                + " modifié sur Vigie-Chiro : synchronisez, puis réessayez.");
            case PublicationPoint.Resultat.Refuse(String cause, String geste) ->
                notificateur.notifier(NiveauNotification.AVERTISSEMENT, cause, geste);
        }
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

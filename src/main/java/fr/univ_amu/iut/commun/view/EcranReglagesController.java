package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.kordamp.ikonli.javafx.FontIcon;

/// Controller de l'écran « Réglages » (#927) : peuple le `TabPane` d'un **onglet par feature
/// contributrice**, à la façon dont [MainController] bâtit les cartes d'accueil depuis
/// `Set<ActiviteAccueil>`. Il injecte le point d'extension `Set<OngletReglages>` (déclaré par
/// [fr.univ_amu.iut.commun.di.CommunModule], vide tant qu'aucune feature ne contribue) et délègue la
/// construction des contrôles à [ControleursReglages], en les câblant à [ReglagesReactifs].
///
/// Nommé `Ecran…` (et non `Reglages…`) pour ne pas entrer en collision avec le service
/// [fr.univ_amu.iut.commun.model.Reglages].
public class EcranReglagesController {

    @FXML
    private TabPane onglets;

    private final Set<OngletReglages> contributions;
    private final ReglagesReactifs reactifs;

    @Inject
    public EcranReglagesController(Set<OngletReglages> contributions, ReglagesReactifs reactifs) {
        this.contributions = Objects.requireNonNull(contributions, "contributions");
        this.reactifs = Objects.requireNonNull(reactifs, "reactifs");
    }

    @FXML
    private void initialize() {
        contributions.stream()
                .filter(EcranReglagesController::estAffichable)
                .sorted(Comparator.comparingInt(OngletReglages::ordre))
                .map(this::construireOnglet)
                .forEach(onglets.getTabs()::add);
    }

    /// Un onglet n'est affiché que s'il a **quelque chose à montrer** : au moins un réglage déclaratif,
    /// ou un formulaire personnalisé (échappatoire). Partagé avec [NavigationReglages] pour décider si
    /// l'entrée du menu ☰ est active.
    static boolean estAffichable(OngletReglages onglet) {
        return onglet instanceof OngletReglagesPersonnalise
                || !onglet.reglages().isEmpty();
    }

    private Tab construireOnglet(OngletReglages onglet) {
        Tab tab = new Tab(onglet.titre());
        if (!onglet.iconeLiteral().isBlank()) {
            tab.setGraphic(new FontIcon(onglet.iconeLiteral()));
        }
        // Défilement vertical : une feature peut déclarer plus de réglages que la hauteur disponible.
        ScrollPane defilement = new ScrollPane(ControleursReglages.formulaire(onglet, reactifs));
        defilement.setFitToWidth(true);
        defilement.getStyleClass().add("onglet-reglages-defilement");
        tab.setContent(defilement);
        return tab;
    }
}

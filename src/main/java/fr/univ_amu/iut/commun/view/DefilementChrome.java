package fr.univ_amu.iut.commun.view;

import com.google.inject.Singleton;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

/// Amener une zone d'écran **dans le champ** (#1486), sur le défilement du chrome.
///
/// ## Pourquoi ce port existe
///
/// Le défilement n'appartient pas aux écrans : c'est le **ScrollPane central** du chrome, tenu par
/// [MainController]. Une feature qui voudrait révéler une de ses zones devrait remonter la scène
/// jusqu'à lui - donc le connaître, donc en dépendre.
///
/// Troisième de la famille `*Chrome` après [OccupationChrome] (le voile) et [AnnonceChrome] (le
/// bandeau), et même patron : singleton du socle, **installé** par le chrome au démarrage, **consommé**
/// par injection, et **sans effet** quand le chrome est absent - injecteurs partiels des outils de
/// capture et de la ligne de commande, qui ne montent pas de `MainView`.
///
/// ## Ce qu'il ne fait pas
///
/// Il ne révèle **rien** si le contenu tient déjà dans le champ, ni si la zone y est déjà. Un défilement
/// qui s'agite pour rien est plus déroutant qu'un défilement absent.
@Singleton
public class DefilementChrome {

    private ScrollPane defilement;

    /// Installe le défilement du chrome. Appelé une fois par [MainController] au démarrage.
    public void installer(ScrollPane defilement) {
        this.defilement = defilement;
    }

    /// Amène `cible` dans le champ, si besoin.
    ///
    /// Le calcul est différé d'un tour de boucle : une zone qu'on vient de rendre visible n'a pas encore
    /// de position, et la révéler tout de suite reviendrait à viser un nœud de hauteur nulle. C'est le
    /// cas courant de l'appelant - on révèle ce qui **vient d'apparaître**.
    public void revele(Node cible) {
        if (defilement == null || cible == null) {
            return;
        }
        Platform.runLater(() -> amener(cible));
    }

    private void amener(Node cible) {
        Node contenu = defilement.getContent();
        if (contenu == null || cible.getScene() == null) {
            return;
        }
        double hauteurContenu = contenu.getBoundsInLocal().getHeight();
        double hauteurVisible = defilement.getViewportBounds().getHeight();
        double course = hauteurContenu - hauteurVisible;
        if (course <= 0) {
            // Tout tient à l'écran : il n'y a rien à révéler.
            return;
        }
        Bounds dansLeContenu = contenu.sceneToLocal(cible.localToScene(cible.getBoundsInLocal()));
        double hautDeLaCible = dansLeContenu.getMinY();
        double hautDuChamp = course * defilement.getVvalue();
        if (hautDeLaCible >= hautDuChamp && dansLeContenu.getMaxY() <= hautDuChamp + hauteurVisible) {
            // Déjà dans le champ, en entier.
            return;
        }
        defilement.setVvalue(Math.clamp(hautDeLaCible / course, 0.0, 1.0));
    }
}

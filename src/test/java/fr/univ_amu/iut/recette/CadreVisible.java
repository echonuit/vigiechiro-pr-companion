package fr.univ_amu.iut.recette;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import org.testfx.api.FxRobot;
import org.testfx.util.WaitForAsyncUtils;

/// Ce qu'un clip de recette **montre réellement**, et comment l'y amener.
///
/// ## Pourquoi cette classe existe
///
/// Un scénario perceptif existe pour qu'un humain juge ce qu'il voit. Or `lookup` trouve un noeud
/// **quelle que soit sa position** : un cas peut passer toutes ses assertions et publier un clip qui
/// ne montre pas son objet. Vécu deux fois sur `S4-33` (#4126, #4128) - le compte rendu était sous la
/// ligne de flottaison, puis derrière la barre de statut, et tout était vert.
///
/// ## Les deux pièges, mesurés
///
/// ⚠️ **La scène n'est pas le cadre.** Comparer aux bornes de la scène laisse passer un noeud caché
/// derrière la barre de statut : à y = 870 dans une scène de 900, « maxY <= hauteur » est vrai et le
/// noeud est invisible. La référence est la zone d'affichage du [ScrollPane] qui le porte.
///
/// ⚠️ **La molette ne suffit pas.** `robot.scroll` n'a pas déplacé le contenu d'un pixel quand le
/// pointeur n'était pas au-dessus du bon panneau. Le défilement se pilote.
public final class CadreVisible {

    /// Marge au-dessus et au-dessous de ce qu'on vient lire : une phrase collée au bord se lit mal, et
    /// c'est la lisibilité que ces cas font juger.
    private static final double AIR_DE_LECTURE = 24;

    /// Nombre de pas de défilement avant d'abandonner : borné pour qu'un noeud inatteignable rende une
    /// erreur qui le nomme, au lieu de tourner.
    private static final int PAS_MAX = 24;

    private static final double PAS = 0.08;

    private CadreVisible() {}

    /// `noeud` est-il réellement à l'image, avec de quoi le lire ?
    public static boolean contient(Node noeud) {
        Bounds cible = noeud.localToScene(noeud.getBoundsInLocal());
        ScrollPane cadre = cadreDefilant(noeud);
        if (cadre == null) {
            return cible.getMinY() >= 0 && cible.getMaxY() <= noeud.getScene().getHeight();
        }
        Bounds vue = cadre.localToScene(cadre.getBoundsInLocal());
        return cible.getMinY() >= vue.getMinY() + AIR_DE_LECTURE && cible.getMaxY() <= vue.getMaxY() - AIR_DE_LECTURE;
    }

    /// Fait défiler jusqu'à ce que `cible` entre dans le cadre, ou échoue en le nommant.
    ///
    /// ⚠️ Viser la ZONE qui contient ce qu'on veut voir ne suffit pas : amener son bord haut dans le
    /// cadre laisse son contenu sous le pli. On vise le noeud qu'on veut lire.
    public static void amener(Node cible, FxRobot robot) {
        ScrollPane cadre = cadreDefilant(cible);
        if (cadre == null) {
            if (contient(cible)) {
                return;
            }
            throw new AssertionError("aucun ScrollPane au-dessus de la cible, et elle est hors du cadre");
        }
        for (int essai = 0; essai < PAS_MAX && !contient(cible); essai++) {
            double avant = cadre.getVvalue();
            robot.interact(() -> cadre.setVvalue(Math.min(1.0, cadre.getVvalue() + PAS)));
            WaitForAsyncUtils.waitForFxEvents();
            if (cadre.getVvalue() == avant) {
                break;
            }
        }
        if (!contient(cible)) {
            throw new AssertionError(
                    "la cible reste hors du cadre après défilement : " + cible.localToScene(cible.getBoundsInLocal()));
        }
    }

    /// Le premier [ScrollPane] au-dessus de `noeud`, ou `null` s'il n'y en a pas.
    private static ScrollPane cadreDefilant(Node noeud) {
        for (Node parent = noeud.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ScrollPane cadre) {
                return cadre;
            }
        }
        return null;
    }
}

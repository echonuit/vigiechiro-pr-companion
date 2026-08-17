package fr.univ_amu.iut.e2e;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Window;
import org.testfx.api.FxRobot;
import org.testfx.service.query.NodeQuery;
import org.testfx.util.NodeQueryUtils;
import org.testfx.util.WaitForAsyncUtils;

/// Attendre qu'un libellé soit **cliquable**, et **dire ce qu'on a vu** si l'attente expire (#3911).
///
/// ## Pourquoi cette classe existe
///
/// Un `WaitForAsyncUtils.waitFor(...)` qui expire jette une `TimeoutException` **nue** : le rapport
/// donne le numéro de ligne, rien d'autre. Sur un défaut qui ne se produit qu'en CI, cela laisse
/// l'enquête sans matière - et [ADR
/// 2213](../../../../../../dev-docs/decisions/2213-un-dispositif-rapporte-avant-de-conclure.md)
/// dit l'inverse :
///
/// > Un dispositif qui ne peut pas conclure ne conclut pas : il **rapporte ce qu'il a vu**.
///
/// Vécu trois fois en deux jours, sur trois écrans différents et sur des PR sans rapport - dont deux
/// ne contenaient **aucun code**. Chaque occurrence a coûté une relance de dix minutes et une enquête
/// qui a recommencé de zéro, faute de trace.
///
/// ## Ce qu'elle change, et ce qu'elle ne change pas
///
/// Elle **ne corrige pas** l'instabilité : le butoir est le même, l'attente est la même. Elle rend la
/// prochaine occurrence **diagnosticable** : le message nomme le nombre de nœuds portant le libellé,
/// leurs bornes dans la scène, leur drapeau `visible`, la taille de la scène et l'état de leur
/// fenêtre. Il dira donc **laquelle** de ces conditions manquait.
///
/// ⚠️ Augmenter le butoir serait le remède évident et le mauvais :
/// [ADR 3668](../../../../../../dev-docs/decisions/3668-un-e2e-attend-le-signal-du-callback-pas-le-retour-du-geste.md)
/// demande au contraire qu'un délai surdimensionné continue d'échouer proprement plutôt que de masquer
/// une course.
final class AttenteAvantClic {

    private AttenteAvantClic() {}

    /// Attend que `libelle` satisfasse le prédicat **exact** du clic - `NodeQueryUtils.isVisible()`,
    /// qui exige en plus du drapeau local que le nœud intersecte le rectangle de la scène (#3875).
    ///
    /// @throws AssertionError à l'expiration, avec l'état observé
    static void attendreCliquable(FxRobot robot, String libelle, int secondes) {
        try {
            WaitForAsyncUtils.waitFor(
                    secondes,
                    TimeUnit.SECONDS,
                    () -> cliquable(robot, libelle).tryQuery().isPresent());
        } catch (TimeoutException expiration) {
            throw new AssertionError(rapport(robot, libelle, secondes), expiration);
        }
    }

    private static NodeQuery cliquable(FxRobot robot, String libelle) {
        return robot.lookup(libelle).match(NodeQueryUtils.isVisible());
    }

    /// L'état au moment de l'expiration, lu **sur le fil JavaFX** : des bornes lues depuis le fil de
    /// test peuvent être en cours de recalcul, et un rapport qui ment est pire que pas de rapport.
    private static String rapport(FxRobot robot, String libelle, int secondes) {
        AtomicReference<String> vu = new AtomicReference<>("(état illisible)");
        try {
            robot.interact(() -> vu.set(decrire(robot, libelle)));
        } catch (RuntimeException echec) {
            vu.set("(état illisible : " + echec + ")");
        }
        return "« " + libelle + " » n'est pas devenu cliquable en " + secondes + " s.\n" + vu.get()
                + "\nRappel : le prédicat exige le drapeau `visible` ET l'intersection avec le rectangle"
                + " de la scène. Ne PAS augmenter le butoir (ADR 3668) : chercher pourquoi la condition"
                + " manquante manquait.";
    }

    private static String decrire(FxRobot robot, String libelle) {
        List<Node> portentLeLibelle = List.copyOf(robot.lookup(libelle).queryAll());
        if (portentLeLibelle.isEmpty()) {
            return "Aucun nœud ne porte ce libellé : l'écran n'a pas été bâti, ou le libellé a changé.";
        }
        StringBuilder texte = new StringBuilder(portentLeLibelle.size() + " nœud(s) portent ce libellé :");
        for (Node noeud : portentLeLibelle) {
            texte.append("\n  - ")
                    .append(noeud.getClass().getSimpleName())
                    .append(" visible=")
                    .append(noeud.isVisible());
            Scene scene = noeud.getScene();
            if (scene == null) {
                texte.append(" — DANS AUCUNE SCÈNE (le nœud a été détaché)");
                continue;
            }
            Bounds dansLaScene = noeud.localToScene(noeud.getBoundsInLocal());
            texte.append(String.format(
                    " bornes y=%.0f..%.0f x=%.0f..%.0f | scène %.0fx%.0f",
                    dansLaScene.getMinY(),
                    dansLaScene.getMaxY(),
                    dansLaScene.getMinX(),
                    dansLaScene.getMaxX(),
                    scene.getWidth(),
                    scene.getHeight()));
            if (dansLaScene.getMaxY() > scene.getHeight() || dansLaScene.getMaxX() > scene.getWidth()) {
                texte.append(" — HORS CADRE : il déborde de la scène, donc il n'est pas cliquable");
            }
            Window fenetre = scene.getWindow();
            texte.append(" | fenêtre ").append(fenetre == null ? "absente" : "affichée=" + fenetre.isShowing());
        }
        return texte.toString();
    }
}

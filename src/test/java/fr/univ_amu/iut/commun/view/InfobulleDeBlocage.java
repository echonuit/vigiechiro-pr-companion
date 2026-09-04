package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.recette.Attente;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import org.testfx.api.FxRobot;
import org.testfx.util.WaitForAsyncUtils;

/// Relit l'infobulle posée par [IndicateurBlocage#expliquer] sur une enveloppe, pour que les tests
/// puissent vérifier **ce qu'elle dit** et pas seulement qu'elle existe.
///
/// `Tooltip.install(Node, Tooltip)` ne range pas l'infobulle dans une propriété publique du nœud
/// (`getTooltip()` n'existe que sur `Control`) : elle vit dans la table de propriétés du nœud, sous une
/// clé interne à JavaFX. On la relit donc par cette clé, faute de mieux.
///
/// Cette dépendance à un détail d'implémentation est **assumée et localisée ici** : si une version de
/// JavaFX change la clé, un seul fichier échoue, avec un message qui dit quoi corriger. L'alternative -
/// ne vérifier que la présence de l'infobulle - laisserait passer exactement le défaut que #1970
/// corrige : un motif de blocage qui existe mais que personne ne lit.
public final class InfobulleDeBlocage {

    /// Clé interne utilisée par `Tooltip.install` (cf. `Tooltip.TOOLTIP_PROP_KEY`).
    private static final String CLE = "javafx.scene.control.Tooltip";

    /// De quoi couvrir le délai d'apparition de JavaFX (une seconde par défaut), sans transformer un
    /// défaut en attente interminable.
    private static final int DELAI_INFOBULLE_S = 5;

    /// Combien de temps l'infobulle reste ouverte quand on la montre pour un clip : de quoi couvrir
    /// l'arrêt que le scénario prend, sans la laisser traîner sur les gestes suivants.
    private static final int TENUE_INFOBULLE_S = 6;

    private InfobulleDeBlocage() {}

    /// Texte de l'infobulle installée sur `enveloppe`.
    ///
    /// @throws AssertionError si aucune infobulle n'y est posée - un blocage sans motif est le défaut
    ///     que ces tests cherchent
    public static String texteDe(Node enveloppe) {
        Object installee = enveloppe.getProperties().get(CLE);
        if (!(installee instanceof Tooltip infobulle)) {
            throw new AssertionError("Aucune infobulle sur " + enveloppe.getId()
                    + " : un contrôle grisé sans motif ne dit pas à l'utilisateur ce qu'il doit corriger."
                    + " Si JavaFX a changé sa clé interne, corriger InfobulleDeBlocage.CLE.");
        }
        return infobulle.getText();
    }

    /// **Fait paraître** l'infobulle de `enveloppe` en la survolant, et rend son texte.
    ///
    /// [#texteDe] lit le motif **par programme** : un clip qui s'en contente montre un bouton gris
    /// et n'explique rien. Or ces cas-là existent pour montrer que le blocage **dit ce qui manque** -
    /// la moitié qui compte reste alors hors de l'image. Constaté en ouvrant les images de S1-16 et
    /// S1-33 : le geste fermé était net, son motif introuvable.
    ///
    /// Le survol est le geste réel, et l'attente est une **assertion** : si l'infobulle ne paraît pas,
    /// le test échoue au lieu de filmer un écran muet.
    ///
    /// @throws TimeoutException si l'infobulle ne paraît pas - un motif qu'on ne peut pas faire venir
    ///     n'existe pas pour l'utilisateur
    public static String montrerEtLire(Node enveloppe, FxRobot robot) throws TimeoutException {
        Object installee = enveloppe.getProperties().get(CLE);
        if (!(installee instanceof Tooltip infobulle)) {
            throw new AssertionError("Aucune infobulle sur " + enveloppe.getId());
        }
        robot.moveTo(enveloppe);
        WaitForAsyncUtils.waitForFxEvents();
        Attente.que(infobulle::isShowing, "l'infobulle de blocage paraît sous le pointeur", DELAI_INFOBULLE_S * 1000L);
        return infobulle.getText();
    }

    /// Fait paraître l'infobulle d'une cible que le pointeur n'atteint pas, et rend son texte (#5205).
    ///
    /// [#montrerEtLire] déplace la souris : ça marche sur un bouton, pas sur un symbole de graphe de
    /// dix pixels, où `isHover()` reste **faux** après `moveTo`. On poste donc l'entrée de souris que
    /// le gestionnaire d'infobulle attend, délai d'apparition à zéro sur cette infobulle-là.
    ///
    /// **Attention** : ceci prouve que l'infobulle FONCTIONNE et qu'on peut la montrer, jamais qu'un
    /// vrai pointeur atteint la cible sans viser. C'est une question de recette, et sa case est `S2-79`.
    public static String montrerParEntreeDeSouris(Node cible, FxRobot robot) throws TimeoutException {
        Object installee = cible.getProperties().get(CLE);
        if (!(installee instanceof Tooltip infobulle)) {
            // Le message de [#texteDe] parle d'un contrôle grisé : il ne vaut pas ici, et un symbole de
            // graphe n'a pas d'identifiant à citer. On nomme donc la CLASSE du nœud, seule chose qui
            // distingue un point d'une courbe pour qui lit le refus.
            throw new AssertionError(
                    "Aucune infobulle sur ce " + cible.getClass().getSimpleName()
                            + " : la cible ne dit alors rien de ce qu'elle vaut, et rien ne remplace ce qu'elle"
                            + " aurait dit.");
        }
        robot.interact(() -> {
            infobulle.setShowDelay(Duration.ZERO);
            // Assez longtemps pour qu'un clip la MONTRE : la durée par défaut ne se règle pas sur
            // l'arrêt que le scénario prend, et la bulle s'éteignait au bout de six images sur les
            // vingt-cinq attendues.
            infobulle.setShowDuration(Duration.seconds(TENUE_INFOBULLE_S));
            Bounds ecran = cible.localToScreen(cible.getBoundsInLocal());
            Bounds scene = cible.localToScene(cible.getBoundsInLocal());
            for (EventType<MouseEvent> type : List.of(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_MOVED)) {
                Event.fireEvent(cible, souris(type, scene, ecran));
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        Attente.que(
                infobulle::isShowing,
                "l'infobulle paraît sur une cible que le pointeur n'atteint pas",
                DELAI_INFOBULLE_S * 1000L);
        return infobulle.getText();
    }

    /// Un évènement de souris minimal, aux coordonnées de SCÈNE **et** d'écran.
    ///
    /// Les deux comptent, pour des lecteurs différents : l'infobulle se place d'après l'écran, le suivi
    /// de gestes du banc dessine le pointeur d'après la SCÈNE. Un premier jet passait `0, 0` en scène :
    /// la bulle paraissait au bon endroit et le pointeur restait dessiné dans le coin, si bien que le
    /// clip montrait une infobulle qui s'ouvre toute seule.
    private static MouseEvent souris(EventType<MouseEvent> type, Bounds scene, Bounds ecran) {
        return new MouseEvent(
                type,
                scene.getMinX() + scene.getWidth() / 2,
                scene.getMinY() + scene.getHeight() / 2,
                ecran.getMinX() + ecran.getWidth() / 2,
                ecran.getMinY() + ecran.getHeight() / 2,
                MouseButton.NONE,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null);
    }
}

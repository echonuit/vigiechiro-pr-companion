package fr.univ_amu.iut.recette;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.application.Platform;
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
/// ⚠️ **Et c'est cette référence qui fait tout le travail, pas une marge.** La première version ajoutait
/// vingt-quatre pixels d'air de chaque côté, au nom de la lisibilité. Deux défauts en sont sortis, aux
/// deux bouts : le **premier** élément d'une liste ne peut rien avoir au-dessus de lui quand le
/// défilement est en butée haute, et le **dernier** rien en dessous quand il est en butée basse.
/// `amener` tournait jusqu'à sa borne et échouait sur des noeuds parfaitement visibles (#4149).
///
/// La marge était de toute façon redondante : mesuré sur la fiche d'un site, la zone d'affichage fait
/// **805 px sur une scène de 900** - elle exclut déjà le chrome et la barre de statut. Ce qui corrigeait
/// #4128 était de comparer au **viewport** ; l'air en plus n'ajoutait qu'un faux négatif.
///
/// ⚠️ **La molette ne suffit pas.** `robot.scroll` n'a pas déplacé le contenu d'un pixel quand le
/// pointeur n'était pas au-dessus du bon panneau. Le défilement se pilote.
public final class CadreVisible {

    /// Nombre de pas de défilement avant d'abandonner : borné pour qu'un noeud inatteignable rende une
    /// erreur qui le nomme, au lieu de tourner.
    private static final int PAS_MAX = 24;

    private static final double PAS = 0.08;

    private CadreVisible() {}

    /// `noeud` est-il réellement à l'image, avec de quoi le lire ?
    public static boolean contient(Node noeud) {
        return surLeFilFx(() -> {
            Bounds cible = noeud.localToScene(noeud.getBoundsInLocal());
            ScrollPane cadre = cadreDefilant(noeud);
            if (cadre == null) {
                return cible.getMinY() >= 0
                        && cible.getMaxY() <= noeud.getScene().getHeight();
            }
            Bounds vue = cadre.localToScene(cadre.getBoundsInLocal());
            return cible.getMinY() >= vue.getMinY() && cible.getMaxY() <= vue.getMaxY();
        });
    }

    /// Lit le graphe de scène **sur le fil de JavaFX**, et rend le résultat à l'appelant.
    ///
    /// ## Pourquoi une lecture a besoin d'un fil
    ///
    /// ⚠️ `getBoundsInLocal()` n'est pas un accesseur : quand les bornes sont **sales**, il déclenche
    /// leur recalcul - `Parent.recomputeBounds`, puis la mise en page du texte - sur le fil qui
    /// appelle. Lire depuis le fil du test fait donc travailler DEUX fils sur le même graphe.
    ///
    /// Ce que cela coûte, mesuré :
    ///
    /// - un verdict faux une fois sur cinq (#4200), quand les bornes lues sont à moitié calculées ;
    /// - et, plus rarement, la corruption du graphe lui-même (#4187) :
    ///   `ObservableListWrapper.get` avec un index de -1, `PrismTextLayout` dont le tableau de
    ///   segments est nul. **Ces deux-là ne peuvent pas se produire depuis le fil de JavaFX** : ce
    ///   sont des états que seule une écriture concurrente produit. La JVM reste ensuite abîmée pour
    ///   les tests suivants du même fork, ce qui explique les classes sans rapport tombées avec.
    ///
    /// `amener` prenait déjà soin de faire la MUTATION par `robot.interact`. C'est la lecture qui
    /// avait été jugée inoffensive, et elle ne l'est pas.
    private static <T> T surLeFilFx(Callable<T> lecture) {
        if (Platform.isFxApplicationThread()) {
            return appeler(lecture);
        }
        try {
            return WaitForAsyncUtils.asyncFx(lecture).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new AssertionError("lecture du cadre interrompue", interruption);
        } catch (ExecutionException | TimeoutException echec) {
            throw new AssertionError("le fil de JavaFX n'a pas rendu les bornes du cadre", echec);
        }
    }

    private static <T> T appeler(Callable<T> lecture) {
        try {
            return lecture.call();
        } catch (Exception echec) {
            throw new AssertionError("lecture du cadre impossible", echec);
        }
    }

    /// Fait défiler jusqu'à ce que `cible` entre dans le cadre, ou échoue en le nommant.
    ///
    /// ⚠️ Viser la ZONE qui contient ce qu'on veut voir ne suffit pas : amener son bord haut dans le
    /// cadre laisse son contenu sous le pli. On vise le noeud qu'on veut lire.
    public static void amener(Node cible, FxRobot robot) {
        ScrollPane cadre = surLeFilFx(() -> cadreDefilant(cible));
        if (cadre == null) {
            if (contient(cible)) {
                return;
            }
            throw new AssertionError("aucun ScrollPane au-dessus de la cible, et elle est hors du cadre");
        }
        for (int essai = 0; essai < PAS_MAX && !contient(cible); essai++) {
            double avant = surLeFilFx(cadre::getVvalue);
            robot.interact(() -> cadre.setVvalue(Math.min(1.0, cadre.getVvalue() + PAS)));
            WaitForAsyncUtils.waitForFxEvents();
            if (surLeFilFx(cadre::getVvalue) == avant) {
                break;
            }
        }
        if (!contient(cible)) {
            throw new AssertionError("la cible reste hors du cadre après défilement : "
                    + surLeFilFx(() -> cible.localToScene(cible.getBoundsInLocal())));
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

package fr.univ_amu.iut.recette;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.testfx.util.WaitForAsyncUtils;

/// **Attendre qu'une condition devienne vraie**, avant d'affirmer quoi que ce soit dessus.
///
/// Ce n'est pas une [Respiration], qui ne s'arrête **que si l'on filme** : entre un geste et une
/// assertion, celle-ci ne tient rien, et le banc réussit parce que le travail a le plus souvent fini
/// avant. Le défaut est coûteux parce qu'il a l'**air** de tenir quelque chose.
///
/// Elle **dit ce qu'elle attendait** en expirant : sans cela l'échec arrive plus tard, sur
/// l'assertion, qui accuse le code alors que c'est la mise en place qui n'a pas eu lieu. Patron de
/// #4504, où il vivait en privé dans `AppTest`. Elle ne remplace pas `waitForFxEvents`, qui vide la
/// file d'événements sans dire que le travail attendu a eu lieu : on fait les deux (#4694).
public final class Attente {

    /// Le délai par défaut, généreux : une attente qui rend la main dès que la condition est vraie ne
    /// coûte pas son délai, elle ne le paie qu'en cas d'échec, où il vaut mieux être patient qu'à
    /// nouveau intermittent.
    private static final long DELAI_MS = 5_000L;

    private Attente() {}

    /// Attend que `condition` devienne vraie, au plus [#DELAI_MS] millisecondes.
    ///
    /// @param condition relue jusqu'à devenir vraie
    /// @param ceQuOnAttend dit à la première personne du banc, et repris tel quel dans l'échec
    public static void que(BooleanSupplier condition, String ceQuOnAttend) {
        que(condition, ceQuOnAttend, DELAI_MS);
    }

    /// Attend que `condition` devienne vraie, en la lisant **sur le fil JavaFX**.
    ///
    /// `waitFor` rappelle le prédicat depuis le fil du test. Un prédicat qui touche le graphe de scène
    /// doit être lu sur le fil FX, qui n'est pas partageable : sinon il lit un graphe qu'un autre fil
    /// est en train d'écrire. Mesuré par #4408, dont le remède vivait en privé dans `ScenarioAccueilTest`,
    /// sur la seule carte de l'accueil qui charge hors du fil JavaFX avant d'afficher (#1214).
    public static void queSurLeFil(BooleanSupplier condition, String ceQuOnAttend) {
        queSurLeFil(condition, ceQuOnAttend, DELAI_MS);
    }

    /// La même, avec un délai choisi.
    public static void queSurLeFil(BooleanSupplier condition, String ceQuOnAttend, long delaiMs) {
        que(() -> lireSurLeFil(condition), ceQuOnAttend, delaiMs);
    }

    /// Exécute `action` **sur le fil JavaFX** et attend qu'elle rende, en disant ce qu'elle faisait
    /// si elle n'y arrive pas.
    ///
    /// Symétrique de [#queSurLeFil] : celle-ci **lit** une condition sur le fil, celle-là y **fait**
    /// quelque chose. `WaitForAsyncUtils.waitForAsyncFx` rend la même chose en levant une
    /// `TimeoutException` nue, et un journal de CI n'apprend alors ni ce qu'on construisait ni où le
    /// parcours s'est arrêté (#4997, même défaut que le `waitFor` nu de #4845).
    ///
    /// @param ceQueOnFaisait dit à la première personne du banc, et repris tel quel dans l'échec
    public static void surLeFil(Runnable action, String ceQueOnFaisait, long delaiMs) {
        surLeFil(
                () -> {
                    action.run();
                    return null;
                },
                ceQueOnFaisait,
                delaiMs);
    }

    /// La même, pour une action qui **rend** une valeur.
    public static <T> T surLeFil(Callable<T> action, String ceQueOnFaisait, long delaiMs) {
        try {
            return WaitForAsyncUtils.waitForAsyncFx(delaiMs, action);
        } catch (Throwable echec) {
            throw new AssertionError(
                    "n'a pas pu " + ceQueOnFaisait + " sur le fil JavaFX en " + delaiMs
                            + " ms. Ce n'est pas le code qui a tort tant que ceci n'a pas eu lieu.",
                    echec);
        }
    }

    /// La valeur du prédicat, évaluée sur le fil JavaFX et rapportée ici.
    private static boolean lireSurLeFil(BooleanSupplier condition) {
        try {
            return Boolean.TRUE.equals(
                    WaitForAsyncUtils.asyncFx(condition::getAsBoolean).get());
        } catch (InterruptedException interrompu) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException echec) {
            // Le predicat a leve SUR le fil FX. Le taire ferait expirer l'attente sur un delai, en
            // accusant la lenteur la ou il y a une exception : elle remonte donc telle quelle.
            throw new AssertionError("le prédicat a levé sur le fil JavaFX", echec.getCause());
        }
    }

    /// La même, avec un délai choisi. Utile aux bancs qui savent leur attente courte, et aux témoins
    /// de cette classe, qui ont besoin d'expirer vite.
    public static void que(BooleanSupplier condition, String ceQuOnAttend, long delaiMs) {
        que(condition, () -> ceQuOnAttend, delaiMs);
    }

    /// La même, dont le message se **construit à l'expiration** plutôt qu'avant d'attendre.
    ///
    /// Un message `String` est évalué avant l'attente : un banc qui veut dire **ce qu'il a observé**
    /// rapporterait alors la valeur d'avant, en faisant croire que rien n'a bougé. `AppTest` tenait
    /// cette particularité en privé, et disait la hauteur atteinte, sans quoi deux causes distinctes
    /// rendent le même chiffre et le journal de CI ne permet pas de trancher (#4504, #4847).
    ///
    /// @param ceQuOnAttend appelé **seulement** si l'attente expire
    public static void que(BooleanSupplier condition, Supplier<String> ceQuOnAttend, long delaiMs) {
        try {
            WaitForAsyncUtils.waitFor(delaiMs, TimeUnit.MILLISECONDS, condition::getAsBoolean);
        } catch (TimeoutException expiration) {
            throw new AssertionError(
                    "attendu en vain pendant " + delaiMs + " ms : " + ceQuOnAttend.get()
                            + ". Ce n'est pas le code qui a tort, c'est ce que le banc attendait qui"
                            + " n'a pas eu lieu.",
                    expiration);
        }
    }
}

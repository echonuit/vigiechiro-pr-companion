package fr.univ_amu.iut.recette;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
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

    /// La même, avec un délai choisi. Utile aux bancs qui savent leur attente courte, et aux témoins
    /// de cette classe, qui ont besoin d'expirer vite.
    public static void que(BooleanSupplier condition, String ceQuOnAttend, long delaiMs) {
        try {
            WaitForAsyncUtils.waitFor(delaiMs, TimeUnit.MILLISECONDS, condition::getAsBoolean);
        } catch (TimeoutException expiration) {
            throw new AssertionError(
                    "attendu en vain pendant " + delaiMs + " ms : " + ceQuOnAttend
                            + ". Ce n'est pas le code qui a tort, c'est ce que le banc attendait qui"
                            + " n'a pas eu lieu.",
                    expiration);
        }
    }
}

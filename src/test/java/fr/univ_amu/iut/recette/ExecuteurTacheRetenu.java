package fr.univ_amu.iut.recette;

import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Un exécuteur qui **retient le travail** à un point de progression choisi, jusqu'au relâchement.
///
/// [ExecuteurTacheRalenti] fait durer un transitoire en pariant sur une DURÉE. Le pari se perd sur une
/// machine rapide : l'opération conclut avant le geste, l'écran a déjà changé, et le cas échoue sans
/// dire pourquoi (#5961). Celui-ci ne parie pas - le travail s'arrête et attend qu'on le relâche.
public final class ExecuteurTacheRetenu implements ExecuteurTache {

    /// Au-delà, l'attente cesse d'elle-même plutôt que de tenir le fork jusqu'à son butoir.
    ///
    /// Un banc qui échoue avant son [#relacher] laisserait sinon le travail bloqué, et les classes
    /// suivantes du même fork attendraient avec lui. Dépasser ce délai est un défaut du banc, pas du
    /// produit : l'attente le DIT en levant, au lieu de rendre la main en silence.
    private static final long BUTOIR_MS = 30_000;

    private final ExecuteurTache delegue;

    private final CountDownLatch relache = new CountDownLatch(1);

    private final CountDownLatch retenu = new CountDownLatch(1);

    private final int aPartirDuPoint;

    private final AtomicInteger points = new AtomicInteger();

    /// @param delegue l'exécuteur réel, à qui tout est confié ; le freiner reste possible en le
    ///     composant, et l'ordre importe peu puisque la retenue suit le relais
    /// @param aPartirDuPoint le rang du point où le travail s'arrête, à partir de 1. Retenir au
    ///     PREMIER laisse un écran presque vide - une table à une ligne ne se cale pas et le geste
    ///     se joue au ras du cadre. Laisser passer quelques points donne au clip de quoi montrer.
    public ExecuteurTacheRetenu(ExecuteurTache delegue, int aPartirDuPoint) {
        this.delegue = delegue;
        this.aPartirDuPoint = aPartirDuPoint;
    }

    /// Laisse le travail repartir, et pour de bon : les points suivants ne sont plus retenus.
    public void relacher() {
        relache.countDown();
    }

    /// Attend que le travail soit EFFECTIVEMENT retenu, et rend la main dès qu'il l'est.
    ///
    /// Sans cette attente, un banc qui cale sa page juste après avoir vu paraître la table la cale
    /// pendant que les points suivants y ajoutent des lignes : le contenu grandit sous lui, et le
    /// calage ne vaut plus. L'écran n'est posé qu'une fois la retenue atteinte.
    public void attendreLaRetenue(long butoirMs) {
        try {
            if (!retenu.await(butoirMs, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Le travail n'a pas atteint son point de retenue ("
                        + aPartirDuPoint + ") en " + butoirMs + " ms : il en a relayé "
                        + points.get() + ".");
            }
        } catch (InterruptedException arret) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public <T> void executer(Supplier<T> travail, Consumer<T> succes, Consumer<Throwable> echec) {
        delegue.executer(travail, succes, echec);
    }

    @Override
    public Executor surFilJavaFx() {
        return delegue.surFilJavaFx();
    }

    @Override
    public Consumer<Progression> relaisProgression(Consumer<Progression> application) {
        Consumer<Progression> reel = delegue.relaisProgression(application);
        return point -> {
            reel.accept(point);
            if (points.incrementAndGet() >= aPartirDuPoint) {
                retenu.countDown();
                attendreLeRelachement();
            }
        };
    }

    /// L'attente, sur le fil qui émet le point - donc jamais celui de JavaFX.
    ///
    /// Le relais est fait AVANT d'attendre : l'écran montre donc le point atteint, puis s'y tient. Un
    /// banc qui attendrait d'abord retiendrait un écran qui n'a pas encore bougé, et le geste porterait
    /// sur l'état d'avant.
    private void attendreLeRelachement() {
        try {
            if (!relache.await(BUTOIR_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Le travail est retenu depuis " + BUTOIR_MS
                        + " ms sans que le banc ne le relâche. Un `relacher()` manque, ou le geste qui"
                        + " devait le précéder a échoué avant de l'atteindre.");
            }
        } catch (InterruptedException arret) {
            Thread.currentThread().interrupt();
        }
    }
}

package fr.univ_amu.iut.recette;

import fr.univ_amu.iut.commun.view.ExecuteurTache;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Un exécuteur qui **retarde le travail**, pour qu'une assertion pressée le rate.
///
/// ## Ce qu'il sert à trouver
///
/// Une assertion posée juste après un geste asynchrone lit un port que le travail n'a pas encore
/// écrit. Elle passe presque toujours - le travail a le plus souvent fini avant - et quand elle
/// échoue, elle échoue dans un autre test, sans motif lisible, et on la classe « flake ». Le dépôt a
/// payé ce diagnostic sur `ScenarioPerceptifRefusDepotTest` (#5152).
///
/// Sous cet exécuteur, un tel site rougit **à tous les coups**. Un site qui reste vert attendait
/// vraiment ce qu'il asserte.
///
/// [ExecuteurTacheRalenti] est son jumeau inverse : il freine le **relais de progression** pour
/// qu'un transitoire dure assez longtemps pour être filmé, quand celui-ci freine le **travail**.
///
/// Le retard a lieu dans le `Supplier` confié au délégué, donc sur le **fil de travail** et jamais
/// sur celui de JavaFX - le freiner gèlerait la scène.
public final class ExecuteurTacheDiffere implements ExecuteurTache {

    /// Assez pour qu'une assertion immédiate tombe dans le trou, assez peu pour qu'une vraie attente
    /// n'expire pas : les attentes du dépôt vont de 5 à 30 secondes.
    public static final long RETARD_PAR_DEFAUT_MS = 400;

    private final ExecuteurTache delegue;

    private final long retardMs;

    public ExecuteurTacheDiffere(ExecuteurTache delegue) {
        this(delegue, RETARD_PAR_DEFAUT_MS);
    }

    /// @param delegue l'exécuteur réel, à qui tout est confié
    /// @param retardMs l'attente ajoutée AVANT le travail, hors du fil JavaFX
    public ExecuteurTacheDiffere(ExecuteurTache delegue, long retardMs) {
        this.delegue = delegue;
        this.retardMs = retardMs;
    }

    @Override
    public <T> void executer(Supplier<T> travail, Consumer<T> succes, Consumer<Throwable> echec) {
        delegue.executer(
                () -> {
                    attendre();
                    return travail.get();
                },
                succes,
                echec);
    }

    @Override
    public Executor surFilJavaFx() {
        return delegue.surFilJavaFx();
    }

    private void attendre() {
        try {
            Thread.sleep(retardMs);
        } catch (InterruptedException arret) {
            Thread.currentThread().interrupt();
        }
    }
}

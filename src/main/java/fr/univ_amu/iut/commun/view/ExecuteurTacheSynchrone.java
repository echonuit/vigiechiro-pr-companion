package fr.univ_amu.iut.commun.view;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// Exécution **synchrone** (défaut, tests) : `travail` puis le callback correspondant s'exécutent
/// immédiatement sur le fil appelant, dans l'ordre. Rend les tests déterministes (pas de fil de fond
/// ni de `Platform.runLater` à attendre).
public final class ExecuteurTacheSynchrone implements ExecuteurTache {

    @Override
    public <T> void executer(Supplier<T> travail, Consumer<T> succes, Consumer<Throwable> echec) {
        T resultat;
        try {
            resultat = travail.get();
        } catch (RuntimeException erreur) {
            echec.accept(erreur);
            return;
        }
        succes.accept(resultat);
    }
}

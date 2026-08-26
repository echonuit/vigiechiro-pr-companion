package fr.univ_amu.iut.recette;

import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Un exécuteur qui **freine le travail** pour qu'un transitoire dure assez longtemps pour être filmé.
///
/// Cinq cas de `S2` portent sur ce qui se passe **pendant** l'import. Sur des fixtures générées,
/// l'opération dure des millisecondes : mesuré sur `sd-nominale` (6 wav) comme sur `sd-grosse` (60), le
/// compte rendu de fin est déjà visible à l'instruction qui suit le clic. Grossir la carte n'y change
/// rien, et les specs posent que les wav restent brefs pour rester légères.
///
/// **Le clip montre donc une lenteur que le produit n'a pas**, et c'est assumé plutôt que caché : un cas
/// qui ment sur le produit est pire qu'un cas absent (ADR 4142). Ce qui rend celui-ci lisible est qu'il
/// le DISE - ici, et dans l'encart que porte la session. Il démontre que ces cinq surfaces existent et
/// s'enchaînent, jamais combien de temps un import prend.
///
/// Le frein s'applique au **relais de progression**, non au travail : l'attente a lieu sur le fil qui
/// émet, jamais sur celui de JavaFX - le freiner gèlerait la scène, et un banc qui ne rend plus d'image
/// ne filme rien. Elle est ainsi proportionnelle : six fichiers font six pas, soixante en font soixante.
/// Un `Thread.sleep` posé sur le travail entier aurait fait mentir la barre au lieu de la montrer.
public final class ExecuteurTacheRalenti implements ExecuteurTache {

    private final ExecuteurTache delegue;

    private final long pauseParPointMs;

    /// @param delegue l'exécuteur réel, à qui tout est confié
    /// @param pauseParPointMs l'attente ajoutée à chaque point de progression, hors du fil JavaFX
    public ExecuteurTacheRalenti(ExecuteurTache delegue, long pauseParPointMs) {
        this.delegue = delegue;
        this.pauseParPointMs = pauseParPointMs;
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
            attendre();
            reel.accept(point);
        };
    }

    /// L'attente, sur le fil qui émet le point - donc jamais celui de JavaFX.
    ///
    /// Une interruption n'est pas rattrapée en silence : le drapeau est reposé et l'attente cesse. Un
    /// banc qu'on arrête doit s'arrêter, et avaler l'interruption ferait tourner le frein pendant que
    /// tout le reste s'éteint.
    private void attendre() {
        try {
            Thread.sleep(pauseParPointMs);
        } catch (InterruptedException arret) {
            Thread.currentThread().interrupt();
        }
    }
}

package fr.univ_amu.iut.commun.view;

import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;

/// Exécution **asynchrone** (production) : `travail` tourne sur un **thread virtuel** (idiome du reste
/// de l'application pour les traitements bloquants - disque, réseau), puis le résultat ou l'erreur est
/// reprogrammé sur le fil JavaFX via [Platform#runLater]. L'IHM reste réactive pendant le traitement.
public final class ExecuteurTacheAsynchrone implements ExecuteurTache {

    @Override
    public <T> void executer(Supplier<T> travail, Consumer<T> succes, Consumer<Throwable> echec) {
        Thread.ofVirtual().name("tache-occupation").start(() -> {
            try {
                T resultat = travail.get();
                Platform.runLater(() -> succes.accept(resultat));
            } catch (RuntimeException erreur) {
                Platform.runLater(() -> echec.accept(erreur));
            }
        });
    }
}

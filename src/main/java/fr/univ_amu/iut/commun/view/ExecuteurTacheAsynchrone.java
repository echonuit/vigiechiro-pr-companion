package fr.univ_amu.iut.commun.view;

import java.util.concurrent.Executor;
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
                JournalisationTache.consigner(erreur);
                Platform.runLater(() -> echec.accept(erreur));
            }
        });
    }

    /// Chaque événement émis par le travail (progression, suivi) est reposté sur le fil JavaFX, dans
    /// l'ordre de soumission (garantie [Platform#runLater]).
    ///
    /// **Sans toolkit, il n'y a pas de fil à rejoindre** (#3542) : la CLI tourne dans un processus où
    /// JavaFX n'a jamais démarré, et `Platform.runLater` y lève `IllegalStateException`. Elle exécute
    /// pourtant le même code de domaine que l'IHM, et depuis que les écritures annoncent leurs
    /// mutations, ce code passe par ici. Un `creer-site` en ligne de commande échouait donc, alors
    /// qu'il n'a **personne à réveiller**.
    ///
    /// On exécute alors **sur place**. C'est exact et non un pis-aller : le report existe pour
    /// respecter le fil propriétaire des propriétés observables ; là où aucune vue n'observe, il n'y a
    /// rien à respecter. Le défaut a été trouvé par les E2E `bats` sur le vrai fat-jar, qu'aucun test
    /// Java in-process ne pouvait voir.
    @Override
    public Executor surFilJavaFx() {
        return commande -> {
            try {
                Platform.runLater(commande);
            } catch (IllegalStateException toolkitAbsent) {
                commande.run();
            }
        };
    }
}

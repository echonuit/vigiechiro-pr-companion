package fr.univ_amu.iut.commun.view;

import com.google.inject.ImplementedBy;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Exécute un **travail lourd hors du fil JavaFX** puis en applique le résultat (ou l'erreur) **sur le
/// fil JavaFX** (#1014). Généralise le patron répété dans les contrôleurs
/// (`Thread.ofVirtual()… Platform.runLater(…)`) en une primitive maîtrisant le fil d'exécution : en
/// production le travail tourne en arrière-plan (l'IHM ne gèle pas), en test tout est **synchrone**
/// (déterministe). Sœur de [ExecuteurFiche], spécialisée aux traitements lourds génériques.
///
/// [ImplementedBy] fixe l'exécution synchrone comme défaut (tests) ; l'application complète surcharge
/// par [ExecuteurTacheAsynchrone] (`CommunModule`).
@ImplementedBy(ExecuteurTacheSynchrone.class)
public interface ExecuteurTache {

    /// Exécute `travail` (jamais sur le fil JavaFX en production), puis remet son résultat à `succes`,
    /// ou, si `travail` lève, l'erreur à `echec` - `succes` et `echec` s'exécutant sur le **fil
    /// JavaFX**. Exactement l'un des deux est appelé. `echec` route typiquement le message vers le filet
    /// d'erreurs de l'écran (#795).
    <T> void executer(Supplier<T> travail, Consumer<T> succes, Consumer<Throwable> echec);
}

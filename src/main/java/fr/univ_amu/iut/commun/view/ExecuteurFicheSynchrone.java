package fr.univ_amu.iut.commun.view;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// Exécution **synchrone** : résout puis ouvre sur le fil appelant. Défaut d'injection, déterministe pour
/// les tests ; suffisant quand la résolution ne fait pas de réseau (identité).
public final class ExecuteurFicheSynchrone implements ExecuteurFiche {

    @Override
    public void resoudrePuisOuvrir(Supplier<String> resolution, Consumer<String> ouverture) {
        ouverture.accept(resolution.get());
    }
}

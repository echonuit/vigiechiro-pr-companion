package fr.univ_amu.iut.commun.view;

import java.util.Objects;

/// Porteur **injectable** de la confirmation d'action destructive (#1013) : par défaut le vrai dialogue
/// ([ConfirmationNavigation]), remplaçable par un stub déterministe en test ([#definir]) - un
/// `showAndWait` natif figerait TestFX headless. Remplace le duo « champ [java.util.function.Predicate]
/// + setter » jusque-là redéclaré dans chaque contrôleur (#798) : le contrôleur détient une instance
/// `final` et l'expose telle quelle à ses tests, la sémantique (défaut, garde nulle) vit ici.
public final class ConfirmateurModifiable implements Confirmateur {

    private Confirmateur delegue;

    /// Porteur au dialogue par défaut.
    public ConfirmateurModifiable() {
        this(new ConfirmationNavigation());
    }

    /// Porteur au dialogue **initial** fourni (ex. [ConfirmationNavigation] à titre personnalisé).
    public ConfirmateurModifiable(Confirmateur initial) {
        this.delegue = Objects.requireNonNull(initial, "confirmateur");
    }

    @Override
    public boolean confirmer(String message) {
        return delegue.confirmer(message);
    }

    /// Remplace la stratégie de confirmation (stub déterministe dans les tests).
    public void definir(Confirmateur confirmateur) {
        this.delegue = Objects.requireNonNull(confirmateur, "confirmateur");
    }
}

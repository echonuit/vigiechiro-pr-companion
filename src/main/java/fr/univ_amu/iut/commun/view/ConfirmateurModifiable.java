package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
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

    /// Délègue le **compte rendu structuré** tel quel (#2223), au lieu de le laisser retomber sur le repli
    /// textuel du port. Sans cette redirection, un `confirmer(CompteRendu)` sur ce porteur aplatissait la
    /// structure **avant** d'atteindre le délégué : le vrai dialogue ([ConfirmationNavigation]) ne recevait
    /// qu'une chaîne, et le rendu structuré de #2060 ne se voyait qu'en test et en capture (qui court-circuitent
    /// ce porteur), jamais en production - où tous les contrôleurs passent par ce `ConfirmateurModifiable` (#1013).
    @Override
    public boolean confirmer(CompteRendu compteRendu) {
        return delegue.confirmer(compteRendu);
    }

    /// Remplace la stratégie de confirmation (stub déterministe dans les tests).
    public void definir(Confirmateur confirmateur) {
        this.delegue = Objects.requireNonNull(confirmateur, "confirmateur");
    }
}

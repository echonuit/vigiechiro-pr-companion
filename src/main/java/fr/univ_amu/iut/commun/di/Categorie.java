package fr.univ_amu.iut.commun.di;

/// Catégorie d'une [Fonctionnalite] : détermine si elle est désactivable et son état par défaut.
///
/// Source unique de vérité de la « désactivabilité » d'une feature (système de feature-flags, #1057).
public enum Categorie {

    /// Feature **socle / load-bearing** : jamais désactivable (d'autres features en dépendent ; la
    /// retirer casserait l'injecteur ou un écran). Toujours active, masquée de l'UI de gestion.
    COEUR(true),

    /// Feature **optionnelle** : désactivable, **active par défaut**.
    OPTIONNELLE(true),

    /// Feature **expérimentale** : désactivable, **inactive par défaut** (workflow « merger une
    /// feature en cours de développement derrière un flag OFF »).
    EXPERIMENTALE(false);

    private final boolean activeParDefaut;

    Categorie(boolean activeParDefaut) {
        this.activeParDefaut = activeParDefaut;
    }

    /// État d'activation par défaut, en l'absence de flag explicite (propriété système ou réglage).
    public boolean activeParDefaut() {
        return activeParDefaut;
    }

    /// Une feature `COEUR` n'est jamais désactivable ; les autres le sont.
    public boolean desactivable() {
        return this != COEUR;
    }
}

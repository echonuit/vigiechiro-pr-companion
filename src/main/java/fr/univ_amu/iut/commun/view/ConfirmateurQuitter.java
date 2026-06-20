package fr.univ_amu.iut.commun.view;

/// Stratégie de **confirmation** avant de quitter un écran à saisie non enregistrée. Permet au
/// [Navigateur] de demander l'avis de l'utilisateur **sans dépendre d'une boîte de dialogue concrète** :
/// l'application branche [ConfirmationNavigation] (vrai `Alert`), les tests un stub déterministe (sans
/// toolkit JavaFX).
@FunctionalInterface
public interface ConfirmateurQuitter {

    /// Demande confirmation. Renvoie `true` pour quitter quand même, `false` pour rester sur place.
    boolean confirmer(String message);
}

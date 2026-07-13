package fr.univ_amu.iut.commun.view;

/// Stratégie de **confirmation d'une action à conséquences** : quitter un écran à saisie non
/// enregistrée, écraser un passage, supprimer des archives, réimporter des résultats… Contrat **neutre**
/// du socle (#1013, généralise le `ConfirmateurQuitter` né avec le [Navigateur]) : les composants
/// demandent l'avis de l'utilisateur **sans dépendre d'une boîte de dialogue concrète** - l'application
/// branche [ConfirmationNavigation] (vrai `Alert`), les tests un stub déterministe (un dialogue natif
/// figerait TestFX headless). Voir [ConfirmateurModifiable] pour le porteur injectable partagé.
@FunctionalInterface
public interface Confirmateur {

    /// Demande confirmation. Renvoie `true` pour poursuivre l'action, `false` pour y renoncer.
    boolean confirmer(String message);
}

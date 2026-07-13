package fr.univ_amu.iut.commun.view;

/// Contrat **optionnel** d'un écran (son controller) qui porte une **saisie non enregistrée** : il
/// signale au [Navigateur] qu'on ne peut pas le quitter sans risque de perte.
///
/// Avant toute navigation sortante (retour, clic d'un segment du fil d'Ariane, retour à l'accueil), le
/// socle interroge la garde de l'écran courant. Si une saisie est en cours, le [Navigateur] demande une
/// confirmation (cf. [Confirmateur] / [ConfirmationNavigation]) ; l'utilisateur peut annuler et
/// rester sur place.
///
/// Un écran sans saisie sensible n'implémente simplement pas ce contrat (navigation libre).
public interface GardeQuitter {

    /// `true` s'il existe une saisie non enregistrée qui serait perdue en quittant l'écran.
    boolean aSaisieNonEnregistree();

    /// Message de confirmation présenté avant de quitter (surchargeable par l'écran).
    default String messageConfirmationQuitter() {
        return "Des modifications non enregistrées seront perdues. Quitter cet écran quand même ?";
    }
}

package fr.univ_amu.iut.audio.viewmodel;

/// Retour d'une **opération** de la vue audio (import CSV, export `_Vu` / bibliothèque, valider,
/// corriger…) : un texte + une **sévérité**, exposé par [AudioViewModel] dans une propriété distincte
/// du message d'**état vide** de la table.
///
/// La distinction est délibérée : avant, erreurs d'opération et indice « aucune observation »
/// partageaient la même propriété, donc une erreur d'import s'affichait dans le placeholder gris de la
/// table, indistinguable de « pas de données » (incident « For input string: SUR » invisible). En
/// séparant les deux, la vue rend le retour d'opération dans un **bandeau toujours visible**, coloré
/// selon la sévérité, là où le placeholder gris reste réservé au seul état vide.
///
/// @param texte message présenté à l'utilisateur (vide = aucun retour à afficher)
/// @param severite niveau d'affichage (succès / information / erreur)
public record RetourOperation(String texte, Severite severite) {

    /// Niveau d'affichage d'un [RetourOperation], piloté côté vue par une classe CSS dédiée.
    public enum Severite {
        SUCCES,
        INFO,
        ERREUR
    }

    /// Aucun retour à afficher (état nominal).
    public static final RetourOperation AUCUN = new RetourOperation("", Severite.INFO);

    /// Retour de **succès** (vert) : opération réussie, avec un bilan.
    public static RetourOperation succes(String texte) {
        return new RetourOperation("✅ " + texte, Severite.SUCCES);
    }

    /// Retour d'**information** (neutre) : action refusée ou guidage, sans échec technique.
    public static RetourOperation info(String texte) {
        return new RetourOperation("ℹ " + texte, Severite.INFO);
    }

    /// Retour d'**erreur** (rouge) : l'opération a échoué.
    public static RetourOperation erreur(String texte) {
        return new RetourOperation("⚠ " + (texte == null ? "Une erreur est survenue." : texte), Severite.ERREUR);
    }

    /// `true` s'il y a un texte à présenter (bandeau visible).
    public boolean present() {
        return texte != null && !texte.isBlank();
    }
}

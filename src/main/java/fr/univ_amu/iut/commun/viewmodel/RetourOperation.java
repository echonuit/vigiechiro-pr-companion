package fr.univ_amu.iut.commun.viewmodel;

/// Retour d'une **opération** d'un écran (import CSV, export, valider, corriger, action refusée…) :
/// un texte + une **sévérité**, exposé par le ViewModel dans une propriété distincte du message
/// d'**état vide** de la table.
///
/// La distinction est délibérée : avant, erreurs d'opération et indice « aucune observation »
/// partageaient la même propriété, donc une erreur d'import s'affichait dans le placeholder gris de la
/// table, indistinguable de « pas de données » (incident « For input string: SUR » invisible). En
/// séparant les deux, la vue rend le retour d'opération dans un **bandeau toujours visible**, coloré
/// selon la sévérité, là où le placeholder gris reste réservé au seul état vide.
///
/// Né dans la vue audio, remonté dans `commun` quand l'Inventaire a eu besoin du même canal (#1837) :
/// « rendre compte sans bloquer » n'a rien de propre à un écran. Se rend avec
/// [fr.univ_amu.iut.commun.view.BandeauRetour].
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

    /// Retour d'**information** (neutre) : action refusée ou guidage, sans échec technique. Le sélecteur
    /// de variante (U+FE0F) force la **présentation emoji** de l'icône (sinon « ℹ » s'affiche en texte « i »).
    public static RetourOperation info(String texte) {
        return new RetourOperation("ℹ️ " + texte, Severite.INFO);
    }

    /// Retour d'**erreur** (rouge) : l'opération a échoué.
    public static RetourOperation erreur(String texte) {
        return new RetourOperation("⚠️ " + (texte == null ? "Une erreur est survenue." : texte), Severite.ERREUR);
    }

    /// `true` s'il y a un texte à présenter (bandeau visible).
    public boolean present() {
        return texte != null && !texte.isBlank();
    }
}

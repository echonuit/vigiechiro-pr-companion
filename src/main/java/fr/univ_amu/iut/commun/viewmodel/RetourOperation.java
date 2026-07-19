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
/// La sévérité ne s'écrit **pas** dans le texte : elle se rend une fois, par le composant, en couleur
/// et en icône (#1933). Un pictogramme collé au message le disait une seconde fois, et dépendait des
/// polices de la machine - sur celles qui ne le portent pas, il ne s'affichait pas du tout.
///
/// @param texte message présenté à l'utilisateur (vide = aucun retour à afficher)
/// @param severite niveau d'affichage (succès / information / avertissement / erreur)
public record RetourOperation(String texte, Severite severite) {

    /// Glyphes de sévérité, **refusés en tête de message**. Cf. le contrôle du constructeur compact.
    private static final String GLYPHES_DE_SEVERITE = "⚠✓✗❌⛔✅❗";

    /// Refuse un message qui **ouvre par un glyphe de sévérité**.
    ///
    /// La sévérité est portée par [#severite()], et la vue la rend deux fois - en couleur et en icône
    /// ([fr.univ_amu.iut.commun.view.BandeauRetour], [fr.univ_amu.iut.commun.view.LibelleRetour]). Un
    /// « ⚠ » dans la chaîne la dirait une troisième fois, sans que rien ne garantisse l'accord entre les
    /// trois : un message « ⚠ … » posé en `Severite.ERREUR` s'afficherait en rouge avec un cercle barré
    /// et un triangle dans le texte.
    ///
    /// Ce n'est pas une précaution théorique. Avant #2052, faute d'un niveau `AVERTISSEMENT`, huit
    /// propriétés avaient **quitté ce type** pour redevenir des chaînes libres portant leur glyphe
    /// (#2050) - et une fois dehors, plus rien ne bornait leur longueur : trois y joignaient des listes.
    /// La garde ferme la porte par laquelle elles sont sorties.
    ///
    /// Elle ne couvre **que** ce type. Les propriétés `String` ad hoc restent libres d'écrire ce qu'elles
    /// veulent : c'est le trou que #2050 achève de combler, propriété par propriété.
    public RetourOperation {
        if (texte != null && !texte.isEmpty() && GLYPHES_DE_SEVERITE.indexOf(texte.charAt(0)) >= 0) {
            throw new IllegalArgumentException(
                    "La sévérité d'un retour est portée par son niveau, pas par un glyphe en tête de"
                            + " message. Message refusé : " + texte);
        }
    }

    /// Niveau d'affichage d'un [RetourOperation], piloté côté vue par une classe CSS dédiée.
    ///
    /// ⚠ **L'ordre de déclaration porte la sévérité** : [CompteRendu#severite()] prend le maximum par
    /// `ordinal()`. Réordonner ces constantes changerait silencieusement quel constat qualifie un compte
    /// rendu entier. `SeveriteTest` épingle l'ordre pour que ce ne soit pas une convention tacite.
    public enum Severite {
        SUCCES,
        INFO,
        AVERTISSEMENT,
        ERREUR
    }

    /// Aucun retour à afficher (état nominal).
    public static final RetourOperation AUCUN = new RetourOperation("", Severite.INFO);

    /// Retour de **succès** (vert) : opération réussie, avec un bilan.
    public static RetourOperation succes(String texte) {
        return new RetourOperation(texte, Severite.SUCCES);
    }

    /// Retour d'**information** (neutre) : action refusée ou guidage, sans échec technique.
    public static RetourOperation info(String texte) {
        return new RetourOperation(texte, Severite.INFO);
    }

    /// Retour d'**avertissement** (ambre) : l'opération a abouti, mais quelque chose mérite l'attention.
    ///
    /// Le niveau manquait, et son absence ne s'est pas traduite par des avertissements mal classés : ils
    /// ont **quitté le type** pour redevenir des chaînes libres portant un « ⚠ » en tête (huit propriétés
    /// recensées, #2050). Une fois dehors, plus rien ne bornait leur longueur.
    public static RetourOperation avertissement(String texte) {
        return new RetourOperation(texte, Severite.AVERTISSEMENT);
    }

    /// Retour d'**erreur** (rouge) : l'opération a échoué.
    public static RetourOperation erreur(String texte) {
        return new RetourOperation(texte == null ? "Une erreur est survenue." : texte, Severite.ERREUR);
    }

    /// `true` s'il y a un texte à présenter (bandeau visible).
    public boolean present() {
        return texte != null && !texte.isBlank();
    }
}

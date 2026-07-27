package fr.univ_amu.iut.commun.model;

import java.util.Objects;

/// Ce qui est arrivé à **un** passage dans un traitement groupé (#2357). Le compte rendu de fin en est
/// la liste : chaque passage y figure, réussi ou non, avec son motif.
///
/// @param cible le passage concerné
/// @param statut ce qui lui est arrivé
/// @param motif explication pour un passage écarté, en échec ou non traité ; vide si réussi
public record IssueTraitement(CiblePassage cible, Statut statut, String motif) {

    public IssueTraitement {
        Objects.requireNonNull(cible, "cible");
        Objects.requireNonNull(statut, "statut");
        motif = motif == null ? "" : motif;
    }

    /// Le passage a reçu l'action, sans erreur.
    public static IssueTraitement reussi(CiblePassage cible) {
        return new IssueTraitement(cible, Statut.REUSSI, "");
    }

    /// Le passage était **inéligible** : écarté **avant** le lancement, avec son motif.
    public static IssueTraitement ecarte(CiblePassage cible, String motif) {
        return new IssueTraitement(cible, Statut.ECARTE, motif);
    }

    /// L'action a été tentée et a échoué. Le lot **continue** : les autres passages ne sont pas punis.
    public static IssueTraitement echec(CiblePassage cible, String motif) {
        return new IssueTraitement(cible, Statut.ECHEC, motif);
    }

    /// Le lot s'est arrêté avant d'arriver à ce passage (annulation). Il est **intact** : rien ne lui a
    /// été fait, il ne reste donc pas dans un état intermédiaire.
    public static IssueTraitement nonTraite(CiblePassage cible) {
        return new IssueTraitement(cible, Statut.NON_TRAITE, "Lot interrompu avant ce passage");
    }

    /// Sévérité à afficher pour cette issue dans le compte rendu.
    public Severite severite() {
        return switch (statut) {
            case REUSSI -> Severite.SUCCES;
            case ECARTE, NON_TRAITE -> Severite.AVERTISSEMENT;
            case ECHEC -> Severite.ERREUR;
        };
    }

    /// Le sort d'un passage dans un traitement groupé.
    public enum Statut {
        /// Action exécutée sans erreur.
        REUSSI,

        /// Inéligible : jamais tentée (annoncé avant le lancement).
        ECARTE,

        /// Tentée, échouée.
        ECHEC,

        /// Jamais atteinte : le lot a été interrompu avant.
        NON_TRAITE
    }
}

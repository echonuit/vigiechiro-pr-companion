package fr.univ_amu.iut.commun.model;

import java.util.List;
import java.util.Objects;

/// Ce qu'a produit un traitement groupé (#2357) : **une issue par passage soumis**, réussi ou non.
///
/// Le contrat est qu'aucun passage de la sélection ne disparaît du compte rendu — ni les inéligibles
/// (écartés), ni ceux que l'annulation n'a pas atteints. C'est ce qui distingue un lot qu'on peut
/// diagnostiquer d'un lot qui « a échoué » sans dire où.
///
/// @param libelleAction le geste tenté (« Préparer le dépôt »)
/// @param issues le sort de chaque passage, dans l'ordre où ils ont été soumis
/// @param interrompu vrai si l'utilisateur a demandé l'arrêt avant la fin
public record ResultatTraitementGroupe(String libelleAction, List<IssueTraitement> issues, boolean interrompu) {

    public ResultatTraitementGroupe {
        Objects.requireNonNull(libelleAction, "libelleAction");
        issues = List.copyOf(issues);
    }

    /// Nombre de passages ayant reçu l'action sans erreur.
    public long reussis() {
        return compter(IssueTraitement.Statut.REUSSI);
    }

    /// Nombre de passages écartés d'emblée (inéligibles).
    public long ecartes() {
        return compter(IssueTraitement.Statut.ECARTE);
    }

    /// Nombre de passages dont l'action a échoué.
    public long echecs() {
        return compter(IssueTraitement.Statut.ECHEC);
    }

    /// Nombre de passages jamais atteints (lot interrompu avant eux).
    public long nonTraites() {
        return compter(IssueTraitement.Statut.NON_TRAITE);
    }

    private long compter(IssueTraitement.Statut statut) {
        return issues.stream().filter(issue -> issue.statut() == statut).count();
    }

    /// Phrase de tête du compte rendu : ce qui a été fait, sur combien, et si l'on s'est arrêté en route.
    public String resume() {
        String base = reussis() + "/" + issues.size() + " passage(s) traité(s)";
        return interrompu ? base + " : lot interrompu à la demande" : base;
    }
}

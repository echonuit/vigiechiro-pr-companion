package fr.univ_amu.iut.commun.api;

import java.util.Objects;

/// L'issue d'un dépôt en parties, avec la [Provenance] de l'étape qui l'a produite (#5598).
///
/// Un dépôt en parties enchaîne l'API et le stockage : demande d'URL, `PUT` de la partie,
/// finalisation. Sa première issue en échec peut venir de l'un comme de l'autre, et seul ce type
/// dit lequel. La provenance n'a de sens que pour un refus ; sur un succès ou une panne locale, elle
/// vaut [Provenance#API], sans conséquence.
public record IssueDeDepot(ReponseApi<String> reponse, Provenance provenance) {

    public IssueDeDepot {
        Objects.requireNonNull(reponse, "reponse");
        Objects.requireNonNull(provenance, "provenance");
    }

    public static IssueDeDepot api(ReponseApi<String> reponse) {
        return new IssueDeDepot(reponse, Provenance.API);
    }

    public static IssueDeDepot stockage(ReponseApi<String> reponse) {
        return new IssueDeDepot(reponse, Provenance.STOCKAGE);
    }
}

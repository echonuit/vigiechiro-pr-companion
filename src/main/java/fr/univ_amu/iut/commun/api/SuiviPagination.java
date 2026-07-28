package fr.univ_amu.iut.commun.api;

/// Suivi du **parcours paginé** d'une collection Eve (#1534) : notifié après chaque page lue, avec le
/// numéro de page (1-based) et le **nombre total de pages** quand le serveur l'annonce (`_meta.total`).
///
/// Le total permet une progression **déterminée** (« page XX/YY ») plutôt qu'une simple animation :
/// l'utilisateur peut anticiper. `totalPages` vaut `0` quand le total est **inconnu** (le serveur ne
/// l'annonce pas, ou le corps est illisible) - l'appelant retombe alors sur un avancement approché.
@FunctionalInterface
public interface SuiviPagination {

    /// Appelé après chaque page **non vide**, avant de demander la suivante. Une exception levée ici
    /// interrompt le parcours et remonte telle quelle (annulation coopérative d'un long téléchargement).
    void surPage(int page, int totalPages);

    /// `true` si l'appelant a **demandé l'annulation**. Consulté pendant les temporisations de réessai
    /// (#2619), là où [#surPage] ne peut rien dire : une reprise dure plusieurs secondes, et « Annuler »
    /// n'a pas à y disparaître - c'est le trou que le relais page par page avait bouché (#1522).
    ///
    /// Le défaut est « jamais » : un parcours qui n'offre pas d'annulation ne renonce pas. Redéfinir avec
    /// le drapeau coopératif de l'appelant, `jeton::estAnnule`.
    default boolean renonce() {
        return false;
    }
}

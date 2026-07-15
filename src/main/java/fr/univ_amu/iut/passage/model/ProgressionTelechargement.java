package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Progression;

/// Progression **du téléchargement** des observations d'une nuit (#1534), sur la plage [0,10 ; 0,90] :
/// c'est l'essentiel du temps d'une reconstruction (la suite - création des séquences puis import - va
/// très vite).
///
/// **Déterminée** dès que le nombre de pages est connu (`_meta.total`) : « page XX/YY » et une barre
/// exacte, ce qui rend aussi l'estimation de temps honnête. Total **inconnu** (`0`) : avancement
/// **approché** qui tend vers 0,90 sans l'atteindre (la barre avance à chaque page, de moins en moins
/// vite).
final class ProgressionTelechargement {

    private ProgressionTelechargement() {}

    /// Point de progression pour la page `page` (1-based), sachant `totalPages` (`0` si inconnu).
    static Progression pour(int page, int totalPages) {
        return new Progression(libelle(page, totalPages), avancement(page, totalPages));
    }

    private static String libelle(int page, int totalPages) {
        return totalPages > 0
                ? "Téléchargement des observations (page " + page + "/" + totalPages + ")…"
                : "Téléchargement des observations (page " + page + ")…";
    }

    private static double avancement(int page, int totalPages) {
        if (totalPages > 0) {
            return 0.10 + 0.80 * Math.min(page, totalPages) / (double) totalPages;
        }
        return 0.10 + 0.80 * (1.0 - 1.0 / (1.0 + 0.1 * page));
    }
}

package fr.univ_amu.iut.sites.model;

/// Ce qu'on a pu tirer d'un texte de position **collé depuis une carte** (#4575).
///
/// Le verdict porte son propre message, comme [VerdictCarre] : chaque cas répond pour lui-même
/// plutôt qu'un `switch` chez l'appelant.
public sealed interface LecturePosition {

    /// Message à afficher, vide quand il n'y a rien à dire.
    String message();

    /// Une position lue, en degrés décimaux.
    record Lue(double latitude, double longitude) implements LecturePosition {
        @Override
        public String message() {
            return "";
        }
    }

    /// Un **lien** de carte, et non une position. Refusé, et jamais analysé : les liens
    /// courts `maps.app.goo.gl` ne portent aucune coordonnée tant qu'ils ne sont pas résolus, et les
    /// résoudre voudrait dire appeler Google. Accepter les liens longs et refuser les courts donnerait
    /// une lecture qui s'explique mal : deux liens copiés sur la même carte, l'un marche et l'autre non.
    record UrlDeCarte() implements LecturePosition {
        @Override
        public String message() {
            return "Ce lien n'est pas une position. Sur la carte, faites un clic droit sur le point et"
                    + " copiez ses coordonnées, puis collez-les ici : 43.296482, 5.369780.";
        }
    }

    /// Le texte ne porte pas deux nombres lisibles. On ne devine rien : le refus dit ce qu'on attend.
    record Illisible() implements LecturePosition {
        @Override
        public String message() {
            return "Ce texte ne porte pas de position. Collez deux nombres séparés par une virgule,"
                    + " latitude puis longitude, par exemple 43.296482, 5.369780.";
        }
    }
}

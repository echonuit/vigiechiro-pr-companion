package fr.univ_amu.iut.commun.model;

/// Distance **orthodromique** (formule de Haversine) entre deux positions GPS, en mètres (#154). Sert
/// à mesurer l'écart entre points d'écoute d'un même site et à repérer des points anormalement proches.
///
/// L'approximation sphérique (rayon terrestre moyen) suffit largement à l'échelle d'un carré Vigie-Chiro
/// (quelques kilomètres) : l'erreur par rapport à un calcul ellipsoïdal y est négligeable.
public final class DistanceGeo {

    /// Rayon terrestre moyen (m) utilisé par Haversine.
    private static final double RAYON_TERRE_METRES = 6_371_000.0;

    /// Écart (m) en deçà duquel deux positions désignent **le même endroit** (#3750).
    ///
    /// ## Une seule constante, parce qu'il n'y a qu'une question
    ///
    /// Deux endroits du code y répondaient séparément : l'audit en ligne comparait **axe par axe, en
    /// degrés** (`1e-4`), la publication d'un point en **mètres** (15). Entre les deux valeurs, l'audit
    /// déclarait deux points identiques pendant que la publication les disait distincts - et
    /// réciproquement selon la latitude. L'utilisateur aurait lu les deux verdicts sur le même écran.
    ///
    /// ⚠️ **Le degré n'est pas une unité de distance** : `1e-4` degré de longitude vaut ~11 m à
    /// l'équateur, ~7,8 m à 45° N et ~5,6 m à 60° N. Une tolérance exprimée ainsi se resserre à mesure
    /// qu'on monte vers le nord, sans que personne ne l'ait décidé. La comparaison passe donc par
    /// [#metresEntre], qui ne varie pas.
    ///
    /// La valeur n'absorbe pas un bruit d'arrondi - les coordonnées voyagent à six décimales, soit un
    /// aller-retour exact à une dizaine de centimètres - mais la **variation humaine et instrumentale** :
    /// position relevée au GPS d'un côté, saisie à la main de l'autre, ou reprise d'une trace. Quinze
    /// mètres restent un ordre de grandeur sous le seuil de protocole (200 m), qui interdit à deux points
    /// distincts d'être aussi proches.
    public static final double ECART_MEME_ENDROIT_METRES = 15.0;

    private DistanceGeo() {}

    /// Les deux positions désignent-elles **le même endroit** ([#ECART_MEME_ENDROIT_METRES]) ?
    public static boolean memeEndroit(double latitude1, double longitude1, double latitude2, double longitude2) {
        return metresEntre(latitude1, longitude1, latitude2, longitude2) <= ECART_MEME_ENDROIT_METRES;
    }

    /// Distance en mètres entre `(latitude1, longitude1)` et `(latitude2, longitude2)`, coordonnées en
    /// **degrés décimaux**. Toujours ≥ 0, symétrique, nulle pour deux positions identiques.
    public static double metresEntre(double latitude1, double longitude1, double latitude2, double longitude2) {
        double deltaLatitude = Math.toRadians(latitude2 - latitude1);
        double deltaLongitude = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
                + Math.cos(Math.toRadians(latitude1))
                        * Math.cos(Math.toRadians(latitude2))
                        * Math.sin(deltaLongitude / 2)
                        * Math.sin(deltaLongitude / 2);
        return RAYON_TERRE_METRES * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}

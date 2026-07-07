package fr.univ_amu.iut.commun.model;

/// Distance **orthodromique** (formule de Haversine) entre deux positions GPS, en mètres (#154). Sert
/// à mesurer l'écart entre points d'écoute d'un même site et à repérer des points anormalement proches.
///
/// L'approximation sphérique (rayon terrestre moyen) suffit largement à l'échelle d'un carré Vigie-Chiro
/// (quelques kilomètres) : l'erreur par rapport à un calcul ellipsoïdal y est négligeable.
public final class DistanceGeo {

    /// Rayon terrestre moyen (m) utilisé par Haversine.
    private static final double RAYON_TERRE_METRES = 6_371_000.0;

    private DistanceGeo() {}

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

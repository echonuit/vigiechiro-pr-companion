package fr.univ_amu.iut.commun.model;

/// Passer des degrés aux mètres, **à deux précisions** qui ne sont pas interchangeables.
///
/// Trois classes faisaient cette conversion, avec deux valeurs pour la même grandeur physique.
/// L'écart de 0,12 % - 111 000 m par degré contre 111 132 - est **délibéré**, et c'est ce que ce type
/// existe pour porter : sans lui, il se lisait comme une incohérence, et le premier lecteur pressé les
/// aurait unifiées.
///
/// | Usage | Constante | Ce qu'un écart de 130 m y fait |
/// |---|---|---|
/// | **Dessiner** une emprise de 2 km | [#KM_PAR_DEGRE_LAT_DESSIN] | rien de visible à l'écran |
/// | **Mesurer** une distance | [#METRES_PAR_DEGRE_LAT] | fait dériver un seuil de 50 m |
///
/// Constaté à la passe 7 de la clôture du chantier #4573, et tranché plutôt qu'assumé : une divergence
/// documentée dans une seule des trois classes n'aurait averti que qui ouvrait celle-là.
public final class ConversionGeographique {

    /// Mètres par degré de latitude, valeur **précise**. Pour mesurer.
    ///
    /// C'est elle qui reproduit ce que la plateforme rend : 1 412 m au coin d'une maille, mesuré le
    /// 2026-08-27 contre le serveur réel.
    public static final double METRES_PAR_DEGRE_LAT = 111_132;

    /// Mètres par degré de longitude **à l'équateur**. À multiplier par le cosinus de la latitude.
    public static final double METRES_PAR_DEGRE_LON_EQUATEUR = 111_320;

    /// Kilomètres par degré de latitude, valeur **ronde**. Pour dessiner.
    ///
    /// Les emprises de carte l'emploient depuis #325. La remplacer par la valeur précise déplacerait
    /// des dessins de 130 m sur une maille de 2 km, ce qui ne corrige rien et change des captures.
    public static final double KM_PAR_DEGRE_LAT_DESSIN = 111.0;

    private ConversionGeographique() {}

    /// Distance en mètres entre deux positions, par projection équirectangulaire locale.
    ///
    /// À l'échelle du kilomètre, elle s'écarte de la distance géodésique de bien moins d'un mètre :
    /// 1 411,7 m calculés ici pour 1 412 m rendus par la plateforme.
    public static double distanceMetres(double lat1, double lon1, double lat2, double lon2) {
        double dx = (lon2 - lon1) * METRES_PAR_DEGRE_LON_EQUATEUR * Math.cos(Math.toRadians(lat1));
        double dy = (lat2 - lat1) * METRES_PAR_DEGRE_LAT;
        return Math.hypot(dx, dy);
    }

    /// Combien de degrés de latitude valent `kilometres`, à la précision du **dessin**.
    public static double degresDeLatitudePour(double kilometres) {
        return kilometres / KM_PAR_DEGRE_LAT_DESSIN;
    }

    /// Combien de degrés de longitude valent `kilometres` à cette `latitude`, à la précision du
    /// **dessin**. Un degré de longitude rétrécit avec le cosinus de la latitude.
    public static double degresDeLongitudePour(double kilometres, double latitude) {
        return kilometres / (KM_PAR_DEGRE_LAT_DESSIN * Math.cos(Math.toRadians(latitude)));
    }
}

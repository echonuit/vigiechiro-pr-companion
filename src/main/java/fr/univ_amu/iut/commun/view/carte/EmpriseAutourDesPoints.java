package fr.univ_amu.iut.commun.view.carte;

import java.util.List;
import java.util.Optional;

/// Fournisseur de repli (#152) : emprise d'**au moins 2 km**, centrée sur la **boîte englobante** des
/// points géolocalisés du carré et **contenant tous ces points**. Honnête en l'absence du carroyage
/// officiel : le carré n'est pas calé sur la grille nationale, mais il est ancré sur la position **réelle**
/// de ses points et les englobe toujours. Sans aucun point géolocalisé, renvoie vide (carré non tracé).
///
/// On centre sur le **milieu min/max** (et non la moyenne) et le demi-côté vaut au moins le demi-écart des
/// points : ainsi tout point reste dans l'emprise même réparti de façon **asymétrique** (sinon un point
/// excentré pouvait sortir d'un carré 2 km centré sur la moyenne). Pour un vrai carré (points dans 2 km)
/// l'emprise reste 2 km ; elle ne s'élargit que sur une donnée anormale.
///
/// Conversion km → degrés : 1° de latitude ≈ 111 km ; 1° de longitude ≈ 111 km × cos(latitude).
public final class EmpriseAutourDesPoints implements FournisseurEmpriseCarre {

    /// Demi-côté **minimal** du carré, en kilomètres (carré Vigie-Chiro de 2 km de côté).
    private static final double DEMI_COTE_KM = 1.0;

    private static final double KM_PAR_DEGRE_LAT = 111.0;

    @Override
    public Optional<EmpriseCarre> emprise(String numeroCarre, List<PointGeo> pointsDuCarre) {
        List<PointGeo> geolocalises = pointsDuCarre.stream()
                .filter(EmpriseAutourDesPoints::estGeolocalise)
                .toList();
        if (geolocalises.isEmpty()) {
            return Optional.empty();
        }
        var lat = geolocalises.stream().mapToDouble(PointGeo::latitude).summaryStatistics();
        var lon = geolocalises.stream().mapToDouble(PointGeo::longitude).summaryStatistics();
        double latCentre = (lat.getMin() + lat.getMax()) / 2.0;
        double lonCentre = (lon.getMin() + lon.getMax()) / 2.0;
        // Demi-côté = max(demi-côté 2 km, demi-écart des points) → l'emprise contient toujours les points.
        double demiLat = Math.max(DEMI_COTE_KM / KM_PAR_DEGRE_LAT, (lat.getMax() - lat.getMin()) / 2.0);
        double demiLonMin = DEMI_COTE_KM / (KM_PAR_DEGRE_LAT * Math.cos(Math.toRadians(latCentre)));
        double demiLon = Math.max(demiLonMin, (lon.getMax() - lon.getMin()) / 2.0);
        return Optional.of(
                new EmpriseCarre(latCentre - demiLat, lonCentre - demiLon, latCentre + demiLat, lonCentre + demiLon));
    }

    /// Un point est exploitable s'il a des coordonnées finies (pas de NaN issu d'un GPS manquant).
    private static boolean estGeolocalise(PointGeo point) {
        return Double.isFinite(point.latitude()) && Double.isFinite(point.longitude());
    }
}

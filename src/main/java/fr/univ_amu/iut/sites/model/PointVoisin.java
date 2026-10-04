package fr.univ_amu.iut.sites.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/// Le point d'un site **le plus proche** d'une position, quand il est à 40 m au plus (#5688).
///
/// 40 m est le rayon que le portail Vigie-Chiro utilise pour rattacher un point à un nom : deux points
/// plus proches que cela sont, pour lui, le même endroit. Le voisinage se **signale**, il n'interdit
/// rien : l'observateur peut vouloir deux points voisins.
public final class PointVoisin {

    /// Rayon de voisinage, en mètres, bornes incluses.
    public static final long RAYON_METRES = 40;

    /// Rayon moyen de la Terre, en mètres : à cette échelle, la sphère suffit.
    private static final double RAYON_TERRE_METRES = 6_371_000.0;

    private PointVoisin() {}

    /// Un point voisin et sa distance, arrondie au mètre.
    public record Voisin(PointDEcoute point, long metres) {}

    /// Le plus proche des points **positionnés**, s'il est dans le rayon ; vide sinon.
    public static Optional<Voisin> lePlusProche(double latitude, double longitude, Collection<PointDEcoute> points) {
        return points.stream()
                .filter(point -> point.latitude() != null && point.longitude() != null)
                .map(point -> new Voisin(
                        point, Math.round(distance(latitude, longitude, point.latitude(), point.longitude()))))
                .filter(voisin -> voisin.metres() <= RAYON_METRES)
                .min(Comparator.comparingLong(Voisin::metres));
    }

    /// Distance orthodromique entre deux positions, en mètres (formule de haversine).
    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);
        return 2 * RAYON_TERRE_METRES * Math.asin(Math.sqrt(a));
    }
}

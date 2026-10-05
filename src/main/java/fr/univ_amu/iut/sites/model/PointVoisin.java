package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.model.DistanceGeo;
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

    private PointVoisin() {}

    /// Un point voisin et sa distance, arrondie au mètre.
    public record Voisin(PointDEcoute point, long metres) {

        /// Ce qu'on en dit, à l'écran comme en ligne de commande (#5837) : une seule phrase, pour que
        /// les deux surfaces ne puissent pas diverger.
        public String avertissement() {
            return "Le point " + point.code() + " de ce site est à " + metres
                    + " m de cette position. Vérifiez que vous ne le créez pas une seconde fois.";
        }
    }

    /// Le plus proche des points **positionnés**, s'il est dans le rayon ; vide sinon.
    public static Optional<Voisin> lePlusProche(double latitude, double longitude, Collection<PointDEcoute> points) {
        return points.stream()
                .filter(point -> point.latitude() != null && point.longitude() != null)
                .map(point -> new Voisin(
                        point, Math.round(DistanceGeo.metresEntre(latitude, longitude, point.latitude(), point.longitude()))))
                .filter(voisin -> voisin.metres() <= RAYON_METRES)
                .min(Comparator.comparingLong(Voisin::metres));
    }
}

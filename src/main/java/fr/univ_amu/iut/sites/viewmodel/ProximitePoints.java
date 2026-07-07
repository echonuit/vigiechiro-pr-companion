package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.model.DistanceGeo;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import java.util.List;

/// Calcul de **proximité entre points d'écoute** d'un même site (#154). Extrait de [SiteDetailViewModel]
/// pour garder le ViewModel sous le seuil de cohésion (PMD `GodClass`) : la géométrie n'est pas de l'état
/// de présentation. Repose sur [DistanceGeo] (Haversine).
final class ProximitePoints {

    private ProximitePoints() {}

    /// Distance (m) du point **géolocalisé le plus proche** de `point` parmi `autres` (les points du même
    /// site), ou `null` si `point` n'a pas de GPS ou qu'aucun autre point géolocalisé n'existe.
    static Double distanceAuPlusProche(PointDEcoute point, List<PointDEcoute> autres) {
        if (point.latitude() == null || point.longitude() == null) {
            return null;
        }
        double minimum = Double.MAX_VALUE;
        for (PointDEcoute autre : autres) {
            if (autre.id().equals(point.id()) || autre.latitude() == null || autre.longitude() == null) {
                continue;
            }
            minimum = Math.min(
                    minimum,
                    DistanceGeo.metresEntre(point.latitude(), point.longitude(), autre.latitude(), autre.longitude()));
        }
        return minimum == Double.MAX_VALUE ? null : minimum;
    }
}

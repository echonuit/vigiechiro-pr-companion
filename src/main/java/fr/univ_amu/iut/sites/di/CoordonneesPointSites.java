package fr.univ_amu.iut.sites.di;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.CoordonneesPoint;
import fr.univ_amu.iut.commun.model.PositionGeo;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import java.util.Optional;

/// Implémentation du port socle [CoordonneesPoint] par la feature `sites`, qui détient les points
/// d'écoute et leurs coordonnées via [PointDao].
///
/// Ce pont permet aux autres features de lire le GPS d'un point sans dépendre de `sites` (elles ne
/// voient que le port `commun`) : c'est le côté « fournisseur » de l'inversion de dépendance qui
/// évite le cycle `passage ↔ sites`. Branché sur le port par [SitesModule] via un `OptionalBinder`.
final class CoordonneesPointSites implements CoordonneesPoint {

    private final PointDao pointDao;

    @Inject
    CoordonneesPointSites(PointDao pointDao) {
        this.pointDao = pointDao;
    }

    @Override
    public Optional<PositionGeo> pour(Long idPoint) {
        if (idPoint == null) {
            return Optional.empty();
        }
        return pointDao.findById(idPoint)
                .filter(point -> point.latitude() != null && point.longitude() != null)
                .map(point -> new PositionGeo(point.latitude(), point.longitude()));
    }
}

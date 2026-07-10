package fr.univ_amu.iut.sites.di;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.InfosPoint;
import fr.univ_amu.iut.commun.model.ReferentielPoint;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import java.util.Optional;

/// Implémentation du port socle [ReferentielPoint] par la feature `sites`, qui détient les points d'écoute
/// via [PointDao]. Pendant de [CoordonneesPointSites] : côté « fournisseur » de l'inversion de dépendance
/// qui évite le cycle `passage ↔ sites`. Branché sur le port par [SitesModule] via un `OptionalBinder`.
final class InfosPointSites implements ReferentielPoint {

    private final PointDao pointDao;

    @Inject
    InfosPointSites(PointDao pointDao) {
        this.pointDao = pointDao;
    }

    @Override
    public Optional<InfosPoint> pour(Long idPoint) {
        if (idPoint == null) {
            return Optional.empty();
        }
        return pointDao.findById(idPoint).map(point -> new InfosPoint(point.code(), point.idSite()));
    }
}

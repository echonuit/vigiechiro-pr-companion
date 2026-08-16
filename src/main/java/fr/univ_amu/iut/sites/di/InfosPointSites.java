package fr.univ_amu.iut.sites.di;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.InfosPoint;
import fr.univ_amu.iut.commun.model.ReferentielPoint;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.util.Optional;

/// Implémentation du port socle [ReferentielPoint] par la feature `sites`, qui détient les points d'écoute
/// via [PointDao]. Pendant de [CoordonneesPointSites] : côté « fournisseur » de l'inversion de dépendance
/// qui évite le cycle `passage ↔ sites`. Branché sur le port par [SitesModule] via un `OptionalBinder`.
final class InfosPointSites implements ReferentielPoint {

    private final PointDao pointDao;

    /// Pour le **numéro de carré** du site (#3854) : il ne sert pas à déposer, mais à conseiller quand le
    /// site n'est pas rattaché. Un site absent ne fait pas échouer l'identité - le numéro reste `null`,
    /// et l'appelant retombe sur son conseil générique.
    private final SiteDao siteDao;

    @Inject
    InfosPointSites(PointDao pointDao, SiteDao siteDao) {
        this.pointDao = pointDao;
        this.siteDao = siteDao;
    }

    @Override
    public Optional<InfosPoint> pour(Long idPoint) {
        if (idPoint == null) {
            return Optional.empty();
        }
        return pointDao.findById(idPoint)
                .map(point -> new InfosPoint(point.code(), point.idSite(), numeroCarreDe(point.idSite())));
    }

    private String numeroCarreDe(Long idSite) {
        return siteDao.findById(idSite).map(Site::numeroCarre).orElse(null);
    }
}

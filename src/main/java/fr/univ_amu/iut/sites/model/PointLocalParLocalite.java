package fr.univ_amu.iut.sites.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.PointParLocalite;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.util.Objects;
import java.util.Optional;

/// Implémentation du port [PointParLocalite] (#1305) : `sites` possède les carrés et leurs points, elle
/// sait donc résoudre « carré 130711, localité Z41 » en identifiant de point local. L'inversion évite le
/// cycle que créerait `passage → sites`.
///
/// Résolution **stricte** : le carré doit exister localement, et porter un point de ce code. Un `Optional`
/// vide dit « je ne connais pas ce point » — la reconstruction (#1305) refusera alors explicitement,
/// plutôt que d'inventer un rattachement (une nuit rattachée au mauvais point est une donnée fausse).
public final class PointLocalParLocalite implements PointParLocalite {

    private final SiteDao siteDao;
    private final PointDao pointDao;

    @Inject
    public PointLocalParLocalite(SiteDao siteDao, PointDao pointDao) {
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
    }

    @Override
    public Optional<Long> pour(String numeroCarre, String codePoint) {
        if (numeroCarre == null || codePoint == null) {
            return Optional.empty();
        }
        return siteDao.findAll().stream()
                .filter(site -> numeroCarre.equals(site.numeroCarre()))
                .flatMap(site -> pointDao.findBySite(site.id()).stream())
                .filter(point -> codePoint.equalsIgnoreCase(point.code()))
                .map(PointDEcoute::id)
                .findFirst();
    }
}

package fr.univ_amu.iut.sites.di;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.model.CommunePoint;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import java.util.Optional;

/// Implémentation du port socle [CommunePoint] par la feature `sites`, qui détient la table latérale
/// `point_commune` via [PointCommuneDao] (#2791, #3442).
///
/// Ce pont permet aux autres features de lire la commune d'un point - donc son territoire, donc son
/// fuseau - sans dépendre de `sites` : c'est le côté « fournisseur » de l'inversion qui évite le cycle
/// `passage ↔ sites`. Branché sur le port par [SitesModule] via un `OptionalBinder`, exactement comme
/// [CoordonneesPointSites].
final class CommunePointSites implements CommunePoint {

    private final PointCommuneDao communesDao;

    @Inject
    CommunePointSites(PointCommuneDao communesDao) {
        this.communesDao = communesDao;
    }

    @Override
    public Optional<Commune> pour(Long idPoint) {
        if (idPoint == null) {
            return Optional.empty();
        }
        return communesDao.pour(idPoint);
    }
}

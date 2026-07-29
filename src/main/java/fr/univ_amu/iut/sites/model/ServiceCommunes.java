package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.model.PositionGeo;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.ResolveurCommune;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// Tient à jour la **commune** des points d'écoute (#2791) : dérivée une fois du GPS via un
/// [ResolveurCommune] (API Géo en production), persistée en table latérale `point_commune`.
///
/// Deux gestes, tous deux **best-effort** côté réseau (le résolveur ne lève jamais) :
///
/// - [#mettreAJour] recalcule la commune d'UN point depuis son état courant - à appeler après une
///   création ou un changement de GPS. L'ancienne valeur est d'abord effacée : un GPS qui a bougé
///   rend la commune mémorisée périmée, et une commune absente vaut mieux qu'une commune fausse.
/// - [#rattraper] comble les points **en attente** (GPS présent, commune absente) : créations hors
///   ligne, points rapatriés avant V37. Il ne retouche jamais une commune déjà résolue.
public class ServiceCommunes {

    private final PointDao pointDao;
    private final PointCommuneDao communeDao;
    private final ResolveurCommune resolveur;

    public ServiceCommunes(PointDao pointDao, PointCommuneDao communeDao, ResolveurCommune resolveur) {
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.communeDao = Objects.requireNonNull(communeDao, "communeDao");
        this.resolveur = Objects.requireNonNull(resolveur, "resolveur");
    }

    /// Recalcule la commune du point depuis son état courant : efface l'éventuelle valeur mémorisée,
    /// puis résout si le point a un GPS. Renvoie la commune résolue, ou vide (point sans GPS, hors
    /// ligne, position hors référentiel) - le rattrapage comblera.
    ///
    /// @throws RegleMetierException si le point est introuvable
    public Optional<Commune> mettreAJour(Long idPoint) {
        PointDEcoute point = pointDao.findById(idPoint)
                .orElseThrow(() -> new RegleMetierException("Point introuvable : " + idPoint));
        communeDao.effacer(point.id());
        return resoudreEtMemoriser(point);
    }

    /// Comble les points **en attente** : GPS présent, commune absente. Ne retouche jamais une
    /// commune déjà résolue (les GPS ne bougent que par [#mettreAJour], qui rafraîchit au passage).
    ///
    /// @return le bilan chiffré : points en attente examinés, communes résolues
    public BilanCommunes rattraper() {
        Set<Long> dejaResolus = communeDao.idsResolus();
        int enAttente = 0;
        int resolues = 0;
        for (PointDEcoute point : pointDao.findAll()) {
            if (point.latitude() == null || point.longitude() == null || dejaResolus.contains(point.id())) {
                continue;
            }
            enAttente++;
            if (resoudreEtMemoriser(point).isPresent()) {
                resolues++;
            }
        }
        return new BilanCommunes(enAttente, resolues);
    }

    /// La commune mémorisée du point, ou vide si non résolue.
    public Optional<Commune> pour(long idPoint) {
        return communeDao.pour(idPoint);
    }

    private Optional<Commune> resoudreEtMemoriser(PointDEcoute point) {
        if (point.latitude() == null || point.longitude() == null) {
            return Optional.empty();
        }
        Optional<Commune> commune = resolveur.resoudre(new PositionGeo(point.latitude(), point.longitude()));
        commune.ifPresent(resolue -> communeDao.definir(point.id(), resolue));
        return commune;
    }

    /// Bilan d'un [#rattraper] : `enAttente` points examinés (GPS présent, commune absente), dont
    /// `resolues` désormais résolus. Le reste (`restantes()`) attend un prochain rattrapage.
    public record BilanCommunes(int enAttente, int resolues) {

        /// Points toujours sans commune après le rattrapage (hors ligne, position hors référentiel).
        public int restantes() {
            return enAttente - resolues;
        }
    }
}

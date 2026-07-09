package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.util.Objects;

/// Marque (ou retire) le drapeau **douteux** (#160) d'une observation : « je l'ai écoutée mais je doute, à
/// repasser ». Orthogonal au taxon et au statut de revue (comme la référence), et **distinct** de « à
/// revoir » (statut non touché = pas encore vue).
///
/// Classe **dédiée** (et non une n-ième méthode de [ServiceValidation], déjà au plafond de cohésion), sur le
/// modèle de [ValidationManuelle] : un seul champ modifié sur le record immuable, réécrit via `update`.
public class MarquageDouteux {

    private final ObservationDao observationDao;

    public MarquageDouteux(ObservationDao observationDao) {
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
    }

    /// Marque (`douteux = true`) ou retire (`false`) le drapeau douteux de l'observation `idObservation`.
    ///
    /// @param idObservation identifiant de l'observation
    /// @param douteux `true` pour marquer douteuse, `false` pour retirer le drapeau
    /// @return l'observation relue, à jour
    /// @throws RegleMetierException si l'observation est introuvable
    public Observation marquer(Long idObservation, boolean douteux) {
        Observation mise = observationDao
                .findById(idObservation)
                .orElseThrow(() -> new RegleMetierException("Observation introuvable : " + idObservation))
                .avecDouteux(douteux);
        observationDao.update(mise);
        return mise;
    }
}

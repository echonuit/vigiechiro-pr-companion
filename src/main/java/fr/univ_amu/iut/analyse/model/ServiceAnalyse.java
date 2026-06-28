package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.util.List;
import java.util.Objects;

/// Service de la feature **`analyse`** (prisme « Espèces & biodiversité ») : expose la **lecture
/// transverse** des observations, agrégées par **espèce** ou par **carré** (richesse spécifique), pour
/// répondre à « quelles espèces, où, combien ». Pur **model** (aucune dépendance IHM/navigation).
///
/// S'appuie sur les projections de [ObservationDao] (feature `validation`) — comme [ServiceMultisite]
/// s'appuie sur les DAO de `sites`/`passage` — sans redéfinir l'agrégation. Le **filtre de statut** de
/// revue (`null` = toutes les observations) est appliqué à la source (SQL).
public class ServiceAnalyse {

    private final ObservationDao observationDao;

    public ServiceAnalyse(ObservationDao observationDao) {
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
    }

    /// Inventaire **par espèce** des observations de l'utilisateur, filtré par `statut` (`null` = tous).
    public List<EspeceAgregee> inventaireParEspece(String idUtilisateur, StatutObservation statut) {
        return observationDao.inventaireParEspece(idUtilisateur, statut);
    }

    /// Inventaire **par carré** (richesse spécifique) des observations de l'utilisateur, filtré par
    /// `statut` (`null` = tous).
    public List<CarreEspeces> inventaireParCarre(String idUtilisateur, StatutObservation statut) {
        return observationDao.inventaireParCarre(idUtilisateur, statut);
    }
}

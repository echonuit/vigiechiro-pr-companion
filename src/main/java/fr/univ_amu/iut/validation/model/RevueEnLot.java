package fr.univ_amu.iut.validation.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/// Actions de revue **en lot** (#479) : valider / corriger / marquer référence sur une **liste**
/// d'observations, en **une transaction atomique** (tout réussit ou tout est annulé). Extrait de
/// [ServiceValidation] (qui reste dédié à l'import et à la revue **unitaire**) pour tenir les seuils de
/// cohésion PMD.
///
/// **Atomicité sans verrou SQLite** : les lectures (chargement des observations à transformer) sont faites
/// **avant** la transaction (auto-commit) ; seules les **écritures** sont groupées atomiquement via
/// [ObservationDao#updateTout]. La validation en lot est en **mode Activité** (aucune propagation
/// Inventaire) : le lot traite **exactement** les lignes visées.
public class RevueEnLot {

    /// Fin de citation `« … ».` des messages d'erreur métier.
    private static final String GUILLEMET_FERMANT = " ».";

    private final ObservationDao observationDao;
    private final TaxonDao taxonDao;

    @Inject
    public RevueEnLot(ObservationDao observationDao, TaxonDao taxonDao) {
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
        this.taxonDao = Objects.requireNonNull(taxonDao, "taxonDao");
    }

    /// Valide en lot (R15) : chaque observation retient sa proposition Tadarida en `manuel`. Renvoie le
    /// nombre validé.
    public int valider(List<Long> ids) {
        return ecrire(ids, RevueEnLot::valideeManuellement);
    }

    /// Corrige en lot (R16) : retient le taxon `codeTaxonObservateur` (qui doit exister) en `manuel` sur
    /// toutes les observations. Renvoie le nombre corrigé.
    ///
    /// @throws RegleMetierException si le taxon est inconnu
    public int corriger(List<Long> ids, String codeTaxonObservateur) {
        if (codeTaxonObservateur == null
                || taxonDao.findById(codeTaxonObservateur).isEmpty()) {
            throw new RegleMetierException("Taxon observateur inconnu : « " + codeTaxonObservateur + GUILLEMET_FERMANT);
        }
        return ecrire(ids, o -> o.avecObservateur(codeTaxonObservateur, null, ModeValidation.MANUEL));
    }

    /// **Marque ou retire** en lot les observations du corpus de référence. Renvoie le nombre traité.
    public int marquerReference(List<Long> ids, boolean reference) {
        return ecrire(ids, o -> o.avecReference(reference));
    }

    /// **Marque ou retire** en lot le drapeau **douteux** (#160) des observations. Renvoie le nombre traité.
    public int marquerDouteux(List<Long> ids, boolean douteux) {
        return ecrire(ids, o -> o.avecDouteux(douteux));
    }

    /// Charge (auto-commit), transforme (pur) puis écrit en **une transaction** ([ObservationDao#updateTout]).
    private int ecrire(List<Long> ids, UnaryOperator<Observation> transformation) {
        List<Observation> mises =
                ids.stream().map(this::charger).map(transformation).toList();
        observationDao.updateTout(mises);
        return mises.size();
    }

    private Observation charger(Long idObservation) {
        return observationDao
                .findById(idObservation)
                .orElseThrow(() -> new RegleMetierException("Observation introuvable : " + idObservation));
    }

    /// Observation **validée à la main** (R15, sans I/O) : retient la proposition Tadarida en `manuel`, avec
    /// la probabilité observateur si présente, sinon Tadarida, sinon `1.0`.
    private static Observation valideeManuellement(Observation observation) {
        Double prob = observation.probObservateur();
        if (prob == null) {
            prob = observation.probTadarida() != null ? observation.probTadarida() : 1.0;
        }
        return observation.avecObservateur(observation.taxonTadarida(), prob, ModeValidation.MANUEL);
    }
}

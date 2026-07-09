package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.util.Objects;
import java.util.Optional;

/// Validation **manuelle** d'une séquence **non identifiée** (présente sur disque mais absente du CSV
/// Tadarida) : crée — ou met à jour — une **observation manuelle** portant le taxon de l'observateur,
/// **sans** taxon ni jeu de résultats Tadarida (`taxon_tadarida`/`results_id` nuls, autorisés depuis la
/// migration V13), en mode `manuel`.
///
/// Classe **dédiée** (et non une n-ième méthode de [ServiceValidation], déjà au plafond de cohésion) : ce
/// cas d'usage — valider un son que Tadarida n'a pas classé — est autonome et se teste isolément.
public class ValidationManuelle {

    private final ObservationDao observationDao;
    private final TaxonDao taxonDao;

    public ValidationManuelle(ObservationDao observationDao, TaxonDao taxonDao) {
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
        this.taxonDao = Objects.requireNonNull(taxonDao, "taxonDao");
    }

    /// Valide la séquence `idSequence` avec le taxon `codeTaxonObservateur`. Au plus **une** observation
    /// manuelle par séquence : re-valider la même séquence **remplace** le taxon plutôt que d'empiler des
    /// observations. L'existence de la séquence est garantie par la FK à l'insert.
    ///
    /// @param idSequence séquence d'écoute à valider
    /// @param codeTaxonObservateur taxon retenu par l'observateur (doit exister en base)
    /// @return l'observation manuelle créée ou mise à jour
    /// @throws RegleMetierException si le taxon est inconnu
    public Observation valider(long idSequence, String codeTaxonObservateur) {
        if (codeTaxonObservateur == null
                || taxonDao.findById(codeTaxonObservateur).isEmpty()) {
            throw new RegleMetierException("Taxon observateur inconnu : « " + codeTaxonObservateur + " ».");
        }
        Optional<Observation> existante = observationDao.observationManuelleDeLaSequence(idSequence);
        if (existante.isPresent()) {
            Observation mise = existante.get().avecObservateur(codeTaxonObservateur, null, ModeValidation.MANUEL);
            observationDao.update(mise);
            return mise;
        }
        Observation manuelle = new Observation(
                null, // id
                idSequence,
                null, // debutS
                null, // finS
                null, // frequenceMedianeKHz
                null, // taxonTadarida : aucune proposition Tadarida
                null, // probTadarida
                null, // taxonAutreTadarida
                codeTaxonObservateur,
                null, // probObservateur
                null, // commentaire
                false, // reference
                ModeValidation.MANUEL,
                null, // idResultats : aucun jeu Tadarida
                false); // douteux : une séquence tout juste validée n'est pas marquée douteuse
        return observationDao.insert(manuelle);
    }
}

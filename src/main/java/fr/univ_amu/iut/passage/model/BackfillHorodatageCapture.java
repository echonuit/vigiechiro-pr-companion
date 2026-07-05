package fr.univ_amu.iut.passage.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import java.time.LocalDateTime;
import java.util.Optional;

/// Backfill **applicatif** de l'horodatage de capture (#530) : renseigne `recorded_at` des séquences déjà
/// importées avant la colonne (migration V09) en **re-parsant leur nom de fichier** (`_AAAAMMJJ_HHMMSS`),
/// plus fiable qu'un `substr` SQLite. Déclenché au démarrage après la migration.
///
/// **Idempotent** : ne cible que les séquences **sans** horodatage ([SequenceDao#sansHorodatage()]) et ne
/// remplit que celles dont le nom est effectivement horodaté (les noms non standard / de test restent à
/// `null`, sans re-traitement coûteux au-delà d'une requête).
public final class BackfillHorodatageCapture {

    private final SequenceDao sequenceDao;

    @Inject
    public BackfillHorodatageCapture(SequenceDao sequenceDao) {
        this.sequenceDao = sequenceDao;
    }

    /// Remplit l'horodatage des séquences qui n'en ont pas et dont le nom est horodaté. Retourne le nombre
    /// de séquences effectivement renseignées.
    public int remplir() {
        int remplis = 0;
        for (SequenceDEcoute sequence : sequenceDao.sansHorodatage()) {
            Optional<LocalDateTime> horodatage = Prefixe.horodatageDe(sequence.nomFichier());
            if (horodatage.isPresent()) {
                sequenceDao.majHorodatage(sequence.id(), horodatage.get());
                remplis++;
            }
        }
        return remplis;
    }
}

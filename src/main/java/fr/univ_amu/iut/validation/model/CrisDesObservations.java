package fr.univ_amu.iut.validation.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.passage.model.CriAttendu;
import fr.univ_amu.iut.passage.model.CrisAttendus;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.util.List;
import java.util.Objects;

/// Implémentation du port [CrisAttendus] (#1302) : projette les **observations** d'une séquence en
/// cris attendus (instants + fréquence médiane), la matière de la vérification acoustique (#1309).
///
/// C'est la feature `validation` qui la fournit — elle possède les observations et dépend déjà de
/// `passage` : l'inversion évite le cycle que créerait `passage → validation`.
///
/// Les observations **incomplètes** (sans instants ou sans fréquence médiane) sont écartées : elles
/// ne décrivent aucun cri localisable, et une fenêtre d'analyse ne se déduit pas d'un `null`. La
/// fréquence médiane est persistée en **kHz** (`median_freq_khz`, cf. V07) ; la vérification
/// acoustique travaille en Hz réels.
public final class CrisDesObservations implements CrisAttendus {

    private static final double HZ_PAR_KHZ = 1_000.0;

    private final ObservationDao observationDao;

    @Inject
    public CrisDesObservations(ObservationDao observationDao) {
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
    }

    @Override
    public List<CriAttendu> pour(Long idSequence) {
        if (idSequence == null) {
            return List.of();
        }
        return observationDao.findBySequence(idSequence).stream()
                .filter(CrisDesObservations::localisable)
                .map(observation -> new CriAttendu(
                        observation.debutS(), observation.finS(), observation.frequenceMedianeKHz() * HZ_PAR_KHZ))
                .toList();
    }

    /// Un cri est **localisable** s'il porte un début, une fin et une fréquence médiane : sans quoi
    /// il n'y a ni fenêtre temporelle ni bande à sonder.
    private static boolean localisable(Observation observation) {
        return observation.debutS() != null && observation.finS() != null && observation.frequenceMedianeKHz() != null;
    }
}

package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Fusion des sons d'un passage dans la vue audio (#audio) : la source [SourceObservations.ParPassage] réunit
/// les **observations Tadarida** et les **séquences non identifiées** en une seule liste. Le piège testé ici est
/// le **doublon** : une séquence validée à la main figure à la fois dans `lignesAudioDuPassage` (elle a désormais
/// une observation) et dans `lignesAudioNonIdentifiees` (elle n'a jamais eu de proposition Tadarida, donc y
/// reste). La restriction `taxonTadarida != null` sur les observations Tadarida la retire de la première branche
/// pour ne la garder que dans la seconde — les deux ensembles se partitionnent sur la nullité de `taxonTadarida`.
class ResolveurSourceAudioTest {

    private static final long ID_PASSAGE = 7L;
    private static final ContextePassage CONTEXTE =
            new ContextePassage(ID_PASSAGE, 1, new ContexteSite("640380", "A1", "Mon site"));

    @Test
    @DisplayName("ParPassage fusionne obs. Tadarida + séquences non identifiées, sans doublon de l'obs. manuelle")
    void par_passage_fusionne_sans_doublon() {
        ServiceValidation service = mock(ServiceValidation.class);
        // Observations du passage : une vraie proposition Tadarida + une observation MANUELLE (taxon Tadarida nul).
        LigneObservationAudio tadarida = ligne(100L, 11L, "pippip", StatutObservation.NON_TOUCHEE);
        LigneObservationAudio manuelle = ligne(200L, 12L, null, StatutObservation.CORRIGEE);
        when(service.lignesAudioDuPassage(ID_PASSAGE)).thenReturn(List.of(tadarida, manuelle));
        // Séquences non identifiées : une pas encore validée (id d'observation nul) + la même séquence manuelle.
        LigneObservationAudio nonValidee = ligne(null, 13L, null, StatutObservation.NON_TOUCHEE);
        when(service.lignesAudioNonIdentifiees(ID_PASSAGE)).thenReturn(List.of(nonValidee, manuelle));

        List<LigneObservationAudio> lignes =
                new ResolveurSourceAudio(service).lignes(new SourceObservations.ParPassage(CONTEXTE));

        // La Tadarida est gardée ; l'obs. manuelle n'apparaît qu'UNE fois (via les non identifiées, filtrée de la
        // branche Tadarida) ; la séquence non validée est présente.
        assertThat(lignes).containsExactly(tadarida, nonValidee, manuelle);
    }

    private static LigneObservationAudio ligne(
            Long idObservation, long idSequence, String taxonTadarida, StatutObservation statut) {
        return new LigneObservationAudio(
                idObservation,
                idSequence,
                ID_PASSAGE,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Site",
                taxonTadarida,
                taxonTadarida == null ? null : 0.9,
                null,
                null,
                statut,
                false,
                null,
                45,
                null,
                taxonTadarida,
                taxonTadarida == null ? null : "Chiroptères",
                "f" + idSequence + ".wav",
                0.2,
                0.4,
                null,
                false);
    }
}

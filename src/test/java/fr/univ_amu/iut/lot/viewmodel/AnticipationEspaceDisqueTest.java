package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Bornes de l'anticipation d'espace disque (#1890, clôture de l'EPIC #1870).
///
/// Les tests de [LotViewModel] couvraient les deux cas francs - il manque de la place, il y en a
/// largement - mais **aucune borne** : PIT a laissé survivre les mutants qui décalent `disponible > 0`
/// en `>= 0` et `disponible < requis` en `<=`. Or ces deux bornes portent chacune une décision
/// délibérée : un disque illisible ne bloque pas, et « exactement assez » n'est pas « pas assez ».
class AnticipationEspaceDisqueTest {

    private static final long GIGA = 1_000_000_000L;

    private final ServiceLot service = mock(ServiceLot.class);
    private final AnticipationEspaceDisque anticipation = new AnticipationEspaceDisque(service);

    private static EtatLot etat(StatutWorkflow statut, Long volume) {
        return new EtatLot(statut, "/ws/session-42", 2, volume, List.of(), null);
    }

    private void disqueEtBesoin(long disponible, long requis) {
        when(service.espaceDisqueDisponible("/ws/session-42")).thenReturn(disponible);
        when(service.estimationTailleDepotOctets(GIGA)).thenReturn(requis);
    }

    @Test
    @DisplayName("Exactement la place nécessaire : on ne bloque pas")
    void exactement_la_place_ne_bloque_pas() {
        disqueEtBesoin(4 * GIGA, 4 * GIGA);

        anticipation.majDepuis(etat(StatutWorkflow.PRET_A_DEPOSER, GIGA));

        assertThat(anticipation.suffisantProperty().get())
                .as("« exactement assez » n'est pas « pas assez » : la borne est stricte")
                .isTrue();
        assertThat(anticipation.raisonProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("Un octet de moins que nécessaire : on bloque")
    void un_octet_de_moins_bloque() {
        disqueEtBesoin(4 * GIGA - 1, 4 * GIGA);

        anticipation.majDepuis(etat(StatutWorkflow.PRET_A_DEPOSER, GIGA));

        assertThat(anticipation.suffisantProperty().get()).isFalse();
        assertThat(anticipation.raisonProperty().get()).isNotEmpty();
    }

    @Test
    @DisplayName("Espace disponible nul : disque illisible, on ne bloque pas plutôt que de bloquer à tort")
    void disque_illisible_ne_bloque_pas() {
        // 0 n'est pas « le disque est plein » mais « on n'a pas su lire » : bloquer sur cette valeur
        // interdirait la génération sur toute machine où la mesure échoue.
        disqueEtBesoin(0L, 4 * GIGA);

        anticipation.majDepuis(etat(StatutWorkflow.PRET_A_DEPOSER, GIGA));

        assertThat(anticipation.suffisantProperty().get()).isTrue();
    }

    @Test
    @DisplayName("Génération non pertinente (nuit pas encore prête) : on ne bloque pas, même sans place")
    void generation_non_pertinente_ne_bloque_pas() {
        disqueEtBesoin(1L, 4 * GIGA);

        anticipation.majDepuis(etat(StatutWorkflow.VERIFIE, GIGA));

        assertThat(anticipation.suffisantProperty().get())
                .as("annoncer un manque de place avant que la génération ait un sens serait du bruit")
                .isTrue();
    }

    @Test
    @DisplayName("Volume inconnu : rien à estimer, on ne bloque pas")
    void volume_inconnu_ne_bloque_pas() {
        when(service.espaceDisqueDisponible("/ws/session-42")).thenReturn(1L);

        anticipation.majDepuis(etat(StatutWorkflow.PRET_A_DEPOSER, null));

        assertThat(anticipation.suffisantProperty().get()).isTrue();
    }

    @Test
    @DisplayName("Réinitialiser repose l'état neutre : suffisant, sans raison")
    void reinitialiser_repose_l_etat_neutre() {
        disqueEtBesoin(1L, 4 * GIGA);
        anticipation.majDepuis(etat(StatutWorkflow.PRET_A_DEPOSER, GIGA));
        assertThat(anticipation.suffisantProperty().get()).isFalse();

        anticipation.reinitialiser();

        assertThat(anticipation.suffisantProperty().get()).isTrue();
        assertThat(anticipation.raisonProperty().get())
                .as("une raison qui survit au changement de passage parlerait du passage précédent")
                .isEmpty();
    }
}

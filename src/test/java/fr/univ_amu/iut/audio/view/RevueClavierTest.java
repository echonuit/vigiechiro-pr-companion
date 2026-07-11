package fr.univ_amu.iut.audio.view;

import static fr.univ_amu.iut.validation.model.StatutObservation.NON_TOUCHEE;
import static fr.univ_amu.iut.validation.model.StatutObservation.VALIDEE;
import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Logique « aller à la prochaine À revoir » de la revue au clavier (#478) : recherche circulaire du
/// prochain statut non touché après le départ, testée en pur (sans IHM).
class RevueClavierTest {

    private static LigneObservationAudio ligne(long id, StatutObservation statut) {
        return new LigneObservationAudio(
                id, id, 1L, 1, null, null, null, null, "Pippip", null, null, null, statut, false, null, null, null,
                null, null, null, null, null, null, null, false);
    }

    @Test
    @DisplayName("Prochaine À revoir : le premier NON_TOUCHEE strictement après le départ")
    void prochaine_apres_le_depart() {
        List<LigneObservationAudio> lignes =
                List.of(ligne(1, VALIDEE), ligne(2, NON_TOUCHEE), ligne(3, VALIDEE), ligne(4, NON_TOUCHEE));

        assertThat(RevueClavier.indexProchaineARevoir(lignes, 0)).isEqualTo(1);
        assertThat(RevueClavier.indexProchaineARevoir(lignes, 1)).isEqualTo(3);
    }

    @Test
    @DisplayName("Sans sélection (départ -1), la recherche démarre au début")
    void sans_selection_demarre_au_debut() {
        List<LigneObservationAudio> lignes = List.of(ligne(1, VALIDEE), ligne(2, NON_TOUCHEE), ligne(3, VALIDEE));

        assertThat(RevueClavier.indexProchaineARevoir(lignes, -1)).isEqualTo(1);
    }

    @Test
    @DisplayName("En fin de liste, la recherche boucle vers le début")
    void boucle_vers_le_debut() {
        List<LigneObservationAudio> lignes =
                List.of(ligne(1, VALIDEE), ligne(2, NON_TOUCHEE), ligne(3, VALIDEE), ligne(4, VALIDEE));

        // Depuis l'index 3 (dernier), la seule À revoir est l'index 1 → on boucle.
        assertThat(RevueClavier.indexProchaineARevoir(lignes, 3)).isEqualTo(1);
    }

    @Test
    @DisplayName("Aucune À revoir → -1 ; liste vide → -1")
    void aucune_a_revoir_ou_liste_vide() {
        List<LigneObservationAudio> toutesRevues = List.of(ligne(1, VALIDEE), ligne(2, VALIDEE));

        assertThat(RevueClavier.indexProchaineARevoir(toutesRevues, 0)).isEqualTo(-1);
        assertThat(RevueClavier.indexProchaineARevoir(List.of(), -1)).isEqualTo(-1);
    }
}

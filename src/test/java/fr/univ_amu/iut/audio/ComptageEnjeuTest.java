package fr.univ_amu.iut.audio;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.audio.viewmodel.ComptageEnjeu;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie le compteur d'**espèces à enjeu** de la barre de statut (#2353) : combien il y en a, combien
/// restent à revoir, et ce qu'il dit.
///
/// Le libellé fait partie du contrat : c'est une information de **pilotage** de la revue, et un compteur
/// qui dit mal ce qu'il compte ne pilote rien.
class ComptageEnjeuTest {

    /// Seule la Pipistrelle commune est prioritaire ici : l'ensemble réel est gardé ailleurs.
    private static final Predicate<LigneObservationAudio> A_ENJEU = ligne -> "Pippip".equals(ligne.taxonRetenu());

    private static LigneObservationAudio ligne(String taxon, StatutObservation statut) {
        return new LigneObservationAudio(
                1L,
                1L,
                1L,
                1,
                "2026-07-03",
                "130711",
                "Z41",
                "Test",
                taxon,
                0.9,
                null,
                null,
                statut,
                false,
                null,
                45,
                null,
                null,
                null,
                null,
                "seq.wav",
                0.0,
                5.0,
                LocalDateTime.of(2026, 7, 3, 22, 0),
                false,
                null,
                null,
                null,
                null,
                0);
    }

    @Test
    @DisplayName("Compte les observations à enjeu et celles qui restent à revoir")
    void compte_le_total_et_le_reste_a_revoir() {
        ComptageEnjeu comptage = ComptageEnjeu.de(
                List.of(
                        ligne("Pippip", StatutObservation.NON_TOUCHEE),
                        ligne("Pippip", StatutObservation.VALIDEE),
                        ligne("Pippip", StatutObservation.CORRIGEE),
                        ligne("Barbar", StatutObservation.NON_TOUCHEE)),
                A_ENJEU);

        assertThat(comptage.total()).as("la Barbastelle n'est pas prioritaire").isEqualTo(3);
        assertThat(comptage.aRevoir())
                .as("validée et corrigée comptent toutes deux comme revues")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Le libellé dit ce qui RESTE, parce que c'est ce sur quoi on agit")
    void libelle_dit_ce_qui_reste() {
        ComptageEnjeu comptage = ComptageEnjeu.de(
                List.of(ligne("Pippip", StatutObservation.NON_TOUCHEE), ligne("Pippip", StatutObservation.VALIDEE)),
                A_ENJEU);

        assertThat(comptage.libelle()).isEqualTo("2 à enjeu, 1 à revoir");
    }

    @Test
    @DisplayName("Quand il ne reste rien, le compteur le dit plutôt que d'afficher « 0 à revoir »")
    void libelle_quand_tout_est_revu() {
        ComptageEnjeu comptage = ComptageEnjeu.de(List.of(ligne("Pippip", StatutObservation.VALIDEE)), A_ENJEU);

        assertThat(comptage.libelle()).isEqualTo("1 à enjeu, toutes revues");
    }

    @Test
    @DisplayName("Sans espèce à enjeu, le compteur s'efface au lieu d'occuper la place")
    void libelle_vide_sans_espece_a_enjeu() {
        // Un « 0 à enjeu » permanent n'apprendrait rien et finirait par ne plus être lu.
        ComptageEnjeu comptage = ComptageEnjeu.de(List.of(ligne("Barbar", StatutObservation.NON_TOUCHEE)), A_ENJEU);

        assertThat(comptage.total()).isZero();
        assertThat(comptage.libelle()).isEmpty();
    }
}

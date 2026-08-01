package fr.univ_amu.iut.validation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Gardes des trois critères de revue portés en ligne de commande (#3082).
///
/// Ce qui est épinglé n'est pas le cas nominal mais les **décisions** : ce qui refuse et ce qui rend
/// vide (ADR 3082), et les deux règles que la commande doit dire **comme l'écran** - la plage qui
/// traverse minuit, et l'observation sans heure qui reste.
class FiltresRevueTest {

    private static LigneObservationAudio ligne(String taxonTadarida, String groupe, Integer heure) {
        return new LigneObservationAudio(
                1L,
                1L,
                1L,
                1,
                "2026-06-22",
                "640380",
                "A1",
                "Mon site",
                taxonTadarida,
                taxonTadarida == null ? null : 0.8,
                null,
                null,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                null,
                null,
                null,
                null,
                groupe,
                "seq.wav",
                null,
                null,
                heure == null ? null : LocalDateTime.of(2026, 6, 22, heure, 0),
                false,
                null,
                null,
                null,
                null,
                0,
                "Ahetze");
    }

    private static final LigneObservationAudio CHIRO_22H = ligne("Rhifer", "Chiroptères", 22);
    private static final LigneObservationAudio CHIRO_3H = ligne("Pipkuh", "Chiroptères", 3);
    private static final LigneObservationAudio SAUTERELLE_14H = ligne("Ortsp", "Orthoptères", 14);
    private static final LigneObservationAudio SANS_PROPOSITION = ligne(null, null, 23);
    private static final LigneObservationAudio SANS_HEURE = ligne("Barbar", "Chiroptères", null);
    private static final List<LigneObservationAudio> TOUTES =
            List.of(CHIRO_22H, CHIRO_3H, SAUTERELLE_14H, SANS_PROPOSITION, SANS_HEURE);

    @Test
    @DisplayName("#3082 : le taxon parent DÉSIGNE, donc il refuse en nommant ce qui est présent")
    void le_taxon_parent_refuse_et_nomme() {
        assertThat(FiltresRevue.parTaxonParent(TOUTES, "chiropteres"))
                .as("partiel et sans accent, comme --lieu")
                .containsExactly(CHIRO_22H, CHIRO_3H, SANS_HEURE);
        assertThatThrownBy(() -> FiltresRevue.parTaxonParent(TOUTES, "Oiseaux"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Chiroptères")
                .hasMessageContaining("Orthoptères");
    }

    @Test
    @DisplayName("#3082 : « non identifié » QUALIFIE, donc il rend vide sans refuser")
    void les_non_identifiees_rendent_vide_sans_refuser() {
        assertThat(FiltresRevue.nonIdentifiees(TOUTES, true))
                .as("sans proposition Tadarida, la règle exacte de la puce")
                .containsExactly(SANS_PROPOSITION);
        assertThat(FiltresRevue.nonIdentifiees(List.of(CHIRO_22H), true))
                .as("« aucune séquence en attente » est une réponse, pas une erreur")
                .isEmpty();
        assertThat(FiltresRevue.nonIdentifiees(TOUTES, false)).isEqualTo(TOUTES);
    }

    @Test
    @DisplayName("#3082 : la plage traverse minuit, comme la puce de l'écran")
    void la_plage_traverse_minuit() {
        // 21 → 6 doit retenir la nuit, et non ses quinze heures complémentaires. Si la commande disait
        // autre chose que l'écran sur la même donnée, un recoupement entre les deux deviendrait faux.
        assertThat(FiltresRevue.parPlageHoraire(TOUTES, 21, 6))
                .as("22 h et 3 h sont dans la nuit ; 14 h non")
                .contains(CHIRO_22H, CHIRO_3H, SANS_PROPOSITION)
                .doesNotContain(SAUTERELLE_14H);
        assertThat(FiltresRevue.parPlageHoraire(TOUTES, 8, 18))
                .as("plage ordinaire : les bornes sont comprises")
                .contains(SAUTERELLE_14H)
                .doesNotContain(CHIRO_22H);
    }

    @Test
    @DisplayName("#3082 : une observation sans heure reste, comme à l'écran")
    void une_observation_sans_heure_reste() {
        // L'absence d'horodatage n'est pas une heure hors plage. L'écarter perdrait précisément les
        // séquences qu'il faut aller examiner.
        assertThat(FiltresRevue.parPlageHoraire(TOUTES, 21, 6)).contains(SANS_HEURE);
        assertThat(FiltresRevue.parPlageHoraire(TOUTES, 8, 18)).contains(SANS_HEURE);
    }

    @Test
    @DisplayName("#3082 : une plage à demi donnée est un refus, pas une interprétation")
    void une_plage_incomplete_est_refusee() {
        // « --heure-debut 21 » seul se lirait « depuis 21 h » ou « jusqu'à 21 h » selon qui le lit :
        // choisir à sa place produirait un fichier plausible et faux.
        assertThatThrownBy(() -> FiltresRevue.parPlageHoraire(TOUTES, 21, null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("--heure-fin");
        assertThatThrownBy(() -> FiltresRevue.parPlageHoraire(TOUTES, null, 6))
                .isInstanceOf(RegleMetierException.class);
        assertThat(FiltresRevue.parPlageHoraire(TOUTES, null, null))
                .as("aucune borne : rien n'est écarté")
                .isEqualTo(TOUTES);
    }

    @Test
    @DisplayName("#3082 : une heure hors de 0..23 est refusée avec ses bornes")
    void une_heure_hors_bornes_est_refusee() {
        assertThatThrownBy(() -> FiltresRevue.parPlageHoraire(TOUTES, 25, 6))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("0 à 23");
        assertThatThrownBy(() -> FiltresRevue.parPlageHoraire(TOUTES, 21, -1)).isInstanceOf(RegleMetierException.class);
    }
}

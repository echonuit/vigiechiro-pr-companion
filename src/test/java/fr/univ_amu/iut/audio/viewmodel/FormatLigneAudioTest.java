package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Formatages d'affichage de la table audio. On vérifie surtout la colonne **« Votre taxon »**
/// ([FormatLigneAudio#votreTaxon]) : tiret tant que l'observation n'est pas revue, vernaculaire du taxon
/// retenu une fois revue, et repli sur le code pour une souche hors référentiel sans vernaculaire.
class FormatLigneAudioTest {

    /// Construit une ligne en ne fixant que ce qui compte ici : le taxon observateur (décision) et le
    /// vernaculaire projeté `nomEspece` (= vernaculaire de `COALESCE(observateur, Tadarida)`).
    private static LigneObservationAudio ligne(String taxonObservateur, String nomEspece) {
        return new LigneObservationAudio(
                1L,
                10L,
                7L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Mon site",
                "Pippip",
                0.9,
                taxonObservateur,
                taxonObservateur == null ? null : 0.95,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                45000,
                nomEspece,
                "Pipistrelle commune");
    }

    @Test
    @DisplayName("Votre taxon : tiret tant que l'observation n'a pas été revue")
    void votre_taxon_tiret_si_non_revue() {
        assertThat(FormatLigneAudio.votreTaxon(ligne(null, "Pipistrelle commune")))
                .isEqualTo("—");
    }

    @Test
    @DisplayName("Votre taxon : vernaculaire du taxon retenu une fois revue")
    void votre_taxon_vernaculaire_si_revue() {
        assertThat(FormatLigneAudio.votreTaxon(ligne("Rhifer", "Grand Rhinolophe")))
                .isEqualTo("Grand Rhinolophe");
    }

    @Test
    @DisplayName("Votre taxon : repli sur le code pour une souche hors référentiel sans vernaculaire")
    void votre_taxon_code_si_souche() {
        assertThat(FormatLigneAudio.votreTaxon(ligne("Xxxsp", null))).isEqualTo("Xxxsp");
    }
}

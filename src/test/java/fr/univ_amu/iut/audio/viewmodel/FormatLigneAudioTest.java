package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Formatages d'affichage de la table audio. On vérifie surtout la colonne **« Votre taxon »**
/// ([FormatLigneAudio#votreTaxon]) : tiret tant que l'observation n'est pas revue, vernaculaire du taxon
/// retenu une fois revue, et repli sur le code pour une souche hors référentiel sans vernaculaire.
class FormatLigneAudioTest {

    @Test
    @DisplayName("#530 : heureColonne affiche l'heure de la nuit (HH:mm) depuis l'instant, tiret si absente")
    void heure_colonne_formate_l_heure_de_la_nuit() {
        assertThat(FormatLigneAudio.heureColonne(LocalDateTime.of(2026, 4, 22, 22, 37, 5)))
                .isEqualTo("22:37");
        // Après minuit (l'instant porte la date du lendemain) : l'affichage reste l'heure du jour.
        assertThat(FormatLigneAudio.heureColonne(LocalDateTime.of(2026, 4, 23, 0, 15, 0)))
                .isEqualTo("00:15");
        assertThat(FormatLigneAudio.heureColonne(null)).isEqualTo("—");
    }

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
                45,
                nomEspece,
                "Pipistrelle commune",
                "Chiroptères",
                "PaRec_20260620_000.wav",
                0.20,
                0.32,
                null,
                false);
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

    @Test
    @DisplayName("Fréquence colonne : « 45 kHz » (valeur Tadarida en kHz), tiret si absente")
    void frequence_colonne_formatee() {
        assertThat(FormatLigneAudio.frequenceColonne(45)).isEqualTo("45 kHz");
        assertThat(FormatLigneAudio.frequenceColonne(null)).isEqualTo("—");
    }

    @Test
    @DisplayName("Durée colonne : durée réelle, unité adaptative ms (< 1 s) / s (≥ 1 s), tiret si absente")
    void duree_colonne_reelle() {
        // Les bornes sont déjà en secondes réelles (temps Tadarida) : durée = finS − debutS, sans division.
        assertThat(FormatLigneAudio.dureeColonne(0.20, 0.32)).isEqualTo("120 ms"); // 0,12 s < 1 s
        assertThat(FormatLigneAudio.dureeColonne(0.40, 2.50)).isEqualTo("2,1 s"); // 2,1 s ≥ 1 s
        assertThat(FormatLigneAudio.dureeColonne(null, 0.32)).isEqualTo("—");
        assertThat(FormatLigneAudio.dureeColonne(0.20, null)).isEqualTo("—");
    }

    @Test
    @DisplayName("Début colonne : position réelle en secondes (temps Tadarida), FR à 2 décimales, tiret si absente")
    void position_colonne_reelle() {
        // debutS est déjà en secondes réelles : affiché tel quel (pas de division).
        assertThat(FormatLigneAudio.positionColonne(3.2)).isEqualTo("3,20 s");
        assertThat(FormatLigneAudio.positionColonne(0.5)).isEqualTo("0,50 s");
        assertThat(FormatLigneAudio.positionColonne(null)).isEqualTo("—");
    }
}

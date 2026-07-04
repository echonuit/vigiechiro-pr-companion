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
                45,
                nomEspece,
                "Pipistrelle commune",
                "PaRec_20260620_000.wav",
                0.20,
                0.32);
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
    @DisplayName("Comparateur Proba : ordre numérique (« 9 % » < « 83 % » < « 100 % »), absent (« — ») en tête")
    void comparateur_pourcentage_ordonne_par_valeur() {
        var tri = new java.util.ArrayList<>(java.util.List.of("83 %", "100 %", "9 %", "—"));
        tri.sort(FormatLigneAudio.comparateurPourcentage());
        assertThat(tri).containsExactly("—", "9 %", "83 %", "100 %");
    }

    @Test
    @DisplayName("Comparateur Passage : ordre numérique (« N°2 » < « N°10 »)")
    void comparateur_passage_ordonne_par_numero() {
        var tri = new java.util.ArrayList<>(java.util.List.of("N°10", "N°2", "N°1"));
        tri.sort(FormatLigneAudio.comparateurNumeroPassage());
        assertThat(tri).containsExactly("N°1", "N°2", "N°10");
    }

    @Test
    @DisplayName("Comparateur Statut : ordre de revue (À revoir → Validée → Corrigée), pas alphabétique")
    void comparateur_statut_ordonne_par_revue() {
        var tri = new java.util.ArrayList<>(java.util.List.of("Corrigée", "À revoir", "Validée"));
        tri.sort(FormatLigneAudio.comparateurStatut());
        assertThat(tri).containsExactly("À revoir", "Validée", "Corrigée");
    }

    @Test
    @DisplayName("Fréquence colonne : « 45 kHz » (valeur Tadarida en kHz), tiret si absente")
    void frequence_colonne_formatee() {
        assertThat(FormatLigneAudio.frequenceColonne(45)).isEqualTo("45 kHz");
        assertThat(FormatLigneAudio.frequenceColonne(null)).isEqualTo("—");
    }

    @Test
    @DisplayName("Comparateur Fréquence : ordre numérique (« 9 kHz » < « 45 kHz »), absente en tête")
    void comparateur_frequence_ordonne_par_valeur() {
        var tri = new java.util.ArrayList<>(java.util.List.of("45 kHz", "9 kHz", "—"));
        tri.sort(FormatLigneAudio.comparateurFrequence());
        assertThat(tri).containsExactly("—", "9 kHz", "45 kHz");
    }

    @Test
    @DisplayName("Durée colonne : durée réelle en ms (bornes transformées ÷ expansion ×10), tiret si absente")
    void duree_colonne_reelle() {
        // (0.32 − 0.20) s transformées = 0,12 s ; ÷ 10 (expansion) = 0,012 s = 12 ms.
        assertThat(FormatLigneAudio.dureeColonne(0.20, 0.32)).isEqualTo("12 ms");
        assertThat(FormatLigneAudio.dureeColonne(null, 0.32)).isEqualTo("—");
        assertThat(FormatLigneAudio.dureeColonne(0.20, null)).isEqualTo("—");
    }

    @Test
    @DisplayName("Comparateur Durée : ordre numérique (« 5 ms » < « 12 ms »), absente en tête")
    void comparateur_duree_ordonne_par_valeur() {
        var tri = new java.util.ArrayList<>(java.util.List.of("12 ms", "5 ms", "—"));
        tri.sort(FormatLigneAudio.comparateurDuree());
        assertThat(tri).containsExactly("—", "5 ms", "12 ms");
    }

    @Test
    @DisplayName(
            "Début colonne : position réelle en secondes (borne transformée ÷ ×10), FR à 2 décimales, tiret si absente")
    void position_colonne_reelle() {
        // debutS est sur la timeline transformée (×10) : 3,2 s transformé → 0,32 s réel.
        assertThat(FormatLigneAudio.positionColonne(3.2)).isEqualTo("0,32 s");
        assertThat(FormatLigneAudio.positionColonne(0.5)).isEqualTo("0,05 s");
        assertThat(FormatLigneAudio.positionColonne(null)).isEqualTo("—");
    }

    @Test
    @DisplayName("Comparateur Début : ordre par position (« 0,05 s » < « 0,32 s »), absente (« — ») en tête")
    void comparateur_position_ordonne_par_valeur() {
        var tri = new java.util.ArrayList<>(java.util.List.of("0,32 s", "0,05 s", "1,20 s", "—"));
        tri.sort(FormatLigneAudio.comparateurPosition());
        assertThat(tri).containsExactly("—", "0,05 s", "0,32 s", "1,20 s");
    }
}

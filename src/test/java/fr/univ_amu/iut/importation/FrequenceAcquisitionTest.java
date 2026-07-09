package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.importation.model.FrequenceAcquisition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Résolution de la vraie fréquence d'acquisition ([FrequenceAcquisition]), calcul pur sans I/O.
class FrequenceAcquisitionTest {

    @Test
    @DisplayName("Avec log : la fréquence du log fait foi, même si l'en-tête est plus bas (brut PR déjà expansé)")
    void avec_log_le_log_fait_foi() {
        // En-tête PR à Fe/10 (38400) mais log Fe384kHz : l'acquisition réelle est 384000.
        assertThat(FrequenceAcquisition.reelle(38_400, 384_000)).isEqualTo(384_000);
        // En-tête « direct » égal au log : inchangé.
        assertThat(FrequenceAcquisition.reelle(384_000, 384_000)).isEqualTo(384_000);
    }

    @Test
    @DisplayName("Sans log, en-tête ≥ seuil : l'en-tête est une acquisition directe (plein spectre)")
    void sans_log_entete_haut_est_direct() {
        assertThat(FrequenceAcquisition.reelle(384_000, null)).isEqualTo(384_000);
        assertThat(FrequenceAcquisition.reelle(FrequenceAcquisition.SEUIL_ACQUISITION_HZ, null))
                .isEqualTo(FrequenceAcquisition.SEUIL_ACQUISITION_HZ);
    }

    @Test
    @DisplayName("Sans log, en-tête < seuil : l'en-tête est déjà expansé ×10 (repli sur la réalité PR)")
    void sans_log_entete_bas_est_deja_expanse() {
        assertThat(FrequenceAcquisition.reelle(38_400, null)).isEqualTo(384_000);
        assertThat(FrequenceAcquisition.reelle(19_200, null)).isEqualTo(192_000);
    }

    @Test
    @DisplayName("Un log à 0 est ignoré (traité comme absent) : on retombe sur l'en-tête")
    void log_zero_ignore() {
        assertThat(FrequenceAcquisition.reelle(38_400, 0)).isEqualTo(384_000); // < seuil -> x10
        assertThat(FrequenceAcquisition.reelle(200_000, 0)).isEqualTo(200_000); // >= seuil -> direct
    }
}

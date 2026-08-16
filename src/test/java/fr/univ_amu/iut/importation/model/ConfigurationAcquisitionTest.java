package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La sérialisation d'une configuration d'acquisition, et la nuit à laquelle elle se rattache (#3460).
///
/// Ces tests portent sur le **record lui-même**, sans passer par [AnalyseurLogPR]. La distinction n'est
/// pas gratuite : l'analyseur ne peut produire qu'une fenêtre d'acquisition **entière ou absente**, sa
/// regex exigeant les deux bornes. La garde qui traite une fenêtre à demi renseignée reste donc
/// inatteignable par lui, alors que le record est public et peut être construit ainsi.
///
/// C'est PIT qui l'a signalé : deux mutants survivaient sur cette garde, et aucun test passant par
/// l'analyseur ne pouvait les tuer.
class ConfigurationAcquisitionTest {

    private static final LocalDateTime POSE_LE_22_A_16H = LocalDateTime.of(2026, 4, 22, 16, 2, 21);

    @Test
    @DisplayName("Une fenêtre complète est sérialisée comme un intervalle")
    void une_fenetre_complete_est_serialisee() {
        ConfigurationAcquisition configuration = new ConfigurationAcquisition(
                POSE_LE_22_A_16H, "20:25:00", "07:47:00", 384000, "8-120kHz", "16dB 1dt. GN0", "Acquisi. …");

        assertThat(configuration.enJson())
                .contains("\"fenetre\":\"20:25:00-07:47:00\"")
                .contains("\"feHz\":\"384000\"")
                .contains("\"bandePassante\":\"8-120kHz\"");
    }

    @Test
    @DisplayName("Une fenêtre à demi renseignée ne fabrique pas un intervalle bancal")
    void une_fenetre_a_demi_renseignee_ne_fabrique_rien() {
        // Sans cette garde, l'absence d'une borne produirait « null-06:30 » ou « 21:00-null » dans une
        // colonne que personne ne relit ensuite. Mieux vaut pas d'intervalle qu'un intervalle faux.
        ConfigurationAcquisition sansFin =
                new ConfigurationAcquisition(POSE_LE_22_A_16H, "21:00:00", null, 256000, null, null, "…");
        ConfigurationAcquisition sansDebut =
                new ConfigurationAcquisition(POSE_LE_22_A_16H, null, "06:30:00", 256000, null, null, "…");

        assertThat(sansFin.enJson()).doesNotContain("null-").doesNotContain("-null");
        assertThat(sansDebut.enJson()).doesNotContain("null-").doesNotContain("-null");
    }

    @Test
    @DisplayName("Une configuration posée l'après-midi régit la nuit qui SUIT, pas la précédente")
    void une_configuration_de_l_apres_midi_regit_la_nuit_qui_suit() {
        // Le capteur est posé et configuré en fin de journée pour la nuit qui commence. La bascule de
        // midi de PartitionNuits donne donc la nuit du 22, celle qui court du 22 au soir au 23 au matin.
        ConfigurationAcquisition configuration =
                new ConfigurationAcquisition(POSE_LE_22_A_16H, "20:25:00", "07:47:00", 384000, null, null, "…");

        assertThat(configuration.nuit()).isEqualTo(LocalDate.of(2026, 4, 22));
    }

    @Test
    @DisplayName("Une configuration posée AVANT midi se rattache à la nuit qui s'achève")
    void une_configuration_d_avant_midi_se_rattache_a_la_nuit_qui_s_acheve() {
        // Le cas symétrique, et il n'est pas théorique : un capteur relevé au petit matin peut
        // journaliser des paramètres à 07:00, qui décrivent la nuit qui vient de finir.
        ConfigurationAcquisition auPetitMatin = new ConfigurationAcquisition(
                LocalDateTime.of(2026, 4, 23, 7, 0, 0), "20:25:00", "07:47:00", 384000, null, null, "…");

        assertThat(auPetitMatin.nuit()).isEqualTo(LocalDate.of(2026, 4, 22));
    }
}

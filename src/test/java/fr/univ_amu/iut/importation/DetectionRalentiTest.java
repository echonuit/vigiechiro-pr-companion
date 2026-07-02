package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.importation.model.DetectionRalenti;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Détection d'un enregistrement **déjà ralenti** ([DetectionRalenti]), calcul pur sans I/O.
class DetectionRalentiTest {

    @Test
    @DisplayName("Avec log : un en-tête sous la fréquence d'acquisition = déjà ralenti")
    void avec_log_entete_sous_fe_est_ralenti() {
        assertThat(DetectionRalenti.estDejaRalenti(38400, 384000)).isTrue();
    }

    @Test
    @DisplayName("Avec log : un en-tête égal à la fréquence d'acquisition = brut (ok)")
    void avec_log_entete_egal_fe_est_ok() {
        assertThat(DetectionRalenti.estDejaRalenti(384000, 384000)).isFalse();
    }

    @Test
    @DisplayName("Sans log : sous le seuil d'un ultrason brut = déjà ralenti ; au-dessus = ok")
    void sans_log_applique_le_seuil() {
        assertThat(DetectionRalenti.estDejaRalenti(38400, null)).isTrue();
        assertThat(DetectionRalenti.estDejaRalenti(DetectionRalenti.FREQUENCE_ACQUISITION_MIN_HZ, null))
                .isFalse();
        assertThat(DetectionRalenti.estDejaRalenti(384000, null)).isFalse();
    }

    @Test
    @DisplayName("Log nul ou non positif : on retombe sur le seuil absolu")
    void log_absent_ou_invalide_retombe_sur_le_seuil() {
        assertThat(DetectionRalenti.estDejaRalenti(38400, 0)).isTrue();
        assertThat(DetectionRalenti.estDejaRalenti(200000, 0)).isFalse();
    }
}

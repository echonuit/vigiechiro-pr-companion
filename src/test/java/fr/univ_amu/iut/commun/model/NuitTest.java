package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NuitTest {

    @Test
    @DisplayName("Un enregistrement du soir appartient à la nuit de sa propre date")
    void le_soir_est_la_nuit_du_jour() {
        assertThat(Nuit.de(LocalDateTime.of(2026, 7, 3, 21, 0, 4))).isEqualTo(LocalDate.of(2026, 7, 3));
    }

    @Test
    @DisplayName("Un enregistrement du petit matin (avant midi) appartient à la nuit de la veille")
    void le_petit_matin_est_la_nuit_de_la_veille() {
        assertThat(Nuit.de(LocalDateTime.of(2026, 7, 4, 5, 15, 0))).isEqualTo(LocalDate.of(2026, 7, 3));
    }

    @Test
    @DisplayName("Midi pile bascule sur la nuit du jour (borne inclusive côté après-midi)")
    void midi_bascule_sur_la_nuit_du_jour() {
        assertThat(Nuit.de(LocalDateTime.of(2026, 7, 4, 12, 0, 0))).isEqualTo(LocalDate.of(2026, 7, 4));
    }

    @Test
    @DisplayName("Juste avant midi appartient encore à la nuit de la veille")
    void juste_avant_midi_est_la_veille() {
        assertThat(Nuit.de(LocalDateTime.of(2026, 7, 4, 11, 59, 59))).isEqualTo(LocalDate.of(2026, 7, 3));
    }
}

package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Un nom qui dit qu'il a perdu une collision** (#5720) : sous le nommage horodaté, toute tranche finit
/// par `_000`, et seul l'arbitrage de [NommageSequences#arbitrer] écrit `_001` ou au-delà. Un nom sans
/// horodatage retombe sur le suffixe indexé, où `_001` est la deuxième tranche : ce n'est pas un perdant.
class PerdantDeCollisionTest {

    private static final String BASE = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205342";

    @Test
    @DisplayName("#5720 : un nom horodaté en _001 ou au-delà est un perdant de collision")
    void un_nom_horodate_au_dela_de_000_est_un_perdant() {
        assertThat(NommageSequences.perdantDeCollision(BASE + "_001.wav")).isTrue();
        assertThat(NommageSequences.perdantDeCollision(BASE + "_002.wav")).isTrue();
    }

    @Test
    @DisplayName("#5720 : un nom horodaté en _000 n'est pas un perdant")
    void un_nom_horodate_en_000_n_est_pas_un_perdant() {
        assertThat(NommageSequences.perdantDeCollision(BASE + "_000.wav")).isFalse();
    }

    @Test
    @DisplayName("#5720 : sans horodatage, _001 est un index de tranche et non un perdant")
    void sans_horodatage_001_est_un_index() {
        assertThat(NommageSequences.perdantDeCollision("Car640380-2026-Pass2-Z1-essai_001.wav"))
                .isFalse();
    }

    @Test
    @DisplayName("#5720 : un nom sans suffixe de tranche n'est pas un perdant")
    void un_nom_sans_suffixe_n_est_pas_un_perdant() {
        assertThat(NommageSequences.perdantDeCollision(BASE + ".wav")).isFalse();
        assertThat(NommageSequences.perdantDeCollision(null)).isFalse();
    }
}

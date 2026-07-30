package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests du formateur de **durée de séquence** partagé [Formats#dureeSecondes] (#1053) : une décimale en
/// locale française, arrondi au dixième, et tiret pour une valeur absente (corrige le NPE latent du
/// formateur privé qu'il remplace).
class FormatsTest {

    @Test
    @DisplayName("dureeSecondes : une décimale, virgule décimale FR")
    void dureeSecondes_nominal() {
        assertThat(Formats.dureeSecondes(5.0)).isEqualTo("5,0 s");
        assertThat(Formats.dureeSecondes(12.5)).isEqualTo("12,5 s");
    }

    @Test
    @DisplayName("dureeSecondes : arrondi au dixième de seconde")
    void dureeSecondes_arrondi() {
        assertThat(Formats.dureeSecondes(2.34)).isEqualTo("2,3 s");
        assertThat(Formats.dureeSecondes(2.37)).isEqualTo("2,4 s");
    }

    @Test
    @DisplayName("dureeSecondes : null → tiret « — », sans NPE")
    void dureeSecondes_null() {
        assertThat(Formats.dureeSecondes(null)).isEqualTo("—");
    }
}

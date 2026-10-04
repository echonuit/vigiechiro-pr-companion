package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le code d'un nouveau point : `Z` suivi du premier numéro libre du site, comme le portail nomme un
/// point libre (#5688). Les points systématiques `A1` à `H2` relèvent de #5608.
class CodePointLibreTest {

    @Test
    @DisplayName("#5688 : un site sans point propose Z1")
    void site_vide() {
        assertThat(CodePointLibre.suivant(List.of())).isEqualTo("Z1");
    }

    @Test
    @DisplayName("#5688 : le premier numéro libre, sans tenir compte des codes qui ne commencent pas par Z")
    void premier_numero_libre() {
        assertThat(CodePointLibre.suivant(List.of("Z1", "Z2", "A1"))).isEqualTo("Z3");
    }

    @Test
    @DisplayName("#5688 : un trou se comble")
    void un_trou_se_comble() {
        assertThat(CodePointLibre.suivant(List.of("Z1", "Z3"))).isEqualTo("Z2");
    }

    @Test
    @DisplayName("#5688 : la casse ne compte pas, et un code Z mal formé ne prend pas de numéro")
    void casse_et_codes_mal_formes() {
        assertThat(CodePointLibre.suivant(List.of("z1", "ZA", "Z"))).isEqualTo("Z2");
    }
}

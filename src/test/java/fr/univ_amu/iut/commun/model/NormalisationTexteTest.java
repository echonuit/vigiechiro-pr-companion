package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests purs de [NormalisationTexte] : la normalisation efface casse et accents, et `contient` en
/// découle (recherche tolérante, #144).
class NormalisationTexteTest {

    @Test
    @DisplayName("normaliser : minuscule, sans accents, sans espaces de bord ; null -> vide")
    void normaliser_efface_casse_et_accents() {
        assertThat(NormalisationTexte.normaliser("  Étang de la Tuilière  ")).isEqualTo("etang de la tuiliere");
        assertThat(NormalisationTexte.normaliser("DÉPOSÉ")).isEqualTo("depose");
        assertThat(NormalisationTexte.normaliser(null)).isEmpty();
    }

    @Test
    @DisplayName("contient : insensible casse/accents ; une requête vide ne correspond jamais")
    void contient_est_tolerant() {
        assertThat(NormalisationTexte.contient("Étang de la Tuilière", "tuiliere"))
                .isTrue();
        assertThat(NormalisationTexte.contient("Carré 640380", "640380")).isTrue();
        assertThat(NormalisationTexte.contient("Vérifié", "VERIFI")).isTrue();
        assertThat(NormalisationTexte.contient("quoi que ce soit", "  ")).isFalse();
        assertThat(NormalisationTexte.contient(null, "x")).isFalse();
    }
}

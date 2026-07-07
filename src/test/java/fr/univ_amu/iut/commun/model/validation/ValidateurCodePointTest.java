package fr.univ_amu.iut.commun.model.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de la règle R2 : code de point = une lettre majuscule suivie d'un ou plusieurs chiffres.
class ValidateurCodePointTest {

    @Test
    @DisplayName("R2 : lettre majuscule + un ou plusieurs chiffres est valide")
    void lettre_majuscule_et_chiffres_valides() {
        assertThat(ValidateurCodePoint.estValide("A1")).isTrue();
        assertThat(ValidateurCodePoint.estValide("Z4")).isTrue();
        assertThat(ValidateurCodePoint.estValide("B0")).isTrue();
        assertThat(ValidateurCodePoint.estValide("Z41"))
                .as("le numéro peut comporter plusieurs chiffres")
                .isTrue();
        assertThat(ValidateurCodePoint.estValide("A12"))
                .as("plusieurs chiffres")
                .isTrue();
        assertThat(ValidateurCodePoint.estValide("B100")).as("trois chiffres").isTrue();
    }

    @Test
    @DisplayName("R2 : formats incorrects rejetés")
    void formats_invalides() {
        assertThat(ValidateurCodePoint.estValide("a1"))
                .as("minuscule interdite")
                .isFalse();
        assertThat(ValidateurCodePoint.estValide("1A")).as("ordre inversé").isFalse();
        assertThat(ValidateurCodePoint.estValide("AA")).as("deux lettres").isFalse();
        assertThat(ValidateurCodePoint.estValide("A")).as("aucun chiffre").isFalse();
        assertThat(ValidateurCodePoint.estValide("A1B"))
                .as("lettre après les chiffres")
                .isFalse();
        assertThat(ValidateurCodePoint.estValide("AB1"))
                .as("deux lettres devant")
                .isFalse();
        assertThat(ValidateurCodePoint.estValide("")).isFalse();
        assertThat(ValidateurCodePoint.estValide(null)).isFalse();
    }

    @Test
    @DisplayName("exigerValide renvoie la valeur quand elle est conforme")
    void exiger_valide_renvoie_la_valeur() {
        assertThat(ValidateurCodePoint.exigerValide("A1")).isEqualTo("A1");
    }

    @Test
    @DisplayName("exigerValide lève une exception quand la règle R2 est violée")
    void exiger_valide_leve_si_invalide() {
        assertThatThrownBy(() -> ValidateurCodePoint.exigerValide("a1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Code de point invalide");
    }
}

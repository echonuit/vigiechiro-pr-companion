package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La règle R1 du format d'un numéro de carré (#4577), en un seul endroit pour les deux sources qui
/// en rendent un.
@DisplayName("Le format d'un numéro de carré (R1)")
class NumeroDeCarreTest {

    @Test
    @DisplayName("un département à un chiffre retrouve son zéro de gauche")
    void departement_a_un_chiffre_retrouve_son_zero() {
        assertThat(NumeroDeCarre.surSixChiffres("40110")).isEqualTo("040110");
    }

    @Test
    @DisplayName("un numéro déjà à six chiffres passe inchangé")
    void six_chiffres_passent_inchanges() {
        assertThat(NumeroDeCarre.surSixChiffres("130711")).isEqualTo("130711");
    }
}

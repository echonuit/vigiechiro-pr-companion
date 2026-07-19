package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// L'indice acoustique **refuse d'être impossible** (#1963).
///
/// Le record prend `(mesurees, concordantes)` et la phrase rendue les cite dans l'ordre inverse
/// (« *concordantes* séquence(s) sur *mesurées* »). Deux `int` de même type, un ordre plausible dans les
/// deux sens : l'inversion est facile, et elle ne fait rougir aucun test - elle s'est produite, et c'est
/// l'ouverture d'une capture qui l'a montrée (« 4236 séquence(s) sur 4053 »).
///
/// Le type l'interdit désormais à la construction, et le message dit où chercher.
class IndiceAcoustiqueTest {

    @Test
    @DisplayName("Plus de concordantes que de mesurées : refusé, avec le sens des arguments dans le message")
    void concordantes_superieures_aux_mesurees_refusees() {
        assertThatThrownBy(() -> new IndiceAcoustique(4053, 4236))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("impossible")
                .as("le message doit rappeler l'ordre attendu, puisque c'est lui qu'on se trompe")
                .hasMessageContaining("(mesurees, concordantes)");
    }

    @Test
    @DisplayName("Un indice négatif est refusé")
    void indice_negatif_refuse() {
        assertThatThrownBy(() -> new IndiceAcoustique(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Les cas légitimes passent, y compris l'égalité et l'indice vide")
    void cas_legitimes() {
        assertThat(new IndiceAcoustique(4236, 4053).estRenseigne()).isTrue();
        assertThat(new IndiceAcoustique(12, 12).concordantes()).isEqualTo(12);
        assertThat(new IndiceAcoustique(0, 0).estRenseigne())
                .as("aucune séquence mesurée : rien à afficher, mais pas une erreur")
                .isFalse();
    }
}

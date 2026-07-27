package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Volumes lus et écrits par un import (#2358) : le type valeur que le compte rendu chiffré rend en
/// barres. Ce qu'on vérifie ici, c'est **l'arithmétique dont dépend l'échelle des barres** - une somme
/// fausse donnerait deux barres à la mauvaise proportion, avec l'autorité du visuel.
class VolumesImportTest {

    @Nested
    @DisplayName("Ce qu'un import a coûté sur le disque")
    class Ecrit {

        @Test
        @DisplayName("Le volume écrit est celui des bruts conservés plus celui des séquences")
        void ecrit_somme_bruts_et_sequences() {
            VolumesImport volumes = new VolumesImport(5_000, 5_000, 1_800);

            assertThat(volumes.octetsEcrits())
                    .as("un import qui conserve les bruts écrit deux fois : l'archive et les séquences")
                    .isEqualTo(6_800);
        }

        @Test
        @DisplayName("Sans conservation des bruts, l'écrit se réduit aux séquences (ADR 0036)")
        void ecrit_sans_bruts_vaut_les_sequences() {
            VolumesImport volumes = new VolumesImport(5_000, 0, 1_800);

            assertThat(volumes.octetsEcrits()).isEqualTo(1_800);
            assertThat(volumes.octetsLus())
                    .as("le volume lu ne dépend pas de l'option de conservation : la carte a été lue en entier")
                    .isEqualTo(5_000);
        }
    }

    @Nested
    @DisplayName("Agrégation d'un import multi-nuits")
    class Cumul {

        @Test
        @DisplayName("Les volumes de deux nuits s'additionnent composante par composante")
        void plus_additionne_les_trois_composantes() {
            VolumesImport premiere = new VolumesImport(5_000, 5_000, 1_800);
            VolumesImport seconde = new VolumesImport(3_000, 3_000, 1_100);

            assertThat(premiere.plus(seconde)).isEqualTo(new VolumesImport(8_000, 8_000, 2_900));
        }

        @Test
        @DisplayName("AUCUN est le neutre du cumul : il n'ajoute rien")
        void aucun_est_neutre() {
            VolumesImport nuit = new VolumesImport(5_000, 0, 1_800);

            assertThat(VolumesImport.AUCUN.plus(nuit)).isEqualTo(nuit);
            assertThat(nuit.plus(VolumesImport.AUCUN)).isEqualTo(nuit);
        }
    }

    @Nested
    @DisplayName("Ce que le compte rendu tait")
    class Vide {

        @Test
        @DisplayName("Rien de mesuré : le bloc des volumes n'a rien à montrer")
        void aucun_est_vide() {
            assertThat(VolumesImport.AUCUN.estVide())
                    .as("deux barres à zéro se liraient comme un import qui n'a rien produit")
                    .isTrue();
        }

        @Test
        @DisplayName("Un import qui a lu sans rien conserver a quand même quelque chose à montrer")
        void lu_seul_n_est_pas_vide() {
            assertThat(new VolumesImport(5_000, 0, 1_800).estVide()).isFalse();
        }
    }

    @Test
    @DisplayName("Un volume négatif est refusé : ce n'est pas une mesure, c'est un défaut de calcul")
    void refuse_les_volumes_negatifs() {
        assertThatThrownBy(() -> new VolumesImport(-1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("octetsLus");
        assertThatThrownBy(() -> new VolumesImport(0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("octetsBruts");
        assertThatThrownBy(() -> new VolumesImport(0, 0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("octetsSequences");
    }
}

package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Les formateurs partagés du produit.
///
/// [Formats#dureeSecondes] (#1053) : une décimale en locale française, arrondi au dixième, et tiret pour
/// une valeur absente - il corrige le NPE latent du formateur privé qu'il remplace.
///
/// [Formats#octetsLisibles] (#3573) : base **1000**, celle dans laquelle les supports sont vendus. Il
/// n'avait aucun test jusque-là, ce qui est aussi la raison pour laquelle une **seconde** base avait pu
/// s'installer ailleurs dans le produit sans que rien ne le dise.
class FormatsTest {

    @Test
    @DisplayName("octetsLisibles : base 1000, celle dans laquelle les supports sont vendus (#3573)")
    void octets_lisibles_en_base_1000() {
        // Une carte « 128 Go » porte 128 000 000 000 octets. Nos chiffres servent à répondre à « est-ce
        // que ça tient sur ma clé ? » : les compter en base 1024 sous une étiquette « Go » ferait
        // comparer des grandeurs différentes sous le même nom.
        assertThat(Formats.octetsLisibles(4_000_000_000L)).isEqualTo("4,0 Go");
        assertThat(Formats.octetsLisibles(1_000_000_000L)).isEqualTo("1,0 Go");
        assertThat(Formats.octetsLisibles(999_999_999L)).isEqualTo("1000 Mo");
        assertThat(Formats.octetsLisibles(1_000_000L)).isEqualTo("1,0 Mo");
        assertThat(Formats.octetsLisibles(1_570_000L))
                .as("sous dix, la décimale porte de l'information : « 1,6 Mo » n'est pas « 2 Mo »")
                .isEqualTo("1,6 Mo");
        assertThat(Formats.octetsLisibles(128_300_000_000L))
                .as("au-dessus, elle n'est plus que du bruit")
                .isEqualTo("128 Go");
        assertThat(Formats.octetsLisibles(10_000_000_000L))
                .as("la borne des dix : à dix pile, plus de décimale")
                .isEqualTo("10 Go");
        assertThat(Formats.octetsLisibles(9_900_000_000L))
                .as("et juste en dessous, elle est là")
                .isEqualTo("9,9 Go");
        assertThat(Formats.octetsLisibles(999_999L)).isEqualTo("999 Ko");
        assertThat(Formats.octetsLisibles(1_000L)).isEqualTo("1 Ko");
    }

    @Test
    @DisplayName("octetsLisibles : sous le kilo-octet et en négatif, on ne descend pas sous zéro")
    void octets_lisibles_bornes_basses() {
        assertThat(Formats.octetsLisibles(999L)).isEqualTo("0 Ko");
        assertThat(Formats.octetsLisibles(0L)).isEqualTo("0 Ko");
        assertThat(Formats.octetsLisibles(-1L))
                .as("une taille négative n'existe pas : la ramener à zéro vaut mieux que l'afficher")
                .isEqualTo("0 Ko");
    }

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

package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// L'étiquette de proximité **donne une distance et ne la juge pas** (#5839).
///
/// Elle a porté une alerte sous un seuil emprunté à un autre protocole. Le Point Fixe n'impose aucune
/// distance entre deux points : la phrase est la même à dix mètres qu'à huit cents (ADR 5839).
class LibelleProximiteTest {

    @Test
    @DisplayName("#5839 : à cent mètres, la distance se dit nue, sans règle ni reproche")
    void cent_metres_ne_sont_pas_une_faute() {
        String libelle = CartesPointsSite.libelleProximite(100);

        // Le texte est exigé EN ENTIER : l'alerte d'avant le prolongeait, et un simple `contains` sur la
        // distance l'aurait laissée revenir.
        assertThat(libelle).isEqualTo("à 100 m du point le plus proche");
    }

    @Test
    @DisplayName("#5839 : la phrase a la même forme à dix mètres qu'à huit cents")
    void la_forme_ne_depend_pas_de_la_distance() {
        assertThat(CartesPointsSite.libelleProximite(10)).isEqualTo("à 10 m du point le plus proche");
        assertThat(CartesPointsSite.libelleProximite(850)).isEqualTo("à 850 m du point le plus proche");
    }

    @Test
    @DisplayName("Au-delà du kilomètre, la distance se lit en kilomètres")
    void distance_lisible_en_kilometres() {
        assertThat(CartesPointsSite.libelleProximite(2400)).isEqualTo("à 2,4 km du point le plus proche");
    }
}

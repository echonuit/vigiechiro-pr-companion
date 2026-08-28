package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La conversion degré-mètre, en un seul endroit et à deux précisions (passe 7 de la clôture de #4573).
@DisplayName("Conversion géographique : deux précisions, une raison chacune")
class ConversionGeographiqueTest {

    @Test
    @DisplayName("la distance mesurée reproduit ce que la plateforme a rendu : 1 412 m au coin d'une maille")
    void distance_reproduit_la_mesure_du_serveur() {
        // Coin commun à quatre carrés et centre de l'un d'eux, mesurés le 2026-08-27 contre le serveur
        // réel, qui rendait 1 412 m. C'est cette précision-là qu'un seuil de 50 m réclame.
        double d = ConversionGeographique.distanceMetres(44.453971, 6.306936, 44.44544392, 6.293767361);

        assertThat(d).isCloseTo(1412.0, within(2.0));
    }

    @Test
    @DisplayName("le demi-côté dessiné vaut 1 km, à la précision ronde qui suffit pour une boîte")
    void demi_cote_dessine_vaut_un_kilometre() {
        // 1 km en degrés de latitude, valeur arrondie : les emprises de carte s'en contentent depuis
        // #325, et les changer déplacerait des dessins sans rien corriger.
        assertThat(ConversionGeographique.degresDeLatitudePour(1.0)).isCloseTo(1.0 / 111.0, within(1e-9));
        // À 45° de latitude, un degré de longitude vaut cos(45°) fois moins.
        assertThat(ConversionGeographique.degresDeLongitudePour(1.0, 45.0))
                .isCloseTo(1.0 / (111.0 * Math.cos(Math.toRadians(45.0))), within(1e-9));
    }

    @Test
    @DisplayName("les deux précisions diffèrent, et c'est le fait que ce type existe pour porter")
    void les_deux_precisions_different() {
        // 111 000 contre 111 132 : 0,12 %, soit 130 m sur une maille de 2 km. Invisible pour un
        // dessin, significatif face à un seuil de 50 m. Unifier sans le savoir ferait dériver l'un
        // ou déplacer l'autre.
        assertThat(ConversionGeographique.METRES_PAR_DEGRE_LAT)
                .isNotEqualTo(ConversionGeographique.KM_PAR_DEGRE_LAT_DESSIN * 1_000);
    }
}

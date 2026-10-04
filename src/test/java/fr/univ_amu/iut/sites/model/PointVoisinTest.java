package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Un point déjà posé à 40 m au plus se signale à la création d'un autre (#5688) : 40 m est le rayon que
/// le portail utilise pour rattacher un point à un nom.
class PointVoisinTest {

    /// Un mètre vers le nord, en degrés de latitude, sur la sphère de rayon moyen 6 371 km.
    private static final double METRE = 1.0 / 111_194.93;

    private static final double LAT = 43.4;
    private static final double LON = 5.4;

    @Test
    @DisplayName("#5688 : un point à 39 m se signale")
    void a_39_metres() {
        assertThat(PointVoisin.lePlusProche(LAT, LON, List.of(point("Z1", LAT + 39 * METRE))))
                .hasValueSatisfying(voisin -> assertThat(voisin.point().code()).isEqualTo("Z1"));
    }

    @Test
    @DisplayName("#5688 : un point à 41 m ne se signale pas")
    void a_41_metres() {
        assertThat(PointVoisin.lePlusProche(LAT, LON, List.of(point("Z1", LAT + 41 * METRE))))
                .isEmpty();
    }

    @Test
    @DisplayName("#5688 : de deux voisins, le plus proche est nommé, avec sa distance arrondie au mètre")
    void le_plus_proche_est_nomme() {
        assertThat(PointVoisin.lePlusProche(
                        LAT,
                        LON,
                        List.of(point("Z1", LAT + 30 * METRE), point("Z2", LAT + 10 * METRE), sansPosition())))
                .hasValueSatisfying(voisin -> {
                    assertThat(voisin.point().code()).isEqualTo("Z2");
                    assertThat(voisin.metres()).isEqualTo(10);
                });
    }

    @Test
    @DisplayName("#5688 : à 40 m exactement, le point se signale encore")
    void a_40_metres_exactement() {
        assertThat(PointVoisin.lePlusProche(LAT, LON, List.of(point("Z1", LAT + 40 * METRE))))
                .hasValueSatisfying(voisin -> assertThat(voisin.metres()).isEqualTo(40));
    }

    /// La distance compte aussi d'est en ouest : à cette latitude, un degré de longitude est plus court
    /// qu'un degré de latitude, et une distance qui l'ignorerait se tromperait d'un quart.
    @Test
    @DisplayName("#5688 : la distance compte d'est en ouest, 30 m à l'est se signale et 41 m non")
    void la_distance_compte_d_est_en_ouest() {
        double metreEst = METRE / Math.cos(Math.toRadians(LAT));

        assertThat(PointVoisin.lePlusProche(LAT, LON, List.of(aLEst("Z1", 30 * metreEst))))
                .hasValueSatisfying(voisin -> assertThat(voisin.metres()).isEqualTo(30));
        assertThat(PointVoisin.lePlusProche(LAT, LON, List.of(aLEst("Z1", 41 * metreEst))))
                .isEmpty();
    }

    private static PointDEcoute aLEst(String code, double ecartDeLongitude) {
        return new PointDEcoute(null, code, LAT, LON + ecartDeLongitude, null, 1L, false);
    }

    private static PointDEcoute point(String code, double latitude) {
        return new PointDEcoute(null, code, latitude, LON, null, 1L, false);
    }

    private static PointDEcoute sansPosition() {
        return new PointDEcoute(null, "A1", null, null, null, 1L, false);
    }
}

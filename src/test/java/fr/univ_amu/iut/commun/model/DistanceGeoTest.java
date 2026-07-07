package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Distance orthodromique (Haversine) entre deux positions GPS (#154).
class DistanceGeoTest {

    @Test
    @DisplayName("Distance nulle pour deux positions identiques")
    void identiques_donnent_zero() {
        assertThat(DistanceGeo.metresEntre(43.401, -1.574, 43.401, -1.574)).isZero();
    }

    @Test
    @DisplayName("Un degré de latitude vaut environ 111,2 km")
    void un_degre_de_latitude() {
        assertThat(DistanceGeo.metresEntre(0, 0, 1, 0)).isCloseTo(111_195, within(50.0));
    }

    @Test
    @DisplayName("Paris → Lyon ≈ 392 km")
    void paris_lyon() {
        double metres = DistanceGeo.metresEntre(48.8566, 2.3522, 45.7640, 4.8357);
        assertThat(metres).isCloseTo(392_000, within(2_000.0));
    }

    @Test
    @DisplayName("Deux points distants d'environ 100 m")
    void petite_distance() {
        // ~0,0009° de latitude ≈ 100 m.
        assertThat(DistanceGeo.metresEntre(43.4010, -1.5740, 43.4019, -1.5740)).isCloseTo(100, within(5.0));
    }

    @Test
    @DisplayName("La distance est symétrique")
    void symetrique() {
        double aVersB = DistanceGeo.metresEntre(43.401, -1.574, 43.5298, 5.4474);
        double bVersA = DistanceGeo.metresEntre(43.5298, 5.4474, 43.401, -1.574);
        assertThat(aVersB).isCloseTo(bVersA, within(1e-6));
    }
}

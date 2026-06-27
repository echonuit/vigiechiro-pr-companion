package fr.univ_amu.iut.commun.view.carte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests du fournisseur de repli [EmpriseAutourDesPoints] (#152) : emprise 2 km centrée sur le barycentre
/// des points géolocalisés, contenant ces points ; vide sans aucun point exploitable.
class EmpriseAutourDesPointsTest {

    private final FournisseurEmpriseCarre fournisseur = new EmpriseAutourDesPoints();

    private static PointGeo point(double latitude, double longitude) {
        return new PointGeo("P", latitude, longitude, Color.RED);
    }

    @Test
    @DisplayName("emprise 2 km centrée sur le barycentre, contenant les points")
    void emprise_centree_contient_les_points() {
        // Deux points dans le département 64 (Pyrénées-Atlantiques), ~1 km d'écart.
        EmpriseCarre emprise = fournisseur
                .emprise("640380", List.of(point(43.300, -0.360), point(43.310, -0.350)))
                .orElseThrow();

        assertThat(emprise.latCentre()).isCloseTo(43.305, within(1e-6));
        assertThat(emprise.lonCentre()).isCloseTo(-0.355, within(1e-6));
        // Côté latitude ≈ 2 km soit ≈ 2/111 degré.
        assertThat(emprise.latMax() - emprise.latMin()).isCloseTo(2.0 / 111.0, within(1e-4));
        assertThat(emprise.contient(43.300, -0.360)).isTrue();
        assertThat(emprise.contient(43.310, -0.350)).isTrue();
    }

    @Test
    @DisplayName("#152 (P3) : l'emprise contient TOUS les points, même répartis de façon asymétrique")
    void emprise_contient_les_points_asymetriques() {
        // Deux points groupés + un excentré (étalement ~1,9 km) : un carré 2 km centré sur la MOYENNE
        // exclurait l'excentré. Centré sur la boîte englobante, l'emprise doit tous les contenir.
        List<PointGeo> points = List.of(point(43.300, -0.360), point(43.300, -0.360), point(43.317, -0.345));

        EmpriseCarre emprise = fournisseur.emprise("640380", points).orElseThrow();

        for (PointGeo p : points) {
            assertThat(emprise.contient(p.latitude(), p.longitude()))
                    .as("le point (%s, %s) doit être dans l'emprise", p.latitude(), p.longitude())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("sans point géolocalisé (liste vide ou GPS manquant), pas d'emprise")
    void sans_point_geolocalise_vide() {
        assertThat(fournisseur.emprise("640380", List.of())).isEmpty();
        assertThat(fournisseur.emprise("640380", List.of(point(Double.NaN, Double.NaN))))
                .as("un point au GPS manquant (NaN) n'ancre pas l'emprise")
                .isEmpty();
    }
}

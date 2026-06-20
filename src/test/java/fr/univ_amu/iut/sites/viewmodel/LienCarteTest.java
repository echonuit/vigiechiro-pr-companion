package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LienCarteTest {

    @Test
    @DisplayName("l'URL OSM centre la carte et pose un marqueur sur le point")
    void osm_centre_et_marque_le_point() {
        assertThat(LienCarte.osm(43.5, 5.4))
                .isEqualTo("https://www.openstreetmap.org/?mlat=43.5&mlon=5.4#map=16/43.5/5.4");
    }

    @Test
    @DisplayName("le séparateur décimal est toujours le point (indépendant de la locale)")
    void osm_utilise_le_point_decimal() {
        String url = LienCarte.osm(-12.3456, 7.0);
        assertThat(url).contains("mlat=-12.3456").contains("mlon=7.0").doesNotContain(",");
    }
}

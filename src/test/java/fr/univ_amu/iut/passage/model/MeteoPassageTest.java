package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de l'accès typé à la **donnée météo optionnelle** d'un passage (#106) : lecture de la clé
/// `tempDebut` dans l'objet `weather_data`, écriture préservant les autres clés, rejet des valeurs non
/// finies, lecture de saisie stricte.
class MeteoPassageTest {

    @Test
    @DisplayName("temperatureDebutNuit lit la clé tempDebut d'un objet météo (tolérant)")
    void lecture_cle_tempDebut() {
        assertThat(MeteoPassage.temperatureDebutNuit(null)).isNull();
        assertThat(MeteoPassage.temperatureDebutNuit("")).isNull();
        assertThat(MeteoPassage.temperatureDebutNuit("{}")).isNull();
        assertThat(MeteoPassage.temperatureDebutNuit("{\"autre\":\"x\"}")).isNull();
        assertThat(MeteoPassage.temperatureDebutNuit("{\"tempDebut\":18.5}")).isEqualTo(18.5);
        assertThat(MeteoPassage.temperatureDebutNuit("{\"tempDebut\":-3,\"hygro\":80}"))
                .isEqualTo(-3.0);
        // Valeur non finie stockée en base : ignorée (jamais NaN °C à l'affichage).
        assertThat(MeteoPassage.temperatureDebutNuit("{\"tempDebut\":NaN}")).isNull();
    }

    @Test
    @DisplayName("definir met à jour tempDebut en PRÉSERVANT les autres clés ; null l'efface")
    void ecriture_preserve_les_autres_cles() {
        assertThat(MeteoPassage.definir(null, 8.5)).isEqualTo("{\"tempDebut\":8.5}");

        String avec = MeteoPassage.definir("{\"hygro\":80}", 8.5);
        assertThat(avec).contains("\"hygro\":80").contains("\"tempDebut\":8.5");
        assertThat(MeteoPassage.temperatureDebutNuit(avec)).as("round-trip").isEqualTo(8.5);

        String efface = MeteoPassage.definir("{\"hygro\":80,\"tempDebut\":8.5}", null);
        assertThat(efface).contains("\"hygro\":80").doesNotContain("tempDebut");

        assertThat(MeteoPassage.definir("{\"tempDebut\":8.5}", null))
                .as("objet devenu vide → colonne effacée")
                .isNull();
    }

    @Test
    @DisplayName("definir refuse une température non finie (NaN/Infini)")
    void ecriture_refuse_non_finie() {
        assertThatThrownBy(() -> MeteoPassage.definir(null, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MeteoPassage.definir(null, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("lireSaisie : vide → null ; virgule/point acceptés ; lève si non numérique OU non fini")
    void lecture_saisie_stricte() {
        assertThat(MeteoPassage.lireSaisie("")).isNull();
        assertThat(MeteoPassage.lireSaisie(null)).isNull();
        assertThat(MeteoPassage.lireSaisie("  9,0 ")).isEqualTo(9.0);
        assertThatThrownBy(() -> MeteoPassage.lireSaisie("froid")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> MeteoPassage.lireSaisie("NaN")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> MeteoPassage.lireSaisie("Infinity")).isInstanceOf(NumberFormatException.class);
    }
}

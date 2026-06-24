package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de l'accès typé à la **donnée météo optionnelle** d'un passage (#106) : lecture tolérante,
/// sérialisation déterministe, lecture de saisie stricte.
class MeteoPassageTest {

    @Test
    @DisplayName("temperatureDebutNuit : lecture tolérante (null/vide/illisible → null, nombre sinon)")
    void lecture_toleranre() {
        assertThat(MeteoPassage.temperatureDebutNuit(null)).isNull();
        assertThat(MeteoPassage.temperatureDebutNuit("  ")).isNull();
        assertThat(MeteoPassage.temperatureDebutNuit("pas un nombre")).isNull();
        assertThat(MeteoPassage.temperatureDebutNuit("8.5")).isEqualTo(8.5);
        assertThat(MeteoPassage.temperatureDebutNuit("8,5")).isEqualTo(8.5);
        assertThat(MeteoPassage.temperatureDebutNuit("-3")).isEqualTo(-3.0);
    }

    @Test
    @DisplayName("serialiser : null → null ; sinon point décimal indépendant de la locale (round-trip)")
    void serialisation_deterministe() {
        assertThat(MeteoPassage.serialiser(null)).isNull();
        assertThat(MeteoPassage.serialiser(8.5)).isEqualTo("8.5");
        assertThat(MeteoPassage.temperatureDebutNuit(MeteoPassage.serialiser(12.3)))
                .isEqualTo(12.3);
    }

    @Test
    @DisplayName("lireSaisie : vide → null (efface) ; virgule/point acceptés ; lève si non numérique")
    void lecture_saisie_stricte() {
        assertThat(MeteoPassage.lireSaisie("")).isNull();
        assertThat(MeteoPassage.lireSaisie(null)).isNull();
        assertThat(MeteoPassage.lireSaisie("  9,0 ")).isEqualTo(9.0);
        assertThat(MeteoPassage.lireSaisie("9.0")).isEqualTo(9.0);
        assertThatThrownBy(() -> MeteoPassage.lireSaisie("froid")).isInstanceOf(NumberFormatException.class);
    }
}

package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// La lecture d'une hauteur de micro saisie en texte libre (#6211), sur les formes que la base porte.
class LectureHauteurTest {

    @ParameterizedTest(name = "« {0} » → {1} m")
    @CsvSource(
            delimiter = '|',
            value = {
                "4 | 4.0",
                "2.5 | 2.5",
                "2,5 | 2.5",
                "2,50 | 2.5",
                "2. | 2.0",
                "2,5m | 2.5",
                "2.5 m | 2.5",
                "3 mètres | 3.0",
                "2m50 | 2.5",
                "1m5 | 1.5",
                "250cm | 2.5",
                "0 | 0.0",
                "0,5 | 0.5",
                "20 | 20.0",
                "29.9 | 29.9",
                "30 | 0.3",
                "50 | 0.5",
                "250 | 2.5",
                "40m | 40.0"
            })
    @DisplayName("#6211 : virgule, unité, « 2m50 », et centimètres sans unité à partir de 30")
    void lit_une_hauteur(String saisie, double metres) {
        assertThat(LectureHauteur.enMetres(saisie)).contains(metres);
    }

    @ParameterizedTest(name = "« {0} »")
    @ValueSource(strings = {"-1", "-0,5", "2-3", "2?", "haut", "2,5,1", "2..5"})
    @DisplayName("#6211 : une valeur négative ou d'une autre forme ne se lit pas")
    void ne_lit_pas_une_autre_forme(String saisie) {
        assertThat(LectureHauteur.enMetres(saisie)).isEmpty();
    }

    @ParameterizedTest(name = "« {0} »")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("#6211 : une hauteur absente ou blanche ne se lit pas")
    void ne_lit_pas_une_hauteur_absente(String saisie) {
        assertThat(LectureHauteur.enMetres(saisie)).isEmpty();
    }
}

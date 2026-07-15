package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import fr.univ_amu.iut.commun.model.Progression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Progression du téléchargement des observations (#1534) : déterminée « page XX/YY » quand le total est
/// connu, approchée sinon (elle avance à chaque page sans jamais atteindre la fin de sa plage).
class ProgressionTelechargementTest {

    @Test
    @DisplayName("Total connu : libellé « page XX/YY » et barre exacte sur [0,10 ; 0,90]")
    void determinee_avec_total() {
        Progression progression = ProgressionTelechargement.pour(2, 4);

        assertThat(progression.libelle()).isEqualTo("Téléchargement des observations (page 2/4)…");
        assertThat(progression.fraction()).isCloseTo(0.10 + 0.80 * 2 / 4.0, within(1e-9));
    }

    @Test
    @DisplayName("Total inconnu (0) : libellé « page XX » et avancement approché, croissant, sous 0,90")
    void approchee_sans_total() {
        Progression premiere = ProgressionTelechargement.pour(1, 0);
        Progression dixieme = ProgressionTelechargement.pour(10, 0);

        assertThat(premiere.libelle()).isEqualTo("Téléchargement des observations (page 1)…");
        assertThat(premiere.fraction()).isGreaterThan(0.10).isLessThan(0.90);
        assertThat(dixieme.fraction())
                .as("la barre avance à chaque page, sans atteindre la fin de sa plage")
                .isGreaterThan(premiere.fraction())
                .isLessThan(0.90);
    }
}

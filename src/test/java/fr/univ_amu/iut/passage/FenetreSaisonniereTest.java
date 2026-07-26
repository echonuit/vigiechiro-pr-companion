package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.passage.model.FenetreSaisonniere;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Table des fenêtres calendaires R3, source unique consommée par la vérification de passage et par le
/// solde de saison. Les bornes (15/06-31/07, 15/08-30/09) sont figées ici : ce test les verrouille.
class FenetreSaisonniereTest {

    @Test
    @DisplayName("passage 1 : fenêtre du 15 juin au 31 juillet")
    void fenetre_passage_1() {
        FenetreSaisonniere fenetre = FenetreSaisonniere.pour(1, 2026).orElseThrow();
        assertThat(fenetre.debut()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(fenetre.fin()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("passage 2 : fenêtre du 15 août au 30 septembre")
    void fenetre_passage_2() {
        FenetreSaisonniere fenetre = FenetreSaisonniere.pour(2, 2026).orElseThrow();
        assertThat(fenetre.debut()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(fenetre.fin()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    @DisplayName("un numéro sans fenêtre définie (autre que 1 ou 2) renvoie vide")
    void numero_sans_fenetre() {
        assertThat(FenetreSaisonniere.pour(3, 2026)).isEmpty();
        assertThat(FenetreSaisonniere.pour(0, 2026)).isEmpty();
    }

    @Test
    @DisplayName("contient : bornes incluses, dehors exclu")
    void contient_bornes_incluses() {
        FenetreSaisonniere fenetre = FenetreSaisonniere.pour(1, 2026).orElseThrow();
        assertThat(fenetre.contient(LocalDate.of(2026, 6, 15)))
                .as("borne début incluse")
                .isTrue();
        assertThat(fenetre.contient(LocalDate.of(2026, 7, 31)))
                .as("borne fin incluse")
                .isTrue();
        assertThat(fenetre.contient(LocalDate.of(2026, 7, 1))).isTrue();
        assertThat(fenetre.contient(LocalDate.of(2026, 6, 14)))
                .as("veille du début")
                .isFalse();
        assertThat(fenetre.contient(LocalDate.of(2026, 8, 1)))
                .as("lendemain de la fin")
                .isFalse();
    }
}

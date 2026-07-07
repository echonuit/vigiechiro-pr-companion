package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Rendu du bilan d'`importer-tadarida` (#616). `rendreBilan` est une fonction pure, testée sur des
/// [BilanImport] construits à la main : import simple (sans compteurs de validations) et réimport (avec).
class ImporterTadaridaTest {

    private static ResultatsIdentification resultats() {
        return new ResultatsIdentification(9L, "observations.csv", "\"Brut\"", "2026-07-07T10:00:00", 42L);
    }

    @Test
    @DisplayName("Import simple : compteurs importées/ignorées/taxons, sans ligne « Validations »")
    void bilan_import_simple() {
        BilanImport bilan = new BilanImport(resultats(), 12, 3, 1);

        String texte = ImporterTadarida.rendreBilan(bilan, false);

        assertThat(texte)
                .contains("jeu #9")
                .contains("Observations importées")
                .contains("12")
                .contains("Lignes ignorées")
                .contains("3 (séquence audio absente ou ligne sans taxon)")
                .contains("Taxons hors référentiel")
                .doesNotContain("Validations");
    }

    @Test
    @DisplayName("Réimport : ajoute les compteurs de validations préservées et perdues")
    void bilan_reimport() {
        BilanImport bilan = new BilanImport(resultats(), 10, 2, 0, 5, 1);

        String texte = ImporterTadarida.rendreBilan(bilan, true);

        assertThat(texte)
                .contains("Validations préservées")
                .contains("5")
                .contains("Validations perdues")
                .contains("1");
    }
}

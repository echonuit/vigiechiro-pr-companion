package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

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

    @Test
    @DisplayName("Passage déjà pourvu d'un jeu, sans --remplacer : refus (relancez avec --remplacer), sans réimporter")
    void deja_pourvu_sans_remplacer_refuse(@TempDir Path dossier) throws IOException {
        ServiceValidation service = mock(ServiceValidation.class);
        ResultatsIdentificationDao resultatsDao = mock(ResultatsIdentificationDao.class);
        when(resultatsDao.findByPassage(42L)).thenReturn(Optional.of(resultats()));
        Path csv = dossier.resolve("obs.csv");
        Files.writeString(csv, "\"nom du fichier\";\"tadarida_taxon\"\n");

        int code = new CommandLine(new ImporterTadarida(service, resultatsDao))
                .execute("--passage", "42", "--csv", csv.toString());

        assertThat(code).isNotZero();
        verify(service, never()).importer(anyLong(), any());
        verify(service, never()).reimporter(anyLong(), any());
    }
}

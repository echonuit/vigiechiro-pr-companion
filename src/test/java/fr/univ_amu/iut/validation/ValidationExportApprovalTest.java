package fr.univ_amu.iut.validation;

import fr.univ_amu.iut.validation.model.ExportVuCsv;
import fr.univ_amu.iut.validation.model.ParserCsvTadarida;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.approvaltests.Approvals;
import org.approvaltests.reporters.QuietReporter;
import org.approvaltests.reporters.UseReporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Golden master (ApprovalTests) du CSV `_Vu` exporté à partir des 473 observations réelles.
/// Verrouille la sortie canonique de [ExportVuCsv] : tout changement de format (ordre des
/// colonnes, sérialisation des nombres, gestion des champs vides) fait diverger le
/// `.received.txt` du `.approved.txt` et casse le test.
///
/// [QuietReporter] : pas de lancement d'outil de diff graphique (compatible CI headless). Pour
/// (re)générer la référence après un changement de format **assumé** : supprimer le
/// `.approved.txt`, relancer, puis renommer le `.received.txt` produit en `.approved.txt`.
@UseReporter(QuietReporter.class)
class ValidationExportApprovalTest {

  @Test
  @DisplayName("L'export _Vu des 473 observations réelles est stable (golden master)")
  void le_csv_vu_exporte_est_stable() throws URISyntaxException {
    Path brut =
        Path.of(
            ValidationExportApprovalTest.class
                .getResource("/validation/observations_brut.csv")
                .toURI());

    ParserCsvTadarida parser = new ParserCsvTadarida();
    ExportVuCsv export = new ExportVuCsv();

    Approvals.verify(export.versChaine(parser.parser(brut).lignes()));
  }
}

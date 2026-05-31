package fr.univ_amu.iut.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.diagnostic.model.AnalyseAnomalies;
import fr.univ_amu.iut.diagnostic.model.ExportDiagnostic;
import fr.univ_amu.iut.diagnostic.model.LectureThLog;
import fr.univ_amu.iut.diagnostic.model.MesureClimatique;
import fr.univ_amu.iut.diagnostic.model.SerieClimatique;
import java.nio.file.Path;
import java.util.List;
import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.approvaltests.reporters.QuietReporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Validation par golden master (ApprovalTests) de l'export CSV du diagnostic (P6-CA6). La série
/// climatique est lue depuis le THLog réel (ressource de test `PaRecPR1925492_THLog.csv`) puis
/// exportée ; le CSV produit est comparé au fichier `*.approved.txt` versionné. Reporter
/// silencieux pour rester exécutable en CI headless.
class ExportDiagnosticTest {

  private static final Options OPTIONS = new Options(new QuietReporter());

  private static List<MesureClimatique> mesuresReelles() {
    try {
      Path thLog =
          Path.of(ExportDiagnosticTest.class.getResource("PaRecPR1925492_THLog.csv").toURI());
      return LectureThLog.lire(thLog);
    } catch (Exception e) {
      throw new IllegalStateException("Ressource THLog de test introuvable", e);
    }
  }

  @Test
  @DisplayName("Golden : export CSV de la série climatique réelle (déterministe)")
  void golden_serie_climatique() {
    String csv = ExportDiagnostic.climatVersCsv(SerieClimatique.presente(mesuresReelles()));
    Approvals.verify(csv, OPTIONS);
  }

  @Test
  @DisplayName("Golden : export CSV des anomalies classées")
  void golden_anomalies() {
    AnalyseAnomalies analyse =
        new AnalyseAnomalies(
            List.of(
                "Réveil non programmé : Wakeup capteur",
                "Erreur SD : échec d'écriture sur la carte SD",
                "Redémarrage inattendu détecté",
                "Batterie faible (12%) : Batteries internes 12%"),
            List.of());
    String csv = ExportDiagnostic.anomaliesVersCsv(analyse);
    Approvals.verify(csv, OPTIONS);
  }

  @Test
  @DisplayName("Export d'une série absente : seule l'entête CSV")
  void export_serie_absente() {
    String csv = ExportDiagnostic.climatVersCsv(SerieClimatique.absente());
    assertThat(csv).isEqualTo("Date;Heure;Temperature_C;Humidite_pct\n");
  }
}

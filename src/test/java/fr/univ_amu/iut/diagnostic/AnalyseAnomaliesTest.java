package fr.univ_amu.iut.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.JsonSimple;
import fr.univ_amu.iut.diagnostic.model.AnalyseAnomalies;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests purs de [AnalyseAnomalies] (R19) : classement par familles d'anomalies et tolérance au
/// journal circulaire (rien à reconstituer si les entrées manquent).
class AnalyseAnomaliesTest {

  @Test
  @DisplayName("R19 : classe les anomalies par famille (réveil, SD, redémarrage, batterie)")
  void classe_par_famille() {
    List<String> anomalies =
        List.of(
            "Réveil non programmé : 23/04/26 - 03:12:00 PR1925492 Wakeup capteur",
            "Erreur SD : échec d'écriture sur la carte SD",
            "Redémarrage inattendu détecté après coupure",
            "Batterie faible (12%) : Batteries internes 12%");

    AnalyseAnomalies analyse =
        AnalyseAnomalies.depuisJournal(
            new JournalDuCapteur(1L, "LogPR1925492.txt", "[]", JsonSimple.tableau(anomalies), 7L));

    assertThat(analyse.aDesAnomalies()).isTrue();
    assertThat(analyse.anomalies()).hasSize(4);
    assertThat(analyse.reveilsNonProgrammes()).hasSize(1).first().asString().contains("Wakeup");
    assertThat(analyse.erreursSD()).hasSize(1).first().asString().contains("carte SD");
    assertThat(analyse.redemarrages()).hasSize(1).first().asString().contains("Redémarrage");
    assertThat(analyse.alertesBatterie()).hasSize(1).first().asString().contains("12%");
  }

  @Test
  @DisplayName("R19 : journal sans anomalie → listes vides, pas de reconstitution")
  void journal_sans_anomalie() {
    AnalyseAnomalies analyse =
        AnalyseAnomalies.depuisJournal(
            new JournalDuCapteur(1L, "LogPR1925492.txt", "[\"### Démarrage\"]", "[]", 7L));

    assertThat(analyse.aDesAnomalies()).isFalse();
    assertThat(analyse.anomalies()).isEmpty();
    assertThat(analyse.reveilsNonProgrammes()).isEmpty();
    assertThat(analyse.evenements()).containsExactly("### Démarrage");
  }

  @Test
  @DisplayName("Analyse vide quand aucun journal n'est exploitable")
  void analyse_vide() {
    AnalyseAnomalies vide = AnalyseAnomalies.vide();

    assertThat(vide.aDesAnomalies()).isFalse();
    assertThat(vide.anomalies()).isEmpty();
    assertThat(vide.evenements()).isEmpty();
  }

  @Test
  @DisplayName("Colonnes JSON nulles tolérées (journal tronqué)")
  void colonnes_nulles_tolerees() {
    AnalyseAnomalies analyse =
        AnalyseAnomalies.depuisJournal(new JournalDuCapteur(1L, "LogPR.txt", null, null, 7L));

    assertThat(analyse.anomalies()).isEmpty();
    assertThat(analyse.evenements()).isEmpty();
  }
}

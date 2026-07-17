package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.JournalParse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de l'analyseur du journal du capteur `LogPR<n>.txt` (C9, R19).
///
/// Les lignes sont fournies **en dur** (reproduction fidèle du format réel observé sur le sample
/// `LogPR1925492.txt` du brief) pour rester self-contained : la CI ne dépend d'aucun fichier hors
/// dépôt.
class AnalyseurLogPRTest {

    private final AnalyseurLogPR analyseur = new AnalyseurLogPR();

    /// Extrait représentatif du journal réel (nuit du 22/04/2026, enregistreur 1925492).
    private static List<String> journalNominal() {
        return List.of(
                "22/04/26 - 16:02:20 PR1925492 Test accès carte SD ",
                "22/04/26 - 16:02:20 PR1925492 ==========================================",
                "22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492, V1.01,"
                        + " CPU 600000000, T4.1",
                "22/04/26 - 16:02:21 PR1925492 ### Passage en mode Protocole Point fixe",
                "22/04/26 - 16:02:21 PR1925492 Sonde température/hygrométrie présente, lecture toutes les" + " 600s",
                "22/04/26 - 16:02:21 PR1925492 Batteries internes 4.1V (90%) (MCP3221)",
                "22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz FL N FPH 00, S."
                        + " R. 16dB 1dt. GN0, Bd. Freq. 8-120kHz, Wav 2-30s SD 99%",
                "22/04/26 - 20:26:13 PR1925492 Wakeup by ALARM... Cpt 1",
                "23/04/26 - 07:48:00 PR1925492 ### Passage en mode Veille",
                "23/04/26 - 07:52:21 PR1925492 Mise en veille, réveil à 20:25, Bat. Interne 4.0 90%");
    }

    @Test
    @DisplayName("Identité de l'enregistreur et paramètres d'acquisition extraits du journal nominal")
    void parse_le_journal_nominal() {
        JournalParse journal = analyseur.analyser(journalNominal());

        assertThat(journal.numeroSerie()).isEqualTo("1925492");
        assertThat(journal.versionModele()).isEqualTo("V1.01, T4.1");
        assertThat(journal.dateDebut()).isEqualTo(LocalDate.of(2026, 4, 22));
        assertThat(journal.heureDebut()).isEqualTo("20:25:00");
        assertThat(journal.heureFin()).isEqualTo("07:47:00");
        assertThat(journal.frequenceEchantillonnageHz()).isEqualTo(384000);
        assertThat(journal.bandePassante()).isEqualTo("8-120kHz");
        assertThat(journal.sensibilite()).isEqualTo("16dB 1dt. GN0");
        assertThat(journal.sondePresente()).isTrue();
    }

    @Test
    @DisplayName("R19 : un journal nominal (sonde présente, batterie OK, réveils ALARM) sans anomalie")
    void journal_nominal_sans_anomalie() {
        JournalParse journal = analyseur.analyser(journalNominal());

        assertThat(journal.aDesAnomalies()).isFalse();
        assertThat(journal.anomalies()).isEmpty();
        assertThat(journal.evenements()).isNotEmpty(); // changements de mode + réveils conservés
    }

    @Test
    @DisplayName("Les paramètres d'acquisition sont sérialisés en JSON (colonne acquisition_params)")
    void parametres_serialises_en_json() {
        JournalParse journal = analyseur.analyser(journalNominal());

        assertThat(journal.parametresAcquisitionJson())
                .contains("\"feHz\":\"384000\"")
                .contains("\"fenetre\":\"20:25:00-07:47:00\"")
                .contains("8-120kHz");
    }

    @Test
    @DisplayName("R19/R20 : sonde absente, batterie faible et réveil non programmé lèvent des anomalies")
    void detecte_les_anomalies() {
        List<String> journalDegrade = List.of(
                "22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492,"
                        + " V1.01, CPU 600000000, T4.1",
                "22/04/26 - 16:02:21 PR1925492 Sonde température/hygrométrie absente",
                "22/04/26 - 16:02:21 PR1925492 Batteries internes 3.2V (12%) (MCP3221)",
                "22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz, Bd. Freq." + " 8-120kHz",
                "22/04/26 - 02:13:05 PR1925492 Wakeup by WATCHDOG... Cpt 7");

        JournalParse journal = analyseur.analyser(journalDegrade);

        assertThat(journal.sondePresente()).isFalse();
        assertThat(journal.aDesAnomalies()).isTrue();
        assertThat(journal.messagesAnomalies())
                .anyMatch(a -> a.contains("Sonde"))
                .anyMatch(a -> a.contains("Batterie faible"))
                .anyMatch(a -> a.contains("Réveil non programmé"));
        // #258 : les anomalies sont affichées dans la liste du diagnostic → pas de code de règle visible.
        assertThat(journal.messagesAnomalies()).allSatisfy(a -> assertThat(a).doesNotContain("R20", "R19"));
    }

    @Test
    @DisplayName("#1696 : évènements/anomalies filtrables par nuit ; une entrée de déploiement reste sur chaque nuit")
    void journal_filtrable_par_nuit() {
        List<String> journal = List.of(
                "22/04/26 - 20:30:00 PR1925492 Sonde température/hygrométrie absente",
                "22/04/26 - 20:31:00 PR1925492 ### demarrage soir22",
                "23/04/26 - 03:00:00 PR1925492 Wakeup by WATCHDOG Cpt3 nuit22", // avant midi → nuit du 22
                "23/04/26 - 21:00:00 PR1925492 ### changement soir23",
                "24/04/26 - 02:00:00 PR1925492 Wakeup by WATCHDOG Cpt5 nuit23"); // avant midi → nuit du 23

        JournalParse j = analyseur.analyser(journal);

        // Évènements rangés par nuit (bascule midi : le réveil du 23/04 03:00 appartient à la nuit du 22).
        assertThat(j.evenementsJsonPourNuit(LocalDate.of(2026, 4, 22)))
                .contains("soir22")
                .contains("nuit22")
                .doesNotContain("soir23", "nuit23");
        assertThat(j.evenementsJsonPourNuit(LocalDate.of(2026, 4, 23)))
                .contains("soir23")
                .contains("nuit23")
                .doesNotContain("soir22", "nuit22");
        // Réveil non programmé (horodaté) : rangé dans sa nuit.
        assertThat(j.anomaliesJsonPourNuit(LocalDate.of(2026, 4, 22)))
                .contains("nuit22")
                .doesNotContain("nuit23");
        // Anomalie de déploiement (sonde absente, non datée) : présente sur chaque nuit.
        assertThat(j.anomaliesJsonPourNuit(LocalDate.of(2026, 4, 22))).contains("Sonde");
        assertThat(j.anomaliesJsonPourNuit(LocalDate.of(2026, 4, 23))).contains("Sonde");
    }

    @Test
    @DisplayName("Un journal sans numéro de série est rejeté (inexploitable)")
    void journal_sans_serie_rejete() {
        assertThatThrownBy(() -> analyseur.analyser(List.of("ligne sans format reconnaissable")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

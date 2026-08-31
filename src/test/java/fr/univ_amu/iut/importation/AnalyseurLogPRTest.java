package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.ConfigurationAcquisition;
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

    /// Une carte laissée **plusieurs nuits** au même point, ce que le protocole Point Fixe rend
    /// courant : le capteur redémarre, et repose ses paramètres. Ici la fréquence change entre les
    /// deux sessions, ce qui conditionne la transformation des séquences.
    private static List<String> journalDeuxSessions() {
        return List.of(
                "22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492, V1.01,"
                        + " CPU 600000000, T4.1",
                "22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz FL N FPH 00, S."
                        + " R. 16dB 1dt. GN0, Bd. Freq. 8-120kHz, Wav 2-30s SD 99%",
                "22/04/26 - 20:26:13 PR1925492 Wakeup by ALARM... Cpt 1",
                "23/04/26 - 07:48:00 PR1925492 ### Passage en mode Veille",
                // Seconde session : le capteur a été repris, reconfiguré, puis reposé.
                "25/04/26 - 17:10:00 PR1925492 Paramètres : Acquisi. 21:00-06:30, Fe256kHz FL N FPH 00, S."
                        + " R. 12dB 1dt. GN0, Bd. Freq. 10-96kHz, Wav 2-30s SD 80%",
                "25/04/26 - 21:01:00 PR1925492 Wakeup by ALARM... Cpt 1",
                "26/04/26 - 06:31:00 PR1925492 ### Passage en mode Veille");
    }

    @Test
    @DisplayName("#3460 : chaque nuit reçoit la config de SA session, pas celle de la première")
    void chaque_nuit_recoit_la_config_de_sa_session() {
        // Le défaut : « la lecture du log par companion se fait sur les premières lignes pour trouver
        // la config du PR ». Une nuit importée repartait donc avec la fréquence d'échantillonnage et la
        // bande passante d'une AUTRE session - des données fausses, en silence, jusqu'à la plateforme.
        JournalParse journal = analyseur.analyser(journalDeuxSessions());

        assertThat(journal.configurationPourNuit(LocalDate.of(2026, 4, 22)))
                .get()
                .extracting(
                        ConfigurationAcquisition::frequenceEchantillonnageHz, ConfigurationAcquisition::bandePassante)
                .containsExactly(384000, "8-120kHz");

        assertThat(journal.configurationPourNuit(LocalDate.of(2026, 4, 25)))
                .get()
                .extracting(
                        ConfigurationAcquisition::frequenceEchantillonnageHz,
                        ConfigurationAcquisition::bandePassante,
                        ConfigurationAcquisition::heureDebut)
                .containsExactly(256000, "10-96kHz", "21:00:00");
    }

    @Test
    @DisplayName("#3460 : une nuit SANS config posée avant elle prend la plus ancienne connue")
    void une_nuit_sans_config_anterieure_prend_la_plus_ancienne() {
        // Le journal est CIRCULAIRE (R19) : ses premières entrées peuvent avoir disparu, si bien qu'une
        // nuit peut n'avoir aucune configuration antérieure. La plus ancienne connue vaut mieux qu'un
        // vide silencieux - c'est un repli assumé, pas une certitude.
        JournalParse journal = analyseur.analyser(journalDeuxSessions());

        assertThat(journal.configurationPourNuit(LocalDate.of(2026, 4, 10)))
                .get()
                .extracting(ConfigurationAcquisition::frequenceEchantillonnageHz)
                .isEqualTo(384000);
    }

    @Test
    @DisplayName("#3460 : sans aucune ligne « Paramètres », il n'y a pas de config à rendre")
    void sans_ligne_parametres_aucune_config() {
        JournalParse journal = analyseur.analyser(
                List.of("22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492, V1.01,"
                        + " CPU 600000000, T4.1"));

        assertThat(journal.configurationPourNuit(LocalDate.of(2026, 4, 22))).isEmpty();
    }

    @Test
    @DisplayName("#3460 : le cas courant - une seule session - ne change pas de comportement")
    void une_seule_session_ne_change_rien() {
        // Le garde qui compte autant que les autres : la très grande majorité des cartes ne portent
        // qu'une session, et cette correction ne doit rien y déplacer.
        JournalParse journal = analyseur.analyser(journalNominal());

        assertThat(journal.configurationPourNuit(LocalDate.of(2026, 4, 22)))
                .get()
                .extracting(ConfigurationAcquisition::frequenceEchantillonnageHz, ConfigurationAcquisition::heureFin)
                .containsExactly(384000, "07:47:00");
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
                "22/04/26 - 02:13:05 PR1925492 Wakeup by unknow... Cpt 7");

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
    @DisplayName("#4981 : un réveil par touche n'est pas une anomalie, un réveil de cause inconnue en reste une")
    void un_reveil_par_touche_n_est_pas_une_anomalie() {
        // Les quatre motifs que `TeensyRecorder.ino` émet réellement. `PINPUSH` couvre toutes les
        // touches, et le firmware sort alors de la veille pour laisser l'observateur agir : c'est un
        // geste voulu. Les deux `unknow` gardent une cause inconnue, donc restent des anomalies.
        JournalParse voulu = analyseur.analyser(List.of(
                "22/04/26 - 20:30:00 PR1925492 Wakeup by ALARM... Cpt 1",
                "23/04/26 - 01:15:00 PR1925492 Wakeup by PINPUSH... Cpt 2"));

        assertThat(voulu.messagesAnomalies())
                .as("ni le réveil programmé ni celui par touche ne sont des anomalies")
                .noneMatch(message -> message.contains("Réveil non programmé"));

        JournalParse inconnu = analyseur.analyser(List.of(
                "22/04/26 - 20:30:00 PR1925492 Wakeup by unknow... Cpt 1",
                "23/04/26 - 01:15:00 PR1925492 Wakeup by unknow ISR... Cpt 2"));

        assertThat(inconnu.messagesAnomalies())
                .as("une cause inconnue reste ce que l'observateur doit savoir")
                .filteredOn(message -> message.contains("Réveil non programmé"))
                .hasSize(2);
    }

    @Test
    @DisplayName("#1696 : évènements/anomalies filtrables par nuit ; une entrée de déploiement reste sur chaque nuit")
    void journal_filtrable_par_nuit() {
        List<String> journal = List.of(
                "22/04/26 - 20:30:00 PR1925492 Sonde température/hygrométrie absente",
                "22/04/26 - 20:31:00 PR1925492 ### demarrage soir22",
                "23/04/26 - 03:00:00 PR1925492 Wakeup by unknow... Cpt 3 nuit22", // avant midi → nuit du 22
                "23/04/26 - 21:00:00 PR1925492 ### changement soir23",
                "24/04/26 - 02:00:00 PR1925492 Wakeup by unknow... Cpt 5 nuit23"); // avant midi → nuit du 23

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

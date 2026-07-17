package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie que le filtre THLog par nuit (#1696) range chaque mesure dans la bonne nuit (bascule midi)
/// et préserve l'entête, sur un relevé couvrant **deux nuits** (cas de la carte SD multi-nuits).
class FiltreThLogNuitTest {

    private static final List<String> THLOG_DEUX_NUITS = List.of(
            "Date\tHour\tTemperature\tHumidity",
            "22/06/2026\t22:30:00\t+16.1\t70", // nuit du 22/06 (soir)
            "23/06/2026\t05:30:00\t+11.9\t84", // nuit du 22/06 (matin, avant midi)
            "23/06/2026\t22:30:00\t+15.0\t72", // nuit du 23/06 (soir)
            "24/06/2026\t05:30:00\t+10.0\t88"); // nuit du 23/06 (matin)

    @Test
    @DisplayName("Ne garde que les mesures de la nuit demandée, entête comprise (bascule midi)")
    void filtre_sur_la_nuit_du_soir() {
        List<String> nuit1 = FiltreThLogNuit.filtrer(THLOG_DEUX_NUITS, LocalDate.of(2026, 6, 22));

        assertThat(nuit1)
                .containsExactly(
                        "Date\tHour\tTemperature\tHumidity",
                        "22/06/2026\t22:30:00\t+16.1\t70",
                        "23/06/2026\t05:30:00\t+11.9\t84");
    }

    @Test
    @DisplayName("La nuit suivante récupère le soir et le matin qui lui reviennent")
    void filtre_sur_la_nuit_suivante() {
        List<String> nuit2 = FiltreThLogNuit.filtrer(THLOG_DEUX_NUITS, LocalDate.of(2026, 6, 23));

        assertThat(nuit2)
                .containsExactly(
                        "Date\tHour\tTemperature\tHumidity",
                        "23/06/2026\t22:30:00\t+15.0\t72",
                        "24/06/2026\t05:30:00\t+10.0\t88");
    }

    @Test
    @DisplayName("Une nuit sans mesure ne garde que l'entête")
    void nuit_sans_mesure_garde_l_entete() {
        List<String> aucune = FiltreThLogNuit.filtrer(THLOG_DEUX_NUITS, LocalDate.of(2026, 1, 1));

        assertThat(aucune).containsExactly("Date\tHour\tTemperature\tHumidity");
    }

    @Test
    @DisplayName("Les lignes illisibles sont écartées ; l'entête reste")
    void ligne_illisible_ecartee() {
        List<String> avecBruit = List.of(
                "Date\tHour\tTemperature\tHumidity",
                "22/06/2026\t22:30:00\t+16.1\t70",
                "ligne corrompue sans tabulation",
                "??/??/????\t99:99:99\t+16.1\t70");

        List<String> nuit = FiltreThLogNuit.filtrer(avecBruit, LocalDate.of(2026, 6, 22));

        assertThat(nuit).containsExactly("Date\tHour\tTemperature\tHumidity", "22/06/2026\t22:30:00\t+16.1\t70");
    }

    @Test
    @DisplayName("Une entrée vide reste vide")
    void entree_vide() {
        assertThat(FiltreThLogNuit.filtrer(List.of(), LocalDate.of(2026, 6, 22)))
                .isEmpty();
    }
}

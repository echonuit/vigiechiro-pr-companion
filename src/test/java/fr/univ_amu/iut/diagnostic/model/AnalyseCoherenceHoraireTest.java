package fr.univ_amu.iut.diagnostic.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import fr.univ_amu.iut.diagnostic.model.CoherenceHoraire.Couverture;
import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie [AnalyseCoherenceHoraire] au point d'Aix-en-Provence pour la nuit du 20 juin 2026 : le
/// coucher y tombe vers 21:23 et le lever vers 05:58, donc la fenêtre que le protocole exige va de
/// **20:53** à **06:28**.
///
/// Le protocole Vigie-Chiro Point Fixe demande de commencer **au moins** 30 minutes avant le coucher
/// et de finir **au moins** 30 minutes après le lever. C'est un plancher : dépasser n'est pas
/// s'écarter, et le modèle signalait l'inverse jusqu'à #4987.
class AnalyseCoherenceHoraireTest {

    private static final double AIX_LAT = 43.529;
    private static final double AIX_LON = 5.447;
    private static final String NUIT = "2026-06-20";

    @Test
    @DisplayName("#4987 : commencer AVANT le coucher respecte le protocole, et ne signale aucun défaut")
    void commencer_avant_le_coucher_respecte_le_protocole() {
        // Le cas que l'observateur a signalé : il faisait ce que le protocole demande, et recevait un
        // avertissement pour cela. Les heures ENCADRENT la marge au lieu de la frôler : le coucher
        // exact vient de l'éphéméride, et un cas posé à la minute près se briserait sur une seconde.
        CoherenceHoraire coherence = AnalyseCoherenceHoraire.analyser(AIX_LAT, AIX_LON, NUIT, "20:38:00", "06:43:00");

        assertThat(coherence.couverture()).isEqualTo(Couverture.COUVERTE);
    }

    @Test
    @DisplayName("La marge du protocole vaut exactement 30 minutes, de part et d'autre")
    void la_marge_du_protocole_vaut_trente_minutes() {
        // La règle s'éprouve contre les heures RENDUES, jamais contre un coucher figé dans le cas :
        // c'est la marge qui est testée ici, pas l'éphéméride.
        CoherenceHoraire coherence = AnalyseCoherenceHoraire.analyser(AIX_LAT, AIX_LON, NUIT, "22:00:00", "05:00:00");

        assertThat(Duration.between(coherence.debutExige(), coherence.coucherSoleil()))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(Duration.between(coherence.leverSoleil(), coherence.finExigee()))
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("#4987 : commencer 30 min APRÈS le coucher viole le protocole, et doit avertir")
    void commencer_apres_le_coucher_viole_le_protocole() {
        // L'erreur de paramétrage réellement commise, et sur laquelle l'application se taisait.
        CoherenceHoraire coherence = AnalyseCoherenceHoraire.analyser(AIX_LAT, AIX_LON, NUIT, "21:53:00", "06:28:00");

        assertThat(coherence.couverture()).isEqualTo(Couverture.INCOMPLETE);
    }

    @Test
    @DisplayName("Une plage plus large que la fenêtre exigée est une information, pas un défaut")
    void une_plage_plus_large_informe() {
        CoherenceHoraire coherence = AnalyseCoherenceHoraire.analyser(AIX_LAT, AIX_LON, NUIT, "20:00:00", "07:00:00");

        assertThat(coherence.couverture()).isEqualTo(Couverture.COUVERTE);
    }

    @Test
    @DisplayName("Finir avant la fin de la fenêtre exigée avertit, même en ayant commencé à l'heure")
    void finir_trop_tot_avertit() {
        CoherenceHoraire coherence = AnalyseCoherenceHoraire.analyser(AIX_LAT, AIX_LON, NUIT, "20:53:00", "06:08:00");

        assertThat(coherence.couverture()).isEqualTo(Couverture.INCOMPLETE);
    }

    @Test
    @DisplayName("Les deux plages sont rendues, pour que l'observateur voie l'attendu et l'obtenu")
    void les_deux_plages_sont_rendues() {
        CoherenceHoraire coherence = AnalyseCoherenceHoraire.analyser(AIX_LAT, AIX_LON, NUIT, "21:00:00", "06:00:00");

        assertThat(coherence.disponible()).isTrue();
        assertThat(coherence.coucherSoleil()).isCloseTo(LocalTime.of(21, 23), within(5, ChronoUnit.MINUTES));
        assertThat(coherence.leverSoleil()).isCloseTo(LocalTime.of(5, 58), within(5, ChronoUnit.MINUTES));
        assertThat(coherence.debutExige()).isCloseTo(LocalTime.of(20, 53), within(5, ChronoUnit.MINUTES));
        assertThat(coherence.finExigee()).isCloseTo(LocalTime.of(6, 28), within(5, ChronoUnit.MINUTES));
        assertThat(coherence.debutEnregistre()).isEqualTo(LocalTime.of(21, 0));
        assertThat(coherence.finEnregistree()).isEqualTo(LocalTime.of(6, 0));
    }

    @Test
    @DisplayName("Sans coordonnées GPS : cohérence indisponible")
    void sans_gps_est_indisponible() {
        assertThat(AnalyseCoherenceHoraire.analyser(null, null, NUIT, "22:00:00", "05:00:00"))
                .isEqualTo(CoherenceHoraire.indisponible());
    }

    @Test
    @DisplayName("Horaires manquants : cohérence indisponible")
    void horaires_manquants_est_indisponible() {
        assertThat(AnalyseCoherenceHoraire.analyser(AIX_LAT, AIX_LON, NUIT, null, null)
                        .disponible())
                .isFalse();
    }

    @Test
    @DisplayName("Horaires illisibles : cohérence indisponible (dégradation propre)")
    void horaires_illisibles_est_indisponible() {
        assertThat(AnalyseCoherenceHoraire.analyser(AIX_LAT, AIX_LON, NUIT, "minuit", "cinq heures")
                        .disponible())
                .isFalse();
    }

    @Test
    @DisplayName("Latitude polaire au solstice d'été (jour polaire) : cohérence indisponible")
    void latitude_polaire_est_indisponible() {
        assertThat(AnalyseCoherenceHoraire.analyser(78.22, 15.65, "2026-06-21", "22:00:00", "03:00:00")
                        .disponible())
                .isFalse();
    }
}

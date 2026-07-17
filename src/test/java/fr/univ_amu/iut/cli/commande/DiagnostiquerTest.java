package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.diagnostic.model.AnalyseAnomalies;
import fr.univ_amu.iut.diagnostic.model.CoherenceHoraire;
import fr.univ_amu.iut.diagnostic.model.Diagnostic;
import fr.univ_amu.iut.diagnostic.model.MesureClimatique;
import fr.univ_amu.iut.diagnostic.model.SerieClimatique;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Rendu (texte + JSON) de `diagnostiquer` (#1672). `rendreTexte` / `projeter` sont des fonctions
/// **pures** (sans base ni effet de bord), testées sur des [Diagnostic] construits à la main (comme
/// `StatutPassageTest`), sans injecteur ni SQLite. Deux cas : diagnostic complet et relevé absent (R20).
class DiagnostiquerTest {

    private static Diagnostic diagnosticComplet() {
        return new Diagnostic(
                42L,
                7L,
                "1925492",
                new AnalyseAnomalies(List.of("Réveil non programmé à 03:12"), List.of("Démarrage")),
                SerieClimatique.presente(List.of(
                        new MesureClimatique(LocalDate.of(2026, 6, 22), LocalTime.of(22, 0), 18.5, 72),
                        new MesureClimatique(LocalDate.of(2026, 6, 23), LocalTime.of(2, 0), 14.0, 88))),
                43.5,
                5.4,
                LocalDateTime.of(2026, 6, 23, 8, 0),
                8.5,
                new CoherenceHoraire(true, LocalTime.of(21, 58), LocalTime.of(5, 48), true, false));
    }

    private static Diagnostic diagnosticSansReleve() {
        return new Diagnostic(
                8L,
                9L,
                "1925492",
                AnalyseAnomalies.vide(),
                SerieClimatique.absente(),
                null,
                null,
                LocalDateTime.of(2026, 6, 24, 8, 0),
                null,
                CoherenceHoraire.indisponible());
    }

    @Test
    @DisplayName("Texte d'un diagnostic complet : enregistreur, mesures, cohérence, GPS, anomalies, évènements")
    void texte_diagnostic_complet() {
        String texte = Diagnostiquer.rendreTexte(42L, diagnosticComplet());

        assertThat(texte)
                .contains("Diagnostic du passage #42")
                .contains("PR 1925492")
                .contains("2 mesures T°/hygrométrie")
                .contains("8,5 °C")
                .contains("nuit 21:58 → 05:48")
                .contains("hors nuit")
                .contains("GPS du point")
                .contains("disponible")
                .contains("Réveil non programmé à 03:12")
                .contains("Démarrage");
    }

    @Test
    @DisplayName("Texte sans relevé (R20) : climat absent, GPS non renseigné, cohérence indisponible")
    void texte_sans_releve() {
        String texte = Diagnostiquer.rendreTexte(8L, diagnosticSansReleve());

        assertThat(texte).contains("absent (R20)").contains("non renseigné").contains("indisponible");
    }

    @Test
    @DisplayName("JSON d'un diagnostic complet : nombres bruts, cohérence, tableaux d'anomalies/évènements")
    void json_diagnostic_complet() {
        Map<String, Object> objet = Diagnostiquer.projeter(42L, diagnosticComplet());

        assertThat(objet)
                .containsEntry("passage", 42L)
                .containsEntry("enregistreur", "1925492")
                .containsEntry("releveClimatiqueAbsent", false)
                .containsEntry("nombreMesures", 2)
                .containsEntry("temperatureDebutNuitCelsius", 8.5)
                .containsEntry("coherenceHoraireDisponible", true)
                .containsEntry("coucherSoleil", "21:58")
                .containsEntry("horsNuit", true)
                .containsEntry("gpsDisponible", true);
        assertThat(objet.get("anomalies")).isEqualTo(List.of("Réveil non programmé à 03:12"));
        // Sérialisation JSON des tableaux (via FormatJson).
        assertThat(FormatJson.objet(objet)).contains("\"evenements\": [\"Démarrage\"]");
    }

    @Test
    @DisplayName("JSON sans relevé : cohérence et coucher à null, GPS false, mesures 0, tableaux vides")
    void json_sans_releve() {
        Map<String, Object> objet = Diagnostiquer.projeter(8L, diagnosticSansReleve());

        assertThat(objet)
                .containsEntry("coherenceHoraireDisponible", false)
                .containsEntry("gpsDisponible", false)
                .containsEntry("nombreMesures", 0)
                .containsEntry("temperatureDebutNuitCelsius", null);
        assertThat(objet.get("coucherSoleil")).isNull();
        assertThat(objet.get("anomalies")).isEqualTo(List.of());
    }
}

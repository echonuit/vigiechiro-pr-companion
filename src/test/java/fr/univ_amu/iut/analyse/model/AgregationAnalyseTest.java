package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie que l'agrégation **en mémoire** ([AgregationAnalyse]) reproduit fidèlement les `GROUP BY`
/// autrefois faits en SQL (#537 étape 4) : compteurs `DISTINCT`, richesse spécifique, plage d'années et
/// tri par défaut.
class AgregationAnalyseTest {

    /// Observation d'analyse minimale : seuls les champs utiles à l'agrégation varient, le reste est
    /// constant (le statut n'intervient pas ici, le filtrage se fait en amont dans le service).
    private static ObservationAnalyse obs(String taxon, long idPassage, int annee, String carre, long idPoint) {
        return new ObservationAnalyse(
                taxon,
                taxon + " (latin)",
                taxon,
                "Chiroptères",
                StatutObservation.VALIDEE,
                idPassage,
                annee,
                carre,
                "Site " + carre,
                idPoint);
    }

    @Test
    @DisplayName("parEspece : une entrée par taxon retenu, compteurs DISTINCT et plage d'années")
    void par_espece_compteurs_distincts() {
        // Pippip : 3 observations sur 2 passages, 2 carrés, 2 points, années 2024→2026 ; Nyclei : 1.
        List<ObservationAnalyse> observations = List.of(
                obs("Pippip", 1L, 2024, "A", 10L),
                obs("Pippip", 1L, 2025, "A", 10L),
                obs("Pippip", 2L, 2026, "B", 11L),
                obs("Nyclei", 3L, 2026, "A", 10L));

        List<EspeceAgregee> inventaire = AgregationAnalyse.parEspece(observations);

        assertThat(inventaire).extracting(EspeceAgregee::code).containsExactly("Pippip", "Nyclei");
        assertThat(inventaire.get(0)).satisfies(pippip -> {
            assertThat(pippip.nbObservations()).isEqualTo(3);
            assertThat(pippip.nbPassages()).isEqualTo(2);
            assertThat(pippip.nbCarres()).isEqualTo(2);
            assertThat(pippip.nbPoints()).isEqualTo(2);
            assertThat(pippip.anneeMin()).isEqualTo(2024);
            assertThat(pippip.anneeMax()).isEqualTo(2026);
            assertThat(pippip.groupe()).isEqualTo("Chiroptères");
        });
    }

    @Test
    @DisplayName("parEspece : trié par nombre d'observations décroissant")
    void par_espece_trie_par_nb_obs_decroissant() {
        List<ObservationAnalyse> observations = List.of(
                obs("Rare", 1L, 2026, "A", 10L),
                obs("Frequent", 2L, 2026, "A", 10L),
                obs("Frequent", 3L, 2026, "A", 10L));

        assertThat(AgregationAnalyse.parEspece(observations))
                .extracting(EspeceAgregee::code)
                .containsExactly("Frequent", "Rare");
    }

    @Test
    @DisplayName("parCarre : richesse = espèces distinctes, trié par richesse décroissante")
    void par_carre_richesse_et_tri() {
        List<ObservationAnalyse> observations = List.of(
                obs("Pippip", 1L, 2025, "A", 10L),
                obs("Nyclei", 1L, 2026, "A", 10L),
                obs("Tadten", 1L, 2026, "A", 10L),
                obs("Pippip", 2L, 2026, "B", 11L));

        List<CarreEspeces> parCarre = AgregationAnalyse.parCarre(observations);

        assertThat(parCarre).extracting(CarreEspeces::numeroCarre).containsExactly("A", "B");
        assertThat(parCarre.get(0)).satisfies(carreA -> {
            assertThat(carreA.richesse()).isEqualTo(3);
            assertThat(carreA.nbObservations()).isEqualTo(3);
            assertThat(carreA.anneeMin()).isEqualTo(2025);
            assertThat(carreA.anneeMax()).isEqualTo(2026);
        });
    }

    @Test
    @DisplayName("Listes vides → agrégations vides")
    void listes_vides() {
        assertThat(AgregationAnalyse.parEspece(List.of())).isEmpty();
        assertThat(AgregationAnalyse.parCarre(List.of())).isEmpty();
    }
}

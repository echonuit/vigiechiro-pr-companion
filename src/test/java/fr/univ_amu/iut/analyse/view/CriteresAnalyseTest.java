package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests unitaires (purs, sans JavaFX) du catalogue de critères analyse : la **recherche texte** est
/// insensible casse/accents et couvre le taxon retenu, le vernaculaire, le latin, le n° de carré et le nom
/// de site. Les éditeurs (listes déroulantes des puces) sont exercés par le test de vue.
class CriteresAnalyseTest {

    private final BiPredicate<ObservationAnalyse, String> recherche = CriteresAnalyse.rechercheTexte();

    private static ObservationAnalyse obs(String taxon, String vern, String latin, String carre, String site) {
        return new ObservationAnalyse(
                taxon, latin, vern, "Chiroptères", StatutObservation.VALIDEE, 1L, 2026, carre, site, 10L);
    }

    @Test
    @DisplayName("La recherche texte est insensible à la casse et aux accents")
    void recherche_insensible_casse_accents() {
        ObservationAnalyse o =
                obs("Pippip", "Pipistrelle commune", "Pipistrellus pipistrellus", "640380", "Étang du Vaccarès");
        assertThat(recherche.test(o, "PIPISTRELLE")).isTrue();
        assertThat(recherche.test(o, "vaccares")).isTrue(); // accent ignoré (Vaccarès)
        assertThat(recherche.test(o, "etang")).isTrue();
    }

    @Test
    @DisplayName("La recherche couvre le taxon retenu, le latin et le n° de carré ; sinon aucune correspondance")
    void recherche_couvre_les_champs_cles() {
        ObservationAnalyse o = obs("Nyclei", "Noctule de Leisler", "Nyctalus leisleri", "640381", "Marais");
        assertThat(recherche.test(o, "nyclei")).isTrue(); // taxon retenu
        assertThat(recherche.test(o, "nyctalus")).isTrue(); // nom latin
        assertThat(recherche.test(o, "640381")).isTrue(); // n° de carré
        assertThat(recherche.test(o, "absent")).isFalse();
    }
}

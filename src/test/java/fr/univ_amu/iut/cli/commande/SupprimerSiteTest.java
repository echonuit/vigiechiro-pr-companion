package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.sites.model.PointDEcoute;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Les deux phrases de `supprimer-site` (#1383) : ce qu'on perd, et ce qui bloque.
///
/// Elles sont pures, donc testables sans base ni processus. Ce qu'un `bats` vérifie ensuite, c'est le
/// **code de sortie** ; ce que ces cas-ci verrouillent, c'est que les phrases disent la bonne chose.
class SupprimerSiteTest {

    private static PointDEcoute point(long id, String code) {
        return new PointDEcoute(id, code, null, null, null, 1L);
    }

    @Test
    @DisplayName("La perte nomme les points qui disparaissent")
    void perteNommeLesPoints() {
        String perte = SupprimerSite.perteEnClair(List.of(point(1, "A1"), point(2, "B2")));

        assertThat(perte).contains("2 point(s)").contains("A1, B2");
    }

    @Test
    @DisplayName("Un site sans point le dit, plutôt que d'annoncer « 0 point(s) »")
    void siteSansPoint() {
        assertThat(SupprimerSite.perteEnClair(List.of()))
                .contains("aucun point d'écoute")
                .doesNotContain("0 point");
    }

    @Test
    @DisplayName("Le refus nomme le point bloquant et dit quoi faire")
    void refusNommeLeBloquantEtLaSuite() {
        String refus = SupprimerSite.refusEnClair(List.of("A1"));

        assertThat(refus)
                .contains("Le point « A1 » porte")
                .contains("au moins un passage")
                .as("un refus dit ce qui manque, la surface dit quoi faire (ADR 2635)")
                .contains("supprimer-passage");
    }

    @Test
    @DisplayName("Plusieurs bloquants s'accordent au pluriel et sont tous nommés")
    void plusieursBloquants() {
        // Le singulier « Le point ... porte » sur trois points serait une petite faute qui décrédibilise
        // le reste du message, au moment précis où l'utilisateur cherche à comprendre un refus.
        String refus = SupprimerSite.refusEnClair(List.of("A1", "B2", "C3"));

        assertThat(refus).contains("Les points A1, B2, C3 portent");
    }
}

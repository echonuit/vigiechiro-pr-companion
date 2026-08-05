package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Restreindre l'inventaire des espèces **en ligne de commande** (#3269), aux mêmes conditions que la
/// barre de filtres d'« Espèces & observations ».
///
/// Jumelle de [FiltresActivite], dont elle reprend la forme et les deux régimes : un critère qui
/// **désigne** refuse quand la chose n'existe pas, un critère qui **qualifie** rend légitimement vide.
class FiltresAnalyseTest {

    private static ObservationAnalyse observation(String taxon, String groupe, StatutObservation statut, long passage) {
        return new ObservationAnalyse(
                taxon, taxon, taxon, groupe, statut, passage, 2026, "640380", "Vallon", 1L, "Ahetze", "A1");
    }

    private static final ObservationAnalyse PIPISTRELLE =
            observation("Pippip", "Chiroptères", StatutObservation.VALIDEE, 10L);

    private static final ObservationAnalyse ORTHOPTERE =
            observation("Grycam", "Orthoptères et cigales", StatutObservation.NON_TOUCHEE, 11L);

    @Test
    @DisplayName("#3269 : --statut ne garde que les observations de cet état de revue")
    void par_statut() {
        List<ObservationAnalyse> retenues =
                FiltresAnalyse.parStatut(List.of(PIPISTRELLE, ORTHOPTERE), StatutObservation.VALIDEE);

        assertThat(retenues).containsExactly(PIPISTRELLE);
    }

    @Test
    @DisplayName("#3269 : un critère nul n'écarte rien")
    void un_critere_nul_necarte_rien() {
        List<ObservationAnalyse> toutes = List.of(PIPISTRELLE, ORTHOPTERE);

        assertThat(FiltresAnalyse.parStatut(toutes, null)).isEqualTo(toutes);
        assertThat(FiltresAnalyse.parTaxonParent(toutes, null)).isEqualTo(toutes);
        assertThat(FiltresAnalyse.parTaxonParent(toutes, "  ")).isEqualTo(toutes);
    }

    @Test
    @DisplayName("#3269 : --taxon-parent correspond partiellement, comme --lieu")
    void par_taxon_parent_partiel() {
        // La ligne de commande ne choisit pas dans une liste : on tape un fragment. C'est l'écart
        // délibéré avec l'écran, et le même que celui de `FiltresActivite`.
        assertThat(FiltresAnalyse.parTaxonParent(List.of(PIPISTRELLE, ORTHOPTERE), "chirop"))
                .containsExactly(PIPISTRELLE);
        assertThat(FiltresAnalyse.parTaxonParent(List.of(PIPISTRELLE, ORTHOPTERE), "ORTHOPTERES"))
                .as("insensible à la casse et aux accents")
                .containsExactly(ORTHOPTERE);
    }

    @Test
    @DisplayName("#3269 : DÉSIGNER un taxon parent absent est un refus qui nomme ce qui existe")
    void par_taxon_parent_absent_refuse() {
        // Un critère qui désigne refuse (ADR 3082) : rendre une liste vide laisserait croire que ce
        // taxon parent existe et n'a rien, alors qu'il n'existe pas du tout.
        assertThatThrownBy(() -> FiltresAnalyse.parTaxonParent(List.of(PIPISTRELLE), "Oiseaux"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Oiseaux")
                .hasMessageContaining("Chiroptères");
    }

    @Test
    @DisplayName("#3269 : QUALIFIER rend légitimement vide, sans refuser")
    void qualifier_rend_vide() {
        // « Aucune espèce à enjeu dans cet inventaire » est une réponse, souvent celle qu'on cherchait.
        assertThat(FiltresAnalyse.aEnjeu(List.of(PIPISTRELLE, ORTHOPTERE), o -> false))
                .isEmpty();
        assertThat(FiltresAnalyse.parNature(List.of(PIPISTRELLE), "opportuniste", Set.of()))
                .isEmpty();
    }

    @Test
    @DisplayName("#3269 : --nature sépare protocole et opportuniste, et refuse une valeur inconnue")
    void par_nature() {
        assertThat(FiltresAnalyse.parNature(List.of(PIPISTRELLE, ORTHOPTERE), "opportuniste", Set.of(10L)))
                .containsExactly(PIPISTRELLE);
        assertThat(FiltresAnalyse.parNature(List.of(PIPISTRELLE, ORTHOPTERE), "protocole", Set.of(10L)))
                .containsExactly(ORTHOPTERE);

        assertThatThrownBy(() -> FiltresAnalyse.parNature(List.of(PIPISTRELLE), "autre", Set.of()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("protocole");
    }

    @Test
    @DisplayName("#3269 : les dimensions de lieu excluent le point, comme les autres commandes")
    void dimensions_de_lieu() {
        // Le schéma pose UNIQUE(site_id, code) : un code de point seul désigne autant de lieux qu'il y a
        // de carrés. L'écran s'en tire en le qualifiant ; la ligne de commande s'aligne sur ses jumelles
        // et ne l'offre pas.
        assertThat(FiltresAnalyse.dimensionsLieu(PIPISTRELLE))
                .contains("Ahetze")
                .anyMatch(dimension -> dimension.contains("640380"))
                .noneMatch(dimension -> dimension.equals("A1"));
    }
}

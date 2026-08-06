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
        // `parNature` manquait à cet inventaire : PIT l'a montré en survivant à un retour vide sur sa
        // garde de nullité (clôture des suites de #3092). Un critère oublié ici est un critère dont on
        // ne sait pas s'il écarte tout quand on ne le pose pas.
        assertThat(FiltresAnalyse.parNature(toutes, null, Set.of())).isEqualTo(toutes);
        assertThat(FiltresAnalyse.parNature(toutes, "  ", Set.of())).isEqualTo(toutes);
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
    @DisplayName("#3269 : le refus n'énumère pas un taxon parent vide")
    void le_refus_nenumere_pas_un_groupe_vide() {
        // Une auto-souche hors référentiel peut n'avoir aucun groupe. Sans le filtre, la phrase se
        // terminerait par « Taxons parents présents : , Chiroptères » - une virgule qui ne désigne rien,
        // dans le message même qui doit aider à retrouver la bonne valeur. Dernier survivant PIT.
        ObservationAnalyse sansGroupe = observation("Xxxxxx", null, StatutObservation.NON_TOUCHEE, 13L);

        assertThatThrownBy(() -> FiltresAnalyse.parTaxonParent(List.of(PIPISTRELLE, sansGroupe), "Oiseaux"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Chiroptères")
                .hasMessageNotContaining(", ,")
                .hasMessageNotContaining(": ,");
    }

    @Test
    @DisplayName("#3269 : QUALIFIER rend légitimement vide, sans refuser")
    void qualifier_rend_vide() {
        // « Aucune espèce à enjeu dans cet inventaire » est une réponse, souvent celle qu'on cherchait.
        assertThat(FiltresAnalyse.aEnjeu(List.of(PIPISTRELLE, ORTHOPTERE), o -> false))
                .isEmpty();
        assertThat(FiltresAnalyse.parNature(List.of(PIPISTRELLE), "opportuniste", Set.of()))
                .isEmpty();

        // Et il RETIENT ce qui correspond. Sans ce second côté, `aEnjeu` pourrait rendre une liste vide
        // en toutes circonstances sans qu'aucune assertion ne bronche : PIT y a survécu, précisément
        // parce que le seul cas testé attendait le vide.
        assertThat(FiltresAnalyse.aEnjeu(List.of(PIPISTRELLE, ORTHOPTERE), o -> o.equals(PIPISTRELLE)))
                .containsExactly(PIPISTRELLE);
    }

    @Test
    @DisplayName("#3269 : une dimension de lieu absente est écartée, pas comparée")
    void dimension_absente_ecartee() {
        // Une observation dont la commune n'est pas résolue est un état légitime (#2989) : le point peut
        // n'avoir aucune coordonnée. La dimension ne doit alors pas être offerte à la comparaison, sinon
        // `--lieu` confronterait une valeur tapée à un null. PIT a survécu à l'ouverture de ce filtre.
        ObservationAnalyse sansCommune = new ObservationAnalyse(
                "Pippip", "Pippip", "Pippip", "Chiroptères", null, 12L, 2026, "640380", "Vallon", 1L, null, "A1");

        assertThat(FiltresAnalyse.dimensionsLieu(sansCommune))
                .as("le carré et son point restent comparables (#3350), la commune manquante est écartée")
                .containsExactly("640380 · Vallon", "640380 · A1");
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

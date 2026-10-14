package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.ParallelismeDeFond;
import org.junit.jupiter.api.Test;

/// La réactivation régénère ses bruts à la borne de [ParallelismeDeFond], pas sur tous les cœurs (#6256).
///
/// À tous les cœurs, elle tenait O3 sur un poste de seize ; rien ne disait ce qu'elle laisse au fil
/// JavaFX, ni aux autres applications, sur un poste de quatre. La règle de l'ADR 6175 vaut donc pour
/// elle comme pour l'import. Ce cas rougit si la borne redevient celle du poste entier.
class ParallelismeDeLaReactivationTest {

    @Test
    void la_regeneration_mene_de_front_la_borne_du_poste_et_pas_tous_ses_coeurs() {
        assertThat(ServiceReactivationPassage.PARALLELISME)
                .as(
                        "régénérations menées de front par l'hydratation, pour %d cœurs",
                        Runtime.getRuntime().availableProcessors())
                .isEqualTo(ParallelismeDeFond.duPoste());
    }
}

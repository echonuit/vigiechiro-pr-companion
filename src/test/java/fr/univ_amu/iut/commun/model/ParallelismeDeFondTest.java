package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/// Une tâche de fond qui calcule mène de front la moitié des cœurs du poste, et laisse l'autre à
/// l'interface (#6175, ADR 6175).
class ParallelismeDeFondTest {

    @Test
    void la_moitie_des_coeurs_va_au_calcul() {
        assertThat(ParallelismeDeFond.pour(16)).isEqualTo(8);
        assertThat(ParallelismeDeFond.pour(8)).isEqualTo(4);
        assertThat(ParallelismeDeFond.pour(4)).isEqualTo(2);
    }

    @Test
    void un_nombre_impair_de_coeurs_arrondit_au_profit_de_l_interface() {
        assertThat(ParallelismeDeFond.pour(7)).isEqualTo(3);
        assertThat(ParallelismeDeFond.pour(3)).isEqualTo(1);
    }

    @Test
    void il_reste_toujours_un_traitement() {
        assertThat(ParallelismeDeFond.pour(2)).isEqualTo(1);
        assertThat(ParallelismeDeFond.pour(1))
                .as("un seul cœur : le calcul passe quand même, l'un après l'autre")
                .isEqualTo(1);
    }

    @Test
    void un_poste_sans_coeur_n_existe_pas() {
        assertThatThrownBy(() -> ParallelismeDeFond.pour(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void le_poste_donne_la_borne_de_ses_propres_coeurs() {
        assertThat(ParallelismeDeFond.duPoste())
                .isEqualTo(ParallelismeDeFond.pour(Runtime.getRuntime().availableProcessors()));
    }
}

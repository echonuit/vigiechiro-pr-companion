package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// « Sur la plateforme » : le prédicat que la chaîne de dépôt attendait sans le nommer (#2775).
///
/// Tant qu'un seul statut recouvrait les deux situations, `== DEPOSE` disait la bonne chose partout.
/// Poser [StatutWorkflow#RECUPERE] a séparé les deux, et chaque `== DEPOSE` est devenu une question :
/// celui-ci parlait-il de « déposé **par nous** », ou de « déjà **là-bas** » ?
class StatutSurLaPlateformeTest {

    @Test
    @DisplayName("#2775 : les deux statuts qui vivent sur Vigie-Chiro, et eux seuls")
    void deux_statuts_sur_la_plateforme() {
        assertThat(StatutWorkflow.DEPOSE.estSurLaPlateforme())
                .as("nous l'y avons mise")
                .isTrue();
        assertThat(StatutWorkflow.RECUPERE.estSurLaPlateforme())
                .as("elle en vient")
                .isTrue();

        for (StatutWorkflow statut : StatutWorkflow.values()) {
            if (statut == StatutWorkflow.DEPOSE || statut == StatutWorkflow.RECUPERE) {
                continue;
            }
            assertThat(statut.estSurLaPlateforme())
                    .as("« %s » n'est encore que chez nous", statut.libelle())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("#2775 : « Dépôt en cours » n'y est PAS - un téléversement entamé n'est pas un dépôt")
    void depot_en_cours_n_y_est_pas() {
        // Le distinguo compte : la chaîne de dépôt doit pouvoir REPRENDRE un dépôt interrompu (#982),
        // ce qu'elle ne ferait plus si ce statut passait pour « déjà là-bas ».
        assertThat(StatutWorkflow.DEPOT_EN_COURS.estSurLaPlateforme()).isFalse();
    }
}

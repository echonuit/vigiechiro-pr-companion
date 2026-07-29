package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Test pur (sans base ni mock) du [MoteurWorkflowPassage] : les transitions de workflow sont une
/// logique purement algorithmique. On vérifie la progression linéaire autorisée, les deux exceptions
/// du dépôt (#980 : marquage manuel, reprise), les sauts d'étape, les retours en arrière et le statut
/// terminal.
class MoteurWorkflowPassageTest {

    private final MoteurWorkflowPassage moteur = new MoteurWorkflowPassage();

    @Test
    @DisplayName("Le successeur immédiat suit l'ordre Importé → Transformé → Vérifié → Prêt → Dépôt en cours → Déposé")
    void successeur_immediat() {
        assertThat(moteur.suivant(StatutWorkflow.IMPORTE)).contains(StatutWorkflow.TRANSFORME);
        assertThat(moteur.suivant(StatutWorkflow.TRANSFORME)).contains(StatutWorkflow.VERIFIE);
        assertThat(moteur.suivant(StatutWorkflow.VERIFIE)).contains(StatutWorkflow.PRET_A_DEPOSER);
        assertThat(moteur.suivant(StatutWorkflow.PRET_A_DEPOSER)).contains(StatutWorkflow.DEPOT_EN_COURS);
        assertThat(moteur.suivant(StatutWorkflow.DEPOT_EN_COURS)).contains(StatutWorkflow.DEPOSE);
    }

    @Test
    @DisplayName("Le statut terminal (Déposé) n'a pas de successeur")
    void statut_terminal_sans_successeur() {
        assertThat(moteur.suivant(StatutWorkflow.DEPOSE)).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("Chaque passage à l'étape suivante est autorisé")
    void transitions_d_etape_en_etape_autorisees() {
        assertThat(moteur.estTransitionAutorisee(StatutWorkflow.IMPORTE, StatutWorkflow.TRANSFORME))
                .isTrue();
        assertThat(moteur.estTransitionAutorisee(StatutWorkflow.VERIFIE, StatutWorkflow.PRET_A_DEPOSER))
                .isTrue();
        assertThat(moteur.estTransitionAutorisee(StatutWorkflow.PRET_A_DEPOSER, StatutWorkflow.DEPOT_EN_COURS))
                .isTrue();
        assertThat(moteur.estTransitionAutorisee(StatutWorkflow.DEPOT_EN_COURS, StatutWorkflow.DEPOSE))
                .isTrue();
    }

    @Test
    @DisplayName("#980 : le marquage manuel saute le statut technique (Prêt à déposer → Déposé autorisé)")
    void marquage_manuel_saute_le_statut_technique() {
        assertThat(moteur.estTransitionAutorisee(StatutWorkflow.PRET_A_DEPOSER, StatutWorkflow.DEPOSE))
                .isTrue();
    }

    @Test
    @DisplayName("#980 : la reprise d'un dépôt interrompu repart du même statut (Dépôt en cours → Dépôt en cours)")
    void reprise_du_depot_autorisee_sur_place() {
        assertThat(moteur.estTransitionAutorisee(StatutWorkflow.DEPOT_EN_COURS, StatutWorkflow.DEPOT_EN_COURS))
                .isTrue();
    }

    @Test
    @DisplayName("Sauter une étape est interdit")
    void saut_d_etape_interdit() {
        assertThat(moteur.estTransitionAutorisee(StatutWorkflow.IMPORTE, StatutWorkflow.DEPOSE))
                .isFalse();
        assertThat(moteur.estTransitionAutorisee(StatutWorkflow.IMPORTE, StatutWorkflow.VERIFIE))
                .isFalse();
        assertThat(moteur.estTransitionAutorisee(StatutWorkflow.VERIFIE, StatutWorkflow.DEPOT_EN_COURS))
                .isFalse();
        assertThatThrownBy(() -> moteur.exigerTransitionAutorisee(StatutWorkflow.IMPORTE, StatutWorkflow.DEPOSE))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("interdite");
    }

    @Test
    @DisplayName("Revenir en arrière est interdit")
    void retour_en_arriere_interdit() {
        assertThat(moteur.estTransitionAutorisee(StatutWorkflow.VERIFIE, StatutWorkflow.IMPORTE))
                .isFalse();
        assertThat(moteur.estTransitionAutorisee(StatutWorkflow.DEPOT_EN_COURS, StatutWorkflow.PRET_A_DEPOSER))
                .isFalse();
        assertThatThrownBy(() -> moteur.exigerTransitionAutorisee(StatutWorkflow.DEPOSE, StatutWorkflow.PRET_A_DEPOSER))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    @DisplayName("Rester sur le même statut n'est pas une transition autorisée (hors reprise du dépôt)")
    void transition_immobile_interdite() {
        assertThat(moteur.estTransitionAutorisee(StatutWorkflow.VERIFIE, StatutWorkflow.VERIFIE))
                .isFalse();
        assertThat(moteur.estTransitionAutorisee(StatutWorkflow.DEPOSE, StatutWorkflow.DEPOSE))
                .isFalse();
    }

    @Test
    @DisplayName("#2581 : « Récupéré » n'a qu'une suite, « Déposé », et rien d'autre n'est autorisé")
    void recupere_ne_mene_qu_a_depose() {
        assertThat(moteur.estTransitionAutorisee(StatutWorkflow.RECUPERE, StatutWorkflow.DEPOSE))
                .as("l'audio est revenu : la nuit rejoint les nuits déposées")
                .isTrue();
        for (StatutWorkflow cible : StatutWorkflow.values()) {
            if (cible == StatutWorkflow.DEPOSE) {
                continue;
            }
            assertThat(moteur.estTransitionAutorisee(StatutWorkflow.RECUPERE, cible))
                    .as(
                            "une nuit récupérée ne peut pas aller vers « %s » : elle n'a parcouru aucune des"
                                    + " étapes d'import",
                            cible.libelle())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("#2581 : aucune étape du workflow ne mène à « Récupéré »")
    void rien_ne_mene_a_recupere() {
        for (StatutWorkflow depart : StatutWorkflow.values()) {
            assertThat(moteur.estTransitionAutorisee(depart, StatutWorkflow.RECUPERE))
                    .as(
                            "« Récupéré » se pose à la création par la synchro, il ne s'atteint pas depuis" + " « %s »",
                            depart.libelle())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("#2581 : « Récupéré » n'a pas de successeur au sens de la progression")
    void recupere_est_hors_de_la_file() {
        assertThat(moteur.suivant(StatutWorkflow.RECUPERE))
                .as("sa suite dépend d'un événement - le retour de l'audio - pas d'une place dans la file")
                .isEmpty();
    }
}

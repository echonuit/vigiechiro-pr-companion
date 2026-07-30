package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le rang de tri d'un statut n'est pas sa position de déclaration (#2774).
///
/// [StatutWorkflow#RECUPERE] est déclaré **en dernier** pour ne pas décaler les comparaisons par
/// `ordinal()` existantes (ADR 2581). Trier sur `ordinal()` le rangerait donc après « Déposé », par pur
/// effet de bord de ce choix d'implémentation - et une nuit récupérée passerait pour la plus avancée de
/// toutes, alors qu'elle n'a parcouru aucune étape.
class StatutWorkflowRangTest {

    @Test
    @DisplayName("#2774 : une nuit récupérée se range AVEC les nuits déposées, pas après elles")
    void recupere_se_range_avec_depose() {
        assertThat(StatutWorkflow.RECUPERE.rangDeProgression())
                .as("elle est sur la plateforme, comme une nuit déposée : c'est là qu'elle se lit")
                .isEqualTo(StatutWorkflow.DEPOSE.rangDeProgression());

        assertThat(StatutWorkflow.RECUPERE.rangDeProgression())
                .as("son ordinal, lui, la mettrait en queue - c'est justement ce qu'on ne veut pas")
                .isNotEqualTo(StatutWorkflow.RECUPERE.ordinal());
    }

    @Test
    @DisplayName("#2774 : pour tous les autres statuts, le rang reste l'ordre de progression")
    void les_autres_statuts_gardent_leur_rang() {
        for (StatutWorkflow statut : StatutWorkflow.values()) {
            if (statut == StatutWorkflow.RECUPERE) {
                continue;
            }
            assertThat(statut.rangDeProgression())
                    .as("« %s » suit la file : son rang est sa place dans la progression", statut.libelle())
                    .isEqualTo(statut.ordinal());
        }
    }

    @Test
    @DisplayName("#2833 : chaque statut répond explicitement s'il est un jalon de la frise")
    void chaque_statut_dit_s_il_est_un_jalon() {
        // Le `switch` exhaustif d'`estJalon()` oblige déjà à répondre pour toute valeur ajoutée : sans
        // réponse, ça ne compile pas. Ce test verrouille l'autre moitié - que la réponse soit celle
        // qu'on croit, et non un `true` posé pour faire taire le compilateur.
        assertThat(StatutWorkflow.DEPOT_EN_COURS.estJalon())
                .as("statut technique : le jalon reste « Prêt à déposer » tant que le dépôt n'est pas fini")
                .isFalse();
        assertThat(StatutWorkflow.RECUPERE.estJalon())
                .as("hors de la file : elle n'a franchi aucune de ces étapes, elle a sa propre frise")
                .isFalse();

        for (StatutWorkflow statut : StatutWorkflow.values()) {
            if (statut == StatutWorkflow.DEPOT_EN_COURS || statut == StatutWorkflow.RECUPERE) {
                continue;
            }
            assertThat(statut.estJalon())
                    .as("« %s » est une étape que la nuit franchit : la frise doit la montrer", statut.libelle())
                    .isTrue();
        }
    }
}

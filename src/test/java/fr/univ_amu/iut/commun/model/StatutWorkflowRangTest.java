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
}

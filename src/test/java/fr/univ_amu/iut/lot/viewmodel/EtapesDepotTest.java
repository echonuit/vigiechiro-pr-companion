package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Stepper du dépôt (#251) : l'étape courante est déduite du statut workflow et de la présence
/// d'archives générées. Pur (aucun état JavaFX).
class EtapesDepotTest {

    @Test
    @DisplayName("Prêt à déposer : étape ② sans archives, étape ③ dès que les archives sont générées")
    void pret_a_deposer_suit_la_generation() {
        assertThat(etatDe(EtapesDepot.calculer(StatutWorkflow.PRET_A_DEPOSER, false), 2))
                .isEqualTo(EtatEtape.COURANTE);
        assertThat(etatDe(EtapesDepot.calculer(StatutWorkflow.PRET_A_DEPOSER, true), 3))
                .isEqualTo(EtatEtape.COURANTE);
    }

    @Test
    @DisplayName("#980 : un dépôt entamé mais incomplet reste à l'étape ③ « Téléverser » (reprenable)")
    void depot_en_cours_reste_au_televersement() {
        assertThat(etatDe(EtapesDepot.calculer(StatutWorkflow.DEPOT_EN_COURS, true), 3))
                .isEqualTo(EtatEtape.COURANTE);
        assertThat(etatDe(EtapesDepot.calculer(StatutWorkflow.DEPOT_EN_COURS, true), 4))
                .isEqualTo(EtatEtape.A_VENIR);
    }

    @Test
    @DisplayName("Déposé : toutes les étapes sont franchies")
    void depose_franchit_tout() {
        EtapesDepot.calculer(StatutWorkflow.DEPOSE, true)
                .forEach(etape -> assertThat(etape.etat()).isEqualTo(EtatEtape.FRANCHIE));
    }

    private static EtatEtape etatDe(java.util.List<EtapeDepot> etapes, int rang) {
        return etapes.get(rang - 1).etat();
    }
}

package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.ControleCoherence;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.StatutControle;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Règle « un contrôle bloquant rend le lot non conforme » ([ActionsLotPossibles]) : re-vérification possible,
/// suite (génération / dépôt) neutralisée, indépendamment du statut persisté.
class ActionsLotPossiblesTest {

    private static EtatLot etat(StatutWorkflow statut, boolean echec) {
        List<ControleCoherence> controles = echec
                ? List.of(new ControleCoherence("Nommage des fichiers", StatutControle.ECHEC, "Préfixe non conforme"))
                : List.of(new ControleCoherence("Nommage des fichiers", StatutControle.OK, "Conforme"));
        return new EtatLot(statut, "/ws/s", 2, 8192L, controles, null);
    }

    @Test
    @DisplayName("Vérifié + conforme : préparation possible, rien d'autre")
    void verifie_conforme() {
        ActionsLotPossibles a = ActionsLotPossibles.depuis(etat(StatutWorkflow.VERIFIE, false));
        assertThat(a.preparer()).isTrue();
        assertThat(a.genererArchives()).isFalse();
        assertThat(a.deposer()).isFalse();
    }

    @Test
    @DisplayName("Prêt à déposer + conforme : génération et dépôt possibles, préparation non")
    void pret_conforme() {
        ActionsLotPossibles a = ActionsLotPossibles.depuis(etat(StatutWorkflow.PRET_A_DEPOSER, false));
        assertThat(a.preparer()).isFalse();
        assertThat(a.genererArchives()).isTrue();
        assertThat(a.deposer()).isTrue();
    }

    @Test
    @DisplayName("Prêt à déposer + contrôle en échec (reliquat) : re-vérification possible, suite neutralisée")
    void pret_en_echec_neutralise() {
        ActionsLotPossibles a = ActionsLotPossibles.depuis(etat(StatutWorkflow.PRET_A_DEPOSER, true));
        assertThat(a.preparer()).isTrue(); // re-vérifiable
        assertThat(a.genererArchives()).isFalse();
        assertThat(a.deposer()).isFalse();
    }

    @Test
    @DisplayName("Déposé + contrôle en échec : ni re-préparation ni dépôt (statut terminal)")
    void depose_en_echec() {
        ActionsLotPossibles a = ActionsLotPossibles.depuis(etat(StatutWorkflow.DEPOSE, true));
        assertThat(a.preparer()).isFalse();
        assertThat(a.deposer()).isFalse();
        assertThat(a.depose()).isTrue();
    }
}

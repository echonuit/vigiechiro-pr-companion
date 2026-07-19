package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.viewmodel.EtatEtape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Stepper du dépôt (#251) : l'étape courante est déduite du statut workflow, de la présence d'archives
/// générées, et — depuis #1998 — de la disponibilité du dépôt automatique. Pur (aucun état JavaFX).
class EtapesDepotTest {

    private static final boolean CONNECTE = true;
    private static final boolean HORS_LIGNE = false;

    @Test
    @DisplayName("hors ligne, Prêt à déposer : étape ② tant que les archives ne sont pas générées")
    void hors_ligne_la_generation_reste_le_passage_oblige() {
        // Sans dépôt automatique, la seule voie est le dépôt manuel : il faut bien générer les archives.
        assertThat(etatDe(EtapesDepot.calculer(StatutWorkflow.PRET_A_DEPOSER, false, HORS_LIGNE), 2))
                .isEqualTo(EtatEtape.COURANTE);
        assertThat(etatDe(EtapesDepot.calculer(StatutWorkflow.PRET_A_DEPOSER, true, HORS_LIGNE), 3))
                .isEqualTo(EtatEtape.COURANTE);
    }

    @Test
    @DisplayName("#1998 : connecté, l'étape ③ est courante sans archives — générer n'est plus obligatoire")
    void connecte_le_televersement_est_atteignable_directement() {
        // C'est le changement du lot : le téléversement produit lui-même ses archives (#1995), donc
        // annoncer « Générer les archives » comme étape courante ferait attendre pour rien.
        assertThat(etatDe(EtapesDepot.calculer(StatutWorkflow.PRET_A_DEPOSER, false, CONNECTE), 3))
                .isEqualTo(EtatEtape.COURANTE);
        assertThat(etatDe(EtapesDepot.calculer(StatutWorkflow.PRET_A_DEPOSER, false, CONNECTE), 2))
                .as("l'étape ② est franchie, pas escamotée : elle reste offerte pour le dépôt manuel")
                .isEqualTo(EtatEtape.FRANCHIE);
    }

    @Test
    @DisplayName("#980 : un dépôt entamé mais incomplet reste à l'étape ③ « Téléverser » (reprenable)")
    void depot_en_cours_reste_au_televersement() {
        assertThat(etatDe(EtapesDepot.calculer(StatutWorkflow.DEPOT_EN_COURS, true, CONNECTE), 3))
                .isEqualTo(EtatEtape.COURANTE);
        assertThat(etatDe(EtapesDepot.calculer(StatutWorkflow.DEPOT_EN_COURS, true, CONNECTE), 4))
                .isEqualTo(EtatEtape.A_VENIR);
    }

    @Test
    @DisplayName("#1998 : un dépôt terminé ne retombe pas à l'étape ② faute d'archives sur le disque")
    void depot_termine_ne_retombe_pas_sur_la_generation() {
        // Le pipeline libère ses archives au fil de l'eau : à la fin, le dossier est vide. Le stepper ne
        // doit pas en conclure « pas encore générées ».
        EtapesDepot.calculer(StatutWorkflow.DEPOSE, false, CONNECTE)
                .forEach(etape -> assertThat(etape.etat()).isEqualTo(EtatEtape.FRANCHIE));
    }

    @Test
    @DisplayName("Déposé : toutes les étapes sont franchies")
    void depose_franchit_tout() {
        EtapesDepot.calculer(StatutWorkflow.DEPOSE, true, CONNECTE)
                .forEach(etape -> assertThat(etape.etat()).isEqualTo(EtatEtape.FRANCHIE));
    }

    private static EtatEtape etatDe(java.util.List<EtapeDepot> etapes, int rang) {
        return etapes.get(rang - 1).etat();
    }
}

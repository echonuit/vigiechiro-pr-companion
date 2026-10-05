package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.viewmodel.EtatEtape;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le fil d'étapes suit le **dernier geste** du dépôt, nom et état (#5859).
///
/// Le titre et le bouton de la dernière étape disaient « Lancer la participation » dès qu'une
/// participation était liée (#5676). Le fil lisait une liste figée, nommait le même geste « Marquer
/// déposé », et le disait fait dès que la nuit était déposée (constat de recette S4-C03).
class DernierGesteDuDepotTest {

    private static final boolean CONNECTE = true;
    private static final boolean HORS_LIGNE = false;

    @Test
    @DisplayName("#5859 : le geste se déduit de la participation liée et de l'analyse")
    void le_geste_se_deduit_de_ce_que_l_ecran_sait() {
        assertThat(DernierGesteDuDepot.de(false, false)).isEqualTo(DernierGesteDuDepot.MARQUER_DEPOSE);
        assertThat(DernierGesteDuDepot.de(false, true))
                .as("sans participation liée, une analyse n'a pas de sens : le geste reste de marquer")
                .isEqualTo(DernierGesteDuDepot.MARQUER_DEPOSE);
        assertThat(DernierGesteDuDepot.de(true, false)).isEqualTo(DernierGesteDuDepot.LANCER_LA_PARTICIPATION);
        assertThat(DernierGesteDuDepot.de(true, true)).isEqualTo(DernierGesteDuDepot.PARTICIPATION_LANCEE);
    }

    @Test
    @DisplayName("#5859 : une participation liée, la dernière étape se nomme « Lancer la participation »")
    void participation_liee_la_derniere_etape_dit_le_geste() {
        assertThat(derniere(DernierGesteDuDepot.LANCER_LA_PARTICIPATION.appliquerAuFil(quatre(StatutWorkflow.DEPOSE))))
                .isEqualTo("4 · Lancer la participation");
        assertThat(derniere(DernierGesteDuDepot.PARTICIPATION_LANCEE.appliquerAuFil(trois(StatutWorkflow.DEPOSE))))
                .as("en trois étapes, le rang suit ; et le nom ne change pas une fois le geste fait")
                .isEqualTo("3 · Lancer la participation");
    }

    @Test
    @DisplayName("#5859 : sans participation liée, le fil est rendu tel qu'il est calculé")
    void sans_participation_le_fil_ne_change_pas() {
        assertThat(DernierGesteDuDepot.MARQUER_DEPOSE.appliquerAuFil(quatre(StatutWorkflow.PRET_A_DEPOSER)))
                .isEqualTo(quatre(StatutWorkflow.PRET_A_DEPOSER));
        assertThat(derniere(DernierGesteDuDepot.MARQUER_DEPOSE.appliquerAuFil(trois(StatutWorkflow.DEPOSE))))
                .isEqualTo("3 · Marquer déposé");
    }

    /// Renommer ne suffisait pas : une nuit déposée rendait toutes ses étapes franchies, donc « Lancer la
    /// participation » s'affichait faite au-dessus d'un bouton encore offert.
    @Test
    @DisplayName("#5859 : nuit déposée, participation à lancer : la dernière étape est courante, pas franchie")
    void la_participation_a_lancer_tient_la_derniere_etape_courante() {
        assertThat(etats(DernierGesteDuDepot.LANCER_LA_PARTICIPATION.appliquerAuFil(quatre(StatutWorkflow.DEPOSE))))
                .containsExactly(EtatEtape.FRANCHIE, EtatEtape.FRANCHIE, EtatEtape.FRANCHIE, EtatEtape.COURANTE);
        assertThat(etats(DernierGesteDuDepot.LANCER_LA_PARTICIPATION.appliquerAuFil(trois(StatutWorkflow.DEPOSE))))
                .containsExactly(EtatEtape.FRANCHIE, EtatEtape.FRANCHIE, EtatEtape.COURANTE);
    }

    @Test
    @DisplayName("#5859 : l'analyse demandée ou faite, tout le fil est franchi")
    void la_participation_lancee_franchit_tout() {
        assertThat(etats(DernierGesteDuDepot.PARTICIPATION_LANCEE.appliquerAuFil(quatre(StatutWorkflow.DEPOSE))))
                .containsOnly(EtatEtape.FRANCHIE);
        assertThat(etats(DernierGesteDuDepot.PARTICIPATION_LANCEE.appliquerAuFil(trois(StatutWorkflow.DEPOSE))))
                .containsOnly(EtatEtape.FRANCHIE);
    }

    /// Le dépôt à la main n'a pas de suite dans l'application : marqué, il est fini (S4-23).
    @Test
    @DisplayName("#5859 : un dépôt marqué à la main garde tout son fil franchi")
    void un_depot_marque_a_la_main_reste_tout_franchi() {
        assertThat(etats(DernierGesteDuDepot.MARQUER_DEPOSE.appliquerAuFil(quatre(StatutWorkflow.DEPOSE))))
                .containsOnly(EtatEtape.FRANCHIE);
    }

    /// Un dépôt partiel offre déjà son bouton de lancement, mais son étape courante reste le
    /// téléversement : c'est lui qui est à finir.
    @Test
    @DisplayName("#5859 : avant la fin du dépôt, le dernier geste ne déplace pas l'étape courante")
    void avant_la_fin_du_depot_le_geste_ne_deplace_rien() {
        List<EtapeDepot> partiel = quatre(StatutWorkflow.DEPOT_EN_COURS);

        assertThat(etats(DernierGesteDuDepot.LANCER_LA_PARTICIPATION.appliquerAuFil(partiel)))
                .containsExactlyElementsOf(etats(partiel));
    }

    @Test
    @DisplayName("#5859 : un fil vide reste vide")
    void un_fil_vide_reste_vide() {
        assertThat(DernierGesteDuDepot.LANCER_LA_PARTICIPATION.appliquerAuFil(List.of()))
                .isEmpty();
    }

    private static List<EtapeDepot> quatre(StatutWorkflow statut) {
        return EtapesDepot.calculer(statut, true, HORS_LIGNE, true);
    }

    private static List<EtapeDepot> trois(StatutWorkflow statut) {
        return EtapesDepot.calculer(statut, false, CONNECTE, false);
    }

    private static String derniere(List<EtapeDepot> etapes) {
        return etapes.get(etapes.size() - 1).libelle();
    }

    private static List<EtatEtape> etats(List<EtapeDepot> etapes) {
        return etapes.stream().map(EtapeDepot::etat).toList();
    }
}

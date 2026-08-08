package fr.univ_amu.iut.audit.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.audit.model.CategorieConstat;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.audit.model.NettoyageDossiersOrphelins;
import fr.univ_amu.iut.audit.model.RapportAudit;
import fr.univ_amu.iut.audit.model.ServiceAuditCoherence;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.ResteDeRestauration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/// Le socle de filtres posé sur l'**Audit de cohérence** (#3100), cinquième écran du chantier #3092.
///
/// Tests **purs** : une `FilteredList` et un `Filtres` ne demandent aucun toolkit JavaFX.
class AuditFiltresTest {

    private static final ConstatAudit ERREUR_42 = new ConstatAudit(
            Severite.ERREUR, CategorieConstat.DISQUE_MANQUANT, 42L, "PaRec_1.wav", "Le fichier est absent.");

    private static final ConstatAudit INFO_42 = new ConstatAudit(
            Severite.INFO, CategorieConstat.DISQUE_ORPHELIN, 42L, "PaRec_2.wav", "Inconnu de la base.");

    private static final ConstatAudit ERREUR_7 =
            new ConstatAudit(Severite.ERREUR, CategorieConstat.AUDIO_DIVERGENT, 7L, "PaRec_3.wav", "Empreinte autre.");

    private static AuditViewModel viewModelAvec(ConstatAudit... constats) {
        ServiceAuditCoherence service = Mockito.mock(ServiceAuditCoherence.class);
        Mockito.when(service.auditerTout()).thenReturn(new RapportAudit(List.of(constats)));
        AuditViewModel viewModel = new AuditViewModel(service, new NettoyageDossiersOrphelins());
        viewModel.rafraichir();
        return viewModel;
    }

    @Test
    @DisplayName("#3100 : une puce posée restreint la liste affichée, sans toucher à l'audit")
    void une_puce_restreint_la_liste_affichee() {
        AuditViewModel viewModel = viewModelAvec(ERREUR_42, INFO_42, ERREUR_7);

        viewModel.filtres().definir("gravite", constat -> constat.severite() == Severite.ERREUR);

        assertThat(viewModel.constatsFiltres()).containsExactly(ERREUR_42, ERREUR_7);
        assertThat(viewModel.constats())
                .as("filtrer masque des lignes, cela n'efface pas le résultat de l'audit")
                .hasSize(3);
    }

    @Test
    @DisplayName("#3100 : le domaine du critère Passage se calcule sur les AUTRES puces (#3095)")
    void le_domaine_du_passage_ignore_la_puce_passage() {
        // Le piège du socle : lire la liste déjà filtrée ferait s'auto-effondrer la puce. Une fois le
        // passage 42 coché, le menu n'offrirait plus que 42, et l'on ne pourrait jamais en cocher un
        // second. « Tous sauf lui » (#3095) est ce qui rend la puce Passage utilisable.
        AuditViewModel viewModel = viewModelAvec(ERREUR_42, INFO_42, ERREUR_7);

        viewModel.filtres().definir("passage", constat -> constat.idPassage() == 42L);

        assertThat(viewModel.filtres().saufLui("passage"))
                .as("le passage 7 doit rester offert alors même que la puce Passage retient 42")
                .containsExactly(ERREUR_42, INFO_42, ERREUR_7);
    }

    @Test
    @DisplayName("#3100 : l'écran sait annoncer une restauration amputée")
    void l_ecran_sait_annoncer_une_restauration_amputee() {
        // Sans bandeau, la mémoire de session remettrait des filtres amputés sans le dire : le défaut
        // même que ce chantier corrige (#3056). Un écran qui reçoit des filtres doit pouvoir en rendre
        // compte.
        AuditViewModel viewModel = viewModelAvec(ERREUR_42);

        viewModel.signalerFiltresDeSessionAmputes(new ResteDeRestauration(List.of("42"), List.of()));

        assertThat(viewModel.retourProperty().get().texte())
                .as("la valeur perdue doit être nommée, sinon l'annonce n'apprend rien")
                .contains("42");

        viewModel.effacerRetour();
        assertThat(viewModel.retourProperty().get().texte()).isEmpty();
    }
}

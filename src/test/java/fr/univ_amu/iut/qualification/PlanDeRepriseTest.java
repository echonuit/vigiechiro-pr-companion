package fr.univ_amu.iut.qualification;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.qualification.model.AvisRevenu;
import fr.univ_amu.iut.qualification.model.PlanDeReprise;
import fr.univ_amu.iut.qualification.model.SequenceSelectionnee;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Ce que la reprise d'un avis garantit **avant** qu'une ligne soit écrite (#4627, ADR 4517).
///
/// Le patron est celui de [fr.univ_amu.iut.passage.model.PlanDePaquet] : on planifie sans écrire, et
/// ce qu'on ne peut pas ranger est nommé plutôt qu'écarté en silence.
class PlanDeRepriseTest {

    private static final Long SELECTION = 11L;

    @Test
    @DisplayName("L'avis se range à côté : le plan ne touche jamais au verdict de l'expéditeur")
    void l_avis_se_range_a_cote_du_verdict_de_l_expediteur() {
        List<SequenceSelectionnee> selection =
                List.of(rattachement(101L, 0, VerdictFichier.BON), rattachement(102L, 1, VerdictFichier.MAUVAIS));
        AvisRevenu avis = new AvisRevenu("martin", Map.of(101L, VerdictFichier.MAUVAIS, 102L, VerdictFichier.MAUVAIS));

        PlanDeReprise plan = PlanDeReprise.pour(selection, avis);

        assertThat(plan.aAppliquer())
                .as("les deux verdicts du relecteur sont repris, dans l'ordre de la sélection")
                .containsExactly(
                        new PlanDeReprise.VerdictRepris(101L, VerdictFichier.MAUVAIS),
                        new PlanDeReprise.VerdictRepris(102L, VerdictFichier.MAUVAIS));
        assertThat(plan.refus())
                .as("rien à refuser, tout tombe dans la sélection")
                .isEmpty();
        assertThat(plan.avisDejaPresent())
                .as("aucun avis antérieur à remplacer")
                .isNull();
    }

    @Test
    @DisplayName("Un verdict hors de la sélection figée fait refuser, et il est nommé")
    void un_verdict_hors_selection_fait_refuser_en_nommant_la_sequence() {
        List<SequenceSelectionnee> selection = List.of(rattachement(101L, 0, VerdictFichier.BON));
        AvisRevenu avis = new AvisRevenu("martin", Map.of(101L, VerdictFichier.MAUVAIS, 999L, VerdictFichier.BON));

        PlanDeReprise plan = PlanDeReprise.pour(selection, avis);

        assertThat(plan.refuse())
                .as("la sélection est figée : ce paquet ne correspond pas à la nuit")
                .isTrue();
        assertThat(plan.refus())
                .as("la séquence en cause est nommée, pas seulement comptée")
                .anySatisfy(motif -> assertThat(motif).contains("999"));
        assertThat(plan.aAppliquer())
                .as("un plan qui refuse n'applique rien, pas même la part valide")
                .isEmpty();
    }

    @Test
    @DisplayName("Un second avis ne s'écrit pas sans dire qui il remplace, ni combien il perd")
    void un_second_avis_annonce_ce_qu_il_remplacerait() {
        List<SequenceSelectionnee> selection = List.of(
                dejaRelu(101L, 0, "claire"), dejaRelu(102L, 1, "claire"), rattachement(103L, 2, VerdictFichier.BON));
        AvisRevenu avis = new AvisRevenu("martin", Map.of(101L, VerdictFichier.BON));

        PlanDeReprise plan = PlanDeReprise.pour(selection, avis);

        assertThat(plan.demandeConfirmation())
                .as("remplacer reste possible, jamais tacite")
                .isTrue();
        assertThat(plan.avisDejaPresent().pseudo())
                .as("qui serait remplacé se nomme")
                .isEqualTo("claire");
        assertThat(plan.avisDejaPresent().verdicts())
                .as("et ce qui serait perdu se compte : deux verdicts, pas trois lignes")
                .isEqualTo(2);
    }

    private static SequenceSelectionnee rattachement(Long idSequence, int position, VerdictFichier verdict) {
        return new SequenceSelectionnee(SELECTION, idSequence, position, false, verdict);
    }

    private static SequenceSelectionnee dejaRelu(Long idSequence, int position, String pseudo) {
        return new SequenceSelectionnee(
                SELECTION, idSequence, position, false, VerdictFichier.BON, VerdictFichier.MAUVAIS, pseudo);
    }
}

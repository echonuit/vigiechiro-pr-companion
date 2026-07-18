package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ResultatEcriture;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.passage.model.EnvoiParticipation;
import fr.univ_amu.iut.passage.model.RattrapageMetadonnees;
import fr.univ_amu.iut.passage.model.RattrapageMetadonnees.BilanRattrapage;
import fr.univ_amu.iut.passage.model.RattrapageMetadonnees.IssuePassage;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

/// Rattrapage des métadonnées en lot (#1861) : la **politique** du lot, testée sans CLI.
///
/// Ce qui est vérifié ici est ce qui distingue un rattrapage utilisable d'une boucle naïve : une nuit qui
/// échoue ne doit pas emporter les suivantes, une écriture *refusée par la plateforme* ne doit pas passer
/// pour un succès, et l'ordre récupérer-puis-envoyer doit être tenu.
class RattrapageMetadonneesTest {

    private final SynchronisationParticipation synchronisation = mock(SynchronisationParticipation.class);
    private final LienVigieChiroDao liens = mock(LienVigieChiroDao.class);
    private final RattrapageMetadonnees rattrapage = new RattrapageMetadonnees(synchronisation, liens);

    private static EnvoiParticipation envoiReussi() {
        return EnvoiParticipation.sansRealignement(ResultatEcriture.reussie());
    }

    @Test
    @DisplayName("#1861 le périmètre est l'ensemble des nuits liées, triées")
    void passages_lies_tries() {
        Map<String, String> table = new LinkedHashMap<>();
        table.put("12", "objectid-12");
        table.put("3", "objectid-3");
        when(liens.tous(LienVigieChiro.ENTITE_PASSAGE)).thenReturn(table);

        assertThat(rattrapage.passagesLies()).containsExactly(3L, 12L);
    }

    @Test
    @DisplayName("#1861 un lien dont la clé locale n'est pas un identifiant est écarté, pas fatal")
    void lien_corrompu_ecarte() {
        Map<String, String> table = new LinkedHashMap<>();
        table.put("7", "objectid-7");
        table.put("pas-un-id", "objectid-x");
        when(liens.tous(LienVigieChiro.ENTITE_PASSAGE)).thenReturn(table);

        assertThat(rattrapage.passagesLies()).containsExactly(7L);
    }

    @Test
    @DisplayName("#1861 récupérer puis envoyer : on part de l'état distant avant de le réécrire (ADR 0020)")
    void recuperer_precede_envoyer() {
        when(synchronisation.pousserVers(1L)).thenReturn(envoiReussi());

        rattrapage.rattraper(List.of(1L), true, true, issue -> {});

        InOrder ordre = Mockito.inOrder(synchronisation);
        ordre.verify(synchronisation).tirerDepuis(1L);
        ordre.verify(synchronisation).pousserVers(1L);
    }

    @Test
    @DisplayName("#1861 best-effort : une nuit qui échoue est ignorée en le disant, le lot continue")
    void best_effort_une_nuit_qui_echoue() {
        when(synchronisation.pousserVers(1L)).thenThrow(new RegleMetierException("Participation introuvable."));
        when(synchronisation.pousserVers(2L)).thenReturn(envoiReussi());
        List<IssuePassage> vues = new ArrayList<>();

        BilanRattrapage bilan = rattrapage.rattraper(List.of(1L, 2L), false, true, vues::add);

        assertThat(bilan).isEqualTo(new BilanRattrapage(1, 1, 0));
        assertThat(vues).hasSize(2);
        assertThat(vues.get(0)).isInstanceOf(IssuePassage.Ignore.class);
        assertThat(((IssuePassage.Ignore) vues.get(0)).cause()).contains("introuvable");
        assertThat(vues.get(1).estTraitee()).isTrue();
    }

    @Test
    @DisplayName("#1861 une écriture REFUSÉE par la plateforme n'est pas un succès (ADR 0008)")
    void ecriture_refusee_compte_comme_ignoree() {
        when(synchronisation.pousserVers(1L))
                .thenReturn(EnvoiParticipation.sansRealignement(ResultatEcriture.echouee("412 Precondition Failed")));
        List<IssuePassage> vues = new ArrayList<>();

        BilanRattrapage bilan = rattrapage.rattraper(List.of(1L), false, true, vues::add);

        // Le geste n'a levé aucune exception : sans regarder le résultat, la nuit aurait été comptée traitée.
        assertThat(bilan).isEqualTo(new BilanRattrapage(0, 1, 0));
        assertThat(((IssuePassage.Ignore) vues.get(0)).cause()).contains("412");
    }

    @Test
    @DisplayName("#1861 les nuits réalignées sont comptées à part : elles disent l'ampleur de la dérive")
    void realignements_comptes_a_part() {
        when(synchronisation.pousserVers(1L))
                .thenReturn(new EnvoiParticipation(
                        ResultatEcriture.reussie(),
                        Optional.of(new EnvoiParticipation.Realignement("15:00", "15:00", "21:00", "06:00"))));
        when(synchronisation.pousserVers(2L)).thenReturn(envoiReussi());
        List<IssuePassage> vues = new ArrayList<>();

        BilanRattrapage bilan = rattrapage.rattraper(List.of(1L, 2L), false, true, vues::add);

        assertThat(bilan).isEqualTo(new BilanRattrapage(2, 0, 1));
        assertThat(vues.get(0).realignement()).isPresent();
        assertThat(vues.get(1).realignement()).isEmpty();
    }

    @Test
    @DisplayName("#1861 récupérer seul n'écrit rien sur la plateforme")
    void recuperer_seul_n_envoie_pas() {
        List<IssuePassage> vues = new ArrayList<>();

        BilanRattrapage bilan = rattrapage.rattraper(List.of(1L), true, false, vues::add);

        verify(synchronisation).tirerDepuis(1L);
        verify(synchronisation, never()).pousserVers(anyLong());
        assertThat(bilan).isEqualTo(new BilanRattrapage(1, 0, 0));
        assertThat(vues.get(0).realignement()).isEmpty();
    }
}

package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.SuiviPagination;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Phase d'**acquisition de l'ancrage** (ADR 0019) : le geste que la réactivation (#1571) et la
/// publication des corrections (#1838) écrivaient à l'identique, jusqu'à ce qu'un renommage doive être
/// fait deux fois.
///
/// Ce qui est vérifié ici est ce que la classe **promet** : se taire quand il n'y a rien à acquérir,
/// préserver les validations quand il y en a, relayer l'avancement **page par page**, et honorer une
/// annulation **à chaque page** plutôt qu'après coup.
class AcquisitionAncrageTest {

    private static final Long PASSAGE = 42L;

    private final ImportObservations importateur = mock(ImportObservations.class);
    private final List<Progression> progressions = new ArrayList<>();
    private final Consumer<Progression> progres = progressions::add;

    @Test
    @DisplayName("nuit non rattachée : rien n'est acquis, rien n'est annoncé, le réseau n'est pas touché")
    void nuit_non_rattachee_ne_declenche_rien() {
        when(importateur.estRattache(PASSAGE)).thenReturn(false);

        RapportAncrage rapport =
                AcquisitionAncrage.acquerirSiNecessaire(importateur, PASSAGE, progres, JetonAnnulation.neutre());

        assertThat(rapport.estMuet())
                .as("dire « rien n'a été fait » après un geste qui n'a rien coûté serait du bruit")
                .isTrue();
        assertThat(progressions)
                .as("pas de progression émise : la barre ne doit pas clignoter pour rien")
                .isEmpty();
        verify(importateur, never()).importer(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("ancrage déjà présent : on ne paie pas un rapatriement inutile")
    void ancrage_deja_present_ne_declenche_rien() {
        when(importateur.estRattache(PASSAGE)).thenReturn(true);
        when(importateur.ancrageManquant(PASSAGE)).thenReturn(false);

        RapportAncrage rapport =
                AcquisitionAncrage.acquerirSiNecessaire(importateur, PASSAGE, progres, JetonAnnulation.neutre());

        assertThat(rapport.estMuet()).isTrue();
        assertThat(progressions).isEmpty();
        verify(importateur, never()).importer(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("ancrage manquant : rapatriement AVEC remplacer=true, pour ne pas coûter ses validations")
    void ancrage_manquant_rapatrie_en_preservant_les_validations() {
        ancrageAAcquerir();
        // Stubbé sur `anyBoolean` À DESSEIN : si le stub n'acceptait que `true`, un appel avec `false`
        // rendrait `null` et le test échouerait sur un NullPointerException opaque. On veut qu'il échoue
        // en NOMMANT le drapeau attendu.
        when(importateur.importer(eq(PASSAGE), anyBoolean(), any())).thenReturn("12 observations ancrées");

        RapportAncrage rapport =
                AcquisitionAncrage.acquerirSiNecessaire(importateur, PASSAGE, progres, JetonAnnulation.neutre());

        assertThat(rapport.texte()).isEqualTo("12 observations ancrées");
        // `remplacer = true` rapatrie l'ancrage EN PRÉSERVANT les validations de l'observateur : aucun
        // de ces deux gestes ne doit lui coûter son travail de revue.
        verify(importateur).importer(eq(PASSAGE), eq(true), any());
    }

    @Test
    @DisplayName("l'avancement est relayé page par page, du libellé partagé à la fraction")
    void l_avancement_est_relaye_page_par_page() {
        ancrageAAcquerir();
        repondEnParcourant((page, total) -> {}, 4);

        AcquisitionAncrage.acquerirSiNecessaire(importateur, PASSAGE, progres, JetonAnnulation.neutre());

        // Une progression d'amorce à 0, puis une par page : sans ce relais la barre resterait figée
        // plusieurs minutes sur un rapatriement de dizaines de pages.
        assertThat(progressions).hasSize(5);
        assertThat(progressions.get(0)).isEqualTo(new Progression(AcquisitionAncrage.LIBELLE, 0.0));
        assertThat(progressions.get(1).libelle()).isEqualTo(AcquisitionAncrage.LIBELLE + " (page 1/4)");
        assertThat(progressions).extracting(Progression::fraction).containsExactly(0.0, 0.25, 0.5, 0.75, 1.0);
    }

    @Test
    @DisplayName("total de pages inconnu : la fraction reste à 0, sans division par zéro ni dépassement")
    void total_inconnu_ne_produit_ni_nan_ni_depassement() {
        ancrageAAcquerir();
        // Le serveur n'annonce pas `_meta.total` : SuiviPagination le signale par un total à 0.
        repondEnParcourant((page, total) -> {}, 0);

        AcquisitionAncrage.acquerirSiNecessaire(importateur, PASSAGE, progres, JetonAnnulation.neutre());

        assertThat(progressions)
                .as("un avancement approché vaut mieux qu'une barre à NaN ou au-delà de 1")
                .extracting(Progression::fraction)
                .containsOnly(0.0);
    }

    @Test
    @DisplayName("« Annuler » est honoré DÈS la première page, pas à la fin du rapatriement")
    void l_annulation_est_honoree_a_chaque_page() {
        ancrageAAcquerir();
        JetonAnnulation jeton = new JetonAnnulation();
        List<Integer> pagesParcourues = new ArrayList<>();
        when(importateur.importer(eq(PASSAGE), anyBoolean(), any())).thenAnswer(invocation -> {
            SuiviPagination suivi = invocation.getArgument(2);
            for (int page = 1; page <= 40; page++) {
                pagesParcourues.add(page);
                jeton.annuler();
                suivi.surPage(page, 40);
            }
            return "jamais atteint";
        });

        assertThatThrownBy(() -> AcquisitionAncrage.acquerirSiNecessaire(importateur, PASSAGE, progres, jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        assertThat(pagesParcourues)
                .as("l'arrêt est coopératif et immédiat : 39 pages de réseau ne sont pas payées pour rien")
                .containsExactly(1);
    }

    @Test
    @DisplayName("les collaborateurs manquants sont refusés à l'entrée, pas découverts en cours de route")
    void les_collaborateurs_sont_obligatoires() {
        JetonAnnulation jeton = JetonAnnulation.neutre();

        assertThatThrownBy(() -> AcquisitionAncrage.acquerirSiNecessaire(null, PASSAGE, progres, jeton))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AcquisitionAncrage.acquerirSiNecessaire(importateur, null, progres, jeton))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AcquisitionAncrage.acquerirSiNecessaire(importateur, PASSAGE, null, jeton))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AcquisitionAncrage.acquerirSiNecessaire(importateur, PASSAGE, progres, null))
                .isInstanceOf(NullPointerException.class);
    }

    /// Le cas où la phase a lieu : nuit rattachée, ancrage absent.
    private void ancrageAAcquerir() {
        when(importateur.estRattache(PASSAGE)).thenReturn(true);
        when(importateur.ancrageManquant(PASSAGE)).thenReturn(true);
    }

    /// Fait jouer au port un parcours de `totalPages` pages, en notifiant le suivi à chacune.
    private void repondEnParcourant(SuiviPagination espion, int totalPages) {
        when(importateur.importer(eq(PASSAGE), anyBoolean(), any())).thenAnswer(invocation -> {
            SuiviPagination suivi = invocation.getArgument(2);
            for (int page = 1; page <= Math.max(totalPages, 1); page++) {
                espion.surPage(page, totalPages);
                suivi.surPage(page, totalPages);
            }
            return "rapatrié";
        });
    }
}

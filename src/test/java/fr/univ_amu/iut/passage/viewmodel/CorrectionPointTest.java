package fr.univ_amu.iut.passage.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.PointsDuCarre;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.DecompteAudio;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.PropositionsEnregistreur;
import fr.univ_amu.iut.passage.model.ServiceConditionsPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceRattachement;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Corriger le **point d'écoute** depuis « Modifier le passage » (#1495).
///
/// Une erreur de point au rattachement est un vrai cas terrain, et le circuit de renommage **transporte
/// déjà** le code du point : `Prefixe(carre, annee, numeroPassage, codePoint)`. Le remède n'ajoute pas
/// une dimension, il libère celle qui était fournie immuable par la navigation.
class CorrectionPointTest {

    private static final Long ID = 42L;

    private final ServicePassage service = mock(ServicePassage.class);
    private final ServiceRattachement rattachement = mock(ServiceRattachement.class);
    private final ServiceConditionsPassage conditions = mock(ServiceConditionsPassage.class);
    private final PropositionsEnregistreur propositions = mock(PropositionsEnregistreur.class);
    private final PointsDuCarre points = mock(PointsDuCarre.class);

    private RattachementViewModel viewModel;

    private static DetailPassage detail(StatutWorkflow statut) {
        return new DetailPassage(
                2,
                2026,
                "2026-06-20",
                "21:00:00",
                "05:00:00",
                "1925492",
                statut,
                Verdict.OK,
                null,
                0L,
                0L,
                6,
                0.0,
                null,
                new DecompteAudio(0, 0));
    }

    @BeforeEach
    void preparer() {
        org.mockito.Mockito.lenient()
                .when(propositions.pour(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(points.codes("040962")).thenReturn(List.of("A1", "B2", "C3"));
        when(service.detailPassage(ID)).thenReturn(detail(StatutWorkflow.TRANSFORME));
        viewModel = new RattachementViewModel(
                service, rattachement, conditions, propositions, Optional.empty(), Optional.empty(), points);
    }

    @Test
    @DisplayName("#1495 : la modale propose les points du même carré, le courant compris")
    void propose_les_points_du_meme_carre() {
        viewModel.ouvrirSur(ID, "040962", "A1");

        assertThat(viewModel.point().disponibles()).containsExactly("A1", "B2", "C3");
        assertThat(viewModel.point().choisiProperty().get()).isEqualTo("A1");
    }

    @Test
    @DisplayName("#1495 : changer le point entraîne un renommage, et le récapitulatif le dit")
    void changer_le_point_entraine_un_renommage() {
        viewModel.ouvrirSur(ID, "040962", "A1");

        viewModel.point().choisiProperty().set("B2");

        assertThat(viewModel.entraineRenommage()).isTrue();
        assertThat(viewModel.sequencesARenommer()).isEqualTo(6);
        // L'avant/après doit montrer le POINT qui change, pas l'année ni le numéro qui, eux, ne bougent
        // pas. C'est le seul endroit où l'utilisateur vérifie qu'il corrige ce qu'il croit corriger.
        assertThat(viewModel.recapProperty().get()).contains("-A1").contains("-B2");
    }

    @Test
    @DisplayName("#1495 : appliquer écrit le nouveau point dans le préfixe")
    void appliquer_ecrit_le_nouveau_point() {
        viewModel.ouvrirSur(ID, "040962", "A1");
        viewModel.point().choisiProperty().set("C3");

        assertThat(viewModel.valider()).isTrue();

        verify(rattachement).modifierRattachement(eq(ID), eq(new Prefixe("040962", 2026, 2, "C3")));
    }

    @Test
    @DisplayName("#1495 : un passage déposé refuse la correction du point, comme le reste du renommage")
    void passage_depose_refuse_la_correction() {
        when(service.detailPassage(ID)).thenReturn(detail(StatutWorkflow.DEPOSE));
        viewModel.ouvrirSur(ID, "040962", "A1");

        viewModel.point().choisiProperty().set("B2");

        // Le verrou de dépôt (#1688) existait pour l'année et le n° ; la correction du point passe par le
        // même `valider()`, donc elle doit en hériter. Vérifié plutôt que supposé.
        assertThat(viewModel.renommageVerrouilleProperty().get()).isTrue();
        assertThat(viewModel.valider()).isFalse();
        verify(rattachement, never())
                .modifierRattachement(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}

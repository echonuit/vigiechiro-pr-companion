package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.Alerte;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [LotViewModel] (état, transitions préparer/déposer). Le [ServiceLot] est
/// mocké : aucune base de données.
@ExtendWith(MockitoExtension.class)
class LotViewModelTest {

    private static final long ID_PASSAGE = 42L;

    @Mock
    private ServiceLot service;

    private LotViewModel viewModel;

    @BeforeEach
    void preparer() {
        viewModel = new LotViewModel(service);
    }

    private static EtatLot etat(StatutWorkflow statut, List<Alerte> alertes, String deposeLe) {
        return new EtatLot(statut, "/ws/session-42", 2, 8192L, alertes, deposeLe);
    }

    @Test
    @DisplayName("ouvrirSur (Vérifié, conforme) : récap + dossier, préparation possible")
    void ouvrir_verifie_permet_preparer() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.VERIFIE, List.of(), null));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.statutProperty().get()).isEqualTo("Vérifié");
        assertThat(viewModel.cheminDossierProperty().get()).isEqualTo("/ws/session-42");
        assertThat(viewModel.recapProperty().get()).isEqualTo("2 séquences · 8 Ko");
        assertThat(viewModel.peutPreparerProperty().get()).isTrue();
        assertThat(viewModel.peutDeposerProperty().get()).isFalse();
        assertThat(viewModel.deposeProperty().get()).isFalse();
        assertThat(viewModel.alertes()).isEmpty();
        assertThat(viewModel.messageProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("ouvrirSur avec alertes bloquantes : préparation impossible, alertes exposées")
    void ouvrir_avec_alertes_bloque_preparer() {
        when(service.consulterLot(ID_PASSAGE))
                .thenReturn(etat(StatutWorkflow.VERIFIE, List.of(Alerte.bloquante("Transformation incomplète")), null));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.peutPreparerProperty().get()).isFalse();
        assertThat(viewModel.alertes()).containsExactly("Transformation incomplète");
        assertThat(viewModel.messageProperty().get()).contains("corrigez");
    }

    @Test
    @DisplayName("ouvrirSur (Prêt à déposer) : dépôt possible, préparation non")
    void ouvrir_pret_a_deposer_permet_deposer() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.PRET_A_DEPOSER, List.of(), null));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.peutDeposerProperty().get()).isTrue();
        assertThat(viewModel.peutPreparerProperty().get()).isFalse();
        assertThat(viewModel.deposeProperty().get()).isFalse();
    }

    @Test
    @DisplayName("ouvrirSur (Déposé) : affiche la date de dépôt")
    void ouvrir_depose_affiche_la_date() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.DEPOSE, List.of(), "2026-06-23T08:00"));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.deposeProperty().get()).isTrue();
        assertThat(viewModel.peutPreparerProperty().get()).isFalse();
        assertThat(viewModel.peutDeposerProperty().get()).isFalse();
        assertThat(viewModel.messageProperty().get()).isEqualTo("Passage déposé le 2026-06-23T08:00.");
    }

    @Test
    @DisplayName("preparer : applique la préparation puis recharge (Prêt à déposer)")
    void preparer_applique_et_recharge() {
        when(service.consulterLot(ID_PASSAGE))
                .thenReturn(
                        etat(StatutWorkflow.VERIFIE, List.of(), null),
                        etat(StatutWorkflow.PRET_A_DEPOSER, List.of(), null));
        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.preparer()).isTrue();
        verify(service).preparerLot(ID_PASSAGE);
        assertThat(viewModel.peutDeposerProperty().get()).isTrue();
        assertThat(viewModel.peutPreparerProperty().get()).isFalse();
    }

    @Test
    @DisplayName("deposer : marque déposé puis recharge (Déposé)")
    void deposer_applique_et_recharge() {
        when(service.consulterLot(ID_PASSAGE))
                .thenReturn(
                        etat(StatutWorkflow.PRET_A_DEPOSER, List.of(), null),
                        etat(StatutWorkflow.DEPOSE, List.of(), "2026-06-23T08:00"));
        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.deposer()).isTrue();
        verify(service).marquerDepose(ID_PASSAGE);
        assertThat(viewModel.deposeProperty().get()).isTrue();
    }

    @Test
    @DisplayName("preparer : une erreur métier est restituée, la vue est préservée")
    void preparer_en_erreur_restitue_le_message() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.VERIFIE, List.of(), null));
        when(service.preparerLot(ID_PASSAGE))
                .thenThrow(new RegleMetierException("Préparation du lot impossible : incohérent"));
        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.preparer()).isFalse();
        assertThat(viewModel.messageProperty().get()).contains("impossible");
        assertThat(viewModel.statutProperty().get()).isEqualTo("Vérifié"); // état non vidé
    }
}

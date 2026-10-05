package fr.univ_amu.iut.lot.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ModeDepot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Les deux questions que la vue pose à l'étape des archives (#5824, #5867) : est-elle une **étape** du
/// fil, ce qui décide des numéros, et les archives **servent**-elles, ce qui décide de la carte et du
/// dépôt manuel. Le repli manuel est le cas où la seconde est vraie sans la première.
///
/// Aucune scène : ce sont des liaisons sur deux modèles de vue, lues sans écran.
class EtapeDesArchivesTest {

    private static final long ID_PASSAGE = 42L;

    private final ServiceLot service = mock(ServiceLot.class);
    private LotViewModel lot;
    private DepotViewModel depot;

    @BeforeEach
    void preparer() {
        lot = new LotViewModel(service);
        depot = new DepotViewModel(service, Optional.of(mock(DepotVigieChiro.class)));
        lenient().when(service.unitesDepot(anyLong())).thenReturn(List.of());
    }

    private void ouvrir(StatutWorkflow statut, ModeDepot forme, boolean connecte, int refusees) {
        when(service.consulterLot(ID_PASSAGE))
                .thenReturn(new EtatLot(statut, "/ws/session-42", 2, 8192L, List.of(), null));
        when(service.formeDuDepot(ID_PASSAGE)).thenReturn(forme);
        when(service.sequencesRefuseesSansRecours(ID_PASSAGE)).thenReturn(refusees);
        lot.declarerDepotAutomatiqueDisponible(connecte);
        lot.ouvrirSur(ID_PASSAGE);
        depot.rehydrater(ID_PASSAGE);
    }

    @Test
    @DisplayName("#5867 : connecté en WAV, des séquences refusées sans recours offrent le repli sans ajouter d'étape")
    void connecte_en_wav_des_refus_offrent_le_repli() {
        ouvrir(StatutWorkflow.DEPOT_EN_COURS, ModeDepot.SEQUENCES_WAV, true, 2);

        assertThat(EtapeDesArchives.offerte(lot).get())
                .as("le fil reste à trois étapes : les numéros ne bougent pas")
                .isFalse();
        assertThat(EtapeDesArchives.enRepli(lot, depot).get()).isTrue();
        assertThat(EtapeDesArchives.servent(lot, depot).get()).isTrue();
    }

    @Test
    @DisplayName("#5867 : connecté en WAV sans refus, ni étape ni repli : la carte reste absente")
    void connecte_en_wav_sans_refus_rien_ne_s_offre() {
        ouvrir(StatutWorkflow.DEPOT_EN_COURS, ModeDepot.SEQUENCES_WAV, true, 0);

        assertThat(EtapeDesArchives.enRepli(lot, depot).get()).isFalse();
        assertThat(EtapeDesArchives.servent(lot, depot).get()).isFalse();
    }

    @Test
    @DisplayName("#5867 : hors connexion la carte est déjà une étape, il n'y a rien à replier")
    void hors_connexion_la_carte_est_une_etape_pas_un_repli() {
        ouvrir(StatutWorkflow.DEPOT_EN_COURS, ModeDepot.SEQUENCES_WAV, false, 2);

        assertThat(EtapeDesArchives.offerte(lot).get()).isTrue();
        assertThat(EtapeDesArchives.enRepli(lot, depot).get()).isFalse();
        assertThat(EtapeDesArchives.servent(lot, depot).get()).isTrue();
    }

    @Test
    @DisplayName("#5867 : en forme ZIP la carte est une étape, même si le plan portait des séquences refusées")
    void en_zip_la_carte_est_une_etape_pas_un_repli() {
        ouvrir(StatutWorkflow.DEPOT_EN_COURS, ModeDepot.ARCHIVES_ZIP, true, 2);

        assertThat(EtapeDesArchives.enRepli(lot, depot).get()).isFalse();
        assertThat(EtapeDesArchives.servent(lot, depot).get()).isTrue();
    }

    @Test
    @DisplayName("#5867 : une fois le passage déposé, le repli se retire")
    void une_fois_depose_le_repli_se_retire() {
        ouvrir(StatutWorkflow.DEPOSE, ModeDepot.SEQUENCES_WAV, true, 2);

        assertThat(EtapeDesArchives.enRepli(lot, depot).get())
                .as("le dernier geste est fait : la carte n'a plus rien à offrir")
                .isFalse();
    }
}

package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [DepotViewModel] (#142) : coordination du téléversement d'une nuit — résolution des
/// séquences via [ServiceLot], dépôt via [DepotVigieChiro], avec dépôt **optionnel** (indisponible hors
/// application connectée). DAO + client mockés, aucun réseau.
@ExtendWith(MockitoExtension.class)
class DepotViewModelTest {

    private static final long ID_PASSAGE = 42L;

    @Mock
    private ServiceLot service;

    @Mock
    private DepotVigieChiro depot;

    @Test
    @DisplayName("disponible() reflète la présence du dépôt (Optional)")
    void disponible_selon_presence() {
        assertThat(new DepotViewModel(service, Optional.of(depot)).disponible()).isTrue();
        assertThat(new DepotViewModel(service, Optional.empty()).disponible()).isFalse();
    }

    @Test
    @DisplayName("televerser dépose les séquences transformées et renvoie le bilan")
    void televerser_depose_les_sequences() {
        List<Path> sequences =
                List.of(Path.of("/ws/session-42/transformes/a.wav"), Path.of("/ws/session-42/transformes/b.wav"));
        when(service.sequencesADeposer(ID_PASSAGE)).thenReturn(sequences);
        when(depot.deposer(eq(ID_PASSAGE), any(), any(), any())).thenReturn(new BilanDepot("part-1", 2, List.of()));

        BilanDepot bilan = new DepotViewModel(service, Optional.of(depot)).televerser(ID_PASSAGE);

        assertThat(bilan.participationId()).isEqualTo("part-1");
        assertThat(bilan.deposees()).isEqualTo(2);
        verify(depot).deposer(eq(ID_PASSAGE), eq(sequences), any(), any());
    }

    @Test
    @DisplayName("#983 : rehydrater() reflète l'état persisté des unités dans la table de dépôt")
    void rehydrater_refile_l_etat_persiste() {
        when(service.unitesDepot(ID_PASSAGE))
                .thenReturn(List.of(
                        new DepotUnite(
                                1L,
                                ID_PASSAGE,
                                "Car-1.zip",
                                TypeDepotUnite.ZIP,
                                StatutDepotUnite.DEPOSE,
                                "obj-1",
                                null,
                                "2026-07-11T14:00:00"),
                        new DepotUnite(
                                2L,
                                ID_PASSAGE,
                                "Car-2.zip",
                                TypeDepotUnite.ZIP,
                                StatutDepotUnite.ECHEC,
                                null,
                                "HTTP 503",
                                "2026-07-11T14:00:00")));
        DepotViewModel vm = new DepotViewModel(service, Optional.empty());

        vm.rehydrater(ID_PASSAGE);

        assertThat(vm.suiviLignes().lignes()).hasSize(2);
        assertThat(vm.suiviLignes().resteAReprendreProperty().get())
                .as("un échec persiste : l'action devient « Retenter les échecs »")
                .isTrue();
    }

    @Test
    @DisplayName("aucune séquence à déposer → refus dur, aucun appel réseau")
    void televerser_sans_sequence_leve() {
        when(service.sequencesADeposer(ID_PASSAGE)).thenReturn(List.of());

        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));

        assertThatThrownBy(() -> vm.televerser(ID_PASSAGE))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Aucune séquence");
    }

    @Test
    @DisplayName("dépôt indisponible (Optional vide, contexte de capture) → refus dur")
    void televerser_indisponible_leve() {
        DepotViewModel vm = new DepotViewModel(service, Optional.empty());

        assertThatThrownBy(() -> vm.televerser(ID_PASSAGE))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("indisponible");
    }

    @Test
    @DisplayName("cycle d'état IHM : en cours → bilan complet / partiel / échec")
    void cycle_etat_ihm() {
        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));
        assertThat(vm.enCoursProperty().get()).isFalse();

        vm.marquerEnCours();
        assertThat(vm.enCoursProperty().get()).isTrue();
        assertThat(vm.messageProperty().get()).contains("en cours");

        vm.appliquerBilan(new BilanDepot("p", 5, List.of()));
        assertThat(vm.enCoursProperty().get()).isFalse();
        assertThat(vm.messageProperty().get()).contains("5 fichier");

        vm.appliquerBilan(new BilanDepot("p", 3, List.of("x.wav")));
        assertThat(vm.messageProperty().get()).contains("échec");

        vm.echec("Token expiré");
        assertThat(vm.enCoursProperty().get()).isFalse();
        assertThat(vm.messageProperty().get()).isEqualTo("Token expiré");
    }
}

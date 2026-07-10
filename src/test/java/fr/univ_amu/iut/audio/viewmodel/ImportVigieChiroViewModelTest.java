package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [ImportVigieChiroViewModel] (axe 4.2) : disponibilité / rattachement, délégation de
/// l'import et cycle d'état IHM (en cours → bilan / échec). [ImportVigieChiro] mocké, aucun réseau.
@ExtendWith(MockitoExtension.class)
class ImportVigieChiroViewModelTest {

    private static final long ID_PASSAGE = 42L;

    @Mock
    private ImportVigieChiro importateur;

    @Test
    @DisplayName("disponible() / rattache() reflètent la présence de l'import et du lien participation")
    void disponible_et_rattache() {
        assertThat(new ImportVigieChiroViewModel(Optional.of(importateur)).disponible())
                .isTrue();
        assertThat(new ImportVigieChiroViewModel(Optional.empty()).disponible()).isFalse();

        when(importateur.estRattache(ID_PASSAGE)).thenReturn(true);
        assertThat(new ImportVigieChiroViewModel(Optional.of(importateur)).rattache(ID_PASSAGE))
                .isTrue();
        assertThat(new ImportVigieChiroViewModel(Optional.empty()).rattache(ID_PASSAGE))
                .isFalse();
    }

    @Test
    @DisplayName("importer délègue au service et renvoie son bilan")
    void importer_delegue() {
        BilanImport bilan = new BilanImport(null, 3, 0, 0);
        when(importateur.importer(ID_PASSAGE, false)).thenReturn(bilan);

        assertThat(new ImportVigieChiroViewModel(Optional.of(importateur)).importer(ID_PASSAGE, false))
                .isSameAs(bilan);
    }

    @Test
    @DisplayName("importer indisponible (Optional vide, contexte de capture) → refus dur")
    void importer_indisponible_leve() {
        ImportVigieChiroViewModel vm = new ImportVigieChiroViewModel(Optional.empty());

        assertThatThrownBy(() -> vm.importer(ID_PASSAGE, false))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("indisponible");
    }

    @Test
    @DisplayName("participations() / rattacher() délèguent (liste vide / no-op si indisponible)")
    void participations_et_rattachement() {
        List<ParticipationVigieChiro> parts = List.of(new ParticipationVigieChiro("6a49", "Z41", "2026-07-03", "S"));
        when(importateur.participationsDisponibles()).thenReturn(parts);

        ImportVigieChiroViewModel present = new ImportVigieChiroViewModel(Optional.of(importateur));
        assertThat(present.participations()).isSameAs(parts);
        present.rattacher(ID_PASSAGE, "6a49");
        verify(importateur).rattacher(ID_PASSAGE, "6a49");

        ImportVigieChiroViewModel absent = new ImportVigieChiroViewModel(Optional.empty());
        assertThat(absent.participations()).isEmpty();
        absent.rattacher(ID_PASSAGE, "6a49"); // sans effet, ne lève pas
    }

    @Test
    @DisplayName("cycle d'état IHM : en cours → bilan (nb observations) / échec")
    void cycle_etat_ihm() {
        ImportVigieChiroViewModel vm = new ImportVigieChiroViewModel(Optional.of(importateur));
        assertThat(vm.enCoursProperty().get()).isFalse();

        vm.marquerEnCours();
        assertThat(vm.enCoursProperty().get()).isTrue();
        assertThat(vm.messageProperty().get()).contains("Récupération");

        vm.appliquerBilan(new BilanImport(null, 7, 0, 0));
        assertThat(vm.enCoursProperty().get()).isFalse();
        assertThat(vm.messageProperty().get()).contains("7 observation");

        vm.echec("Token expiré");
        assertThat(vm.messageProperty().get()).isEqualTo("Token expiré");
    }
}

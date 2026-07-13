package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.BilanPublication;
import fr.univ_amu.iut.validation.model.PublicationCorrections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ViewModel de la publication des corrections (#723) : disponibilité (feature/connexion), cycle
/// en cours → bilan/échec, et résumé lisible du bilan (écarts et refus cités seulement s'il y en a).
class PublicationCorrectionsViewModelTest {

    @Test
    @DisplayName("indisponible sans service (capture, feature coupée) : disponible() false, publier refuse")
    void indisponible_sans_service() {
        PublicationCorrectionsViewModel vm = new PublicationCorrectionsViewModel(Optional.empty());

        assertThat(vm.disponible()).isFalse();
        assertThatThrownBy(() -> vm.publier(7L)).isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> vm.trier(7L)).isInstanceOf(RegleMetierException.class);
    }

    @Test
    @DisplayName("cycle : marquerEnCours pose l'état, appliquerBilan le lève et restitue le résumé")
    void cycle_en_cours_puis_bilan() {
        PublicationCorrections moteur = mock(PublicationCorrections.class);
        when(moteur.publier(7L)).thenReturn(new BilanPublication(2, 0, 0, 0, List.of()));
        PublicationCorrectionsViewModel vm = new PublicationCorrectionsViewModel(Optional.of(moteur));

        vm.marquerEnCours();
        assertThat(vm.enCoursProperty().get()).isTrue();
        assertThat(vm.messageProperty().get()).contains("Publication");

        vm.appliquerBilan(vm.publier(7L));
        assertThat(vm.enCoursProperty().get()).isFalse();
        assertThat(vm.messageProperty().get()).isEqualTo("Corrections publiées vers VigieChiro : 2 envoyée(s).");
    }

    @Test
    @DisplayName("résumé : les écarts et refus ne sont cités que s'il y en a (première cause en exemple)")
    void resume_cite_les_ecarts_presents() {
        assertThat(PublicationCorrectionsViewModel.resume(new BilanPublication(5, 0, 0, 0, List.of())))
                .isEqualTo("Corrections publiées vers VigieChiro : 5 envoyée(s).");

        assertThat(PublicationCorrectionsViewModel.resume(
                        new BilanPublication(1, 2, 1, 3, List.of("Observation 7 : HTTP 404"))))
                .contains("1 envoyée(s)")
                .contains("2 à compléter (certitude non déclarée)")
                .contains("1 sans ancrage plateforme")
                .contains("3 hors référentiel")
                .contains("1 refus, dont : Observation 7 : HTTP 404");
    }

    @Test
    @DisplayName("echec : lève l'état en cours et restitue le message (chaîne vide = annulation)")
    void echec_restitue() {
        PublicationCorrectionsViewModel vm = new PublicationCorrectionsViewModel(Optional.empty());
        vm.marquerEnCours();

        vm.echec("VigieChiro injoignable (non connecté, ou réseau indisponible).");

        assertThat(vm.enCoursProperty().get()).isFalse();
        assertThat(vm.messageProperty().get()).contains("injoignable");
    }
}

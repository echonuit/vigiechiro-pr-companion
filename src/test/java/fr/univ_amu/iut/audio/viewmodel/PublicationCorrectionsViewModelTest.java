package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.RapportAncrage;
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
        // Les portes ouvertes par #1838 refusent aussi : une feature coupée ne doit pas laisser passer
        // une surcharge par la bande (ADR 0003).
        assertThatThrownBy(() -> vm.publier(7L, progres -> {}, JetonAnnulation.neutre()))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> vm.ancrageAcquerable(7L)).isInstanceOf(RegleMetierException.class);
    }

    @Test
    @DisplayName("#1838 : la publication suivie et le prédicat d'ancrage délèguent au service")
    void delegations_ancrage() {
        PublicationCorrections moteur = mock(PublicationCorrections.class);
        BilanPublication bilan = new BilanPublication(1, 0, 0, 0, List.of());
        when(moteur.publier(eq(7L), any(), any())).thenReturn(bilan);
        when(moteur.ancrageAcquerable(7L)).thenReturn(true);
        PublicationCorrectionsViewModel vm = new PublicationCorrectionsViewModel(Optional.of(moteur));

        assertThat(vm.publier(7L, progres -> {}, JetonAnnulation.neutre())).isSameAs(bilan);
        assertThat(vm.ancrageAcquerable(7L)).isTrue();
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
        assertThat(vm.messageProperty().get()).isEqualTo("Corrections publiées vers Vigie-Chiro : 2 envoyée(s).");
    }

    @Test
    @DisplayName("résumé : les écarts et refus ne sont cités que s'il y en a (première cause en exemple)")
    void resume_cite_les_ecarts_presents() {
        assertThat(PublicationCorrectionsViewModel.resume(new BilanPublication(5, 0, 0, 0, List.of())))
                .isEqualTo("Corrections publiées vers Vigie-Chiro : 5 envoyée(s).");

        assertThat(PublicationCorrectionsViewModel.resume(
                        new BilanPublication(1, 2, 1, 3, List.of("Observation 7 : HTTP 404"))))
                .contains("1 envoyée(s)")
                .contains("2 à compléter (certitude non déclarée)")
                .contains("1 sans ancrage plateforme")
                .contains("3 hors référentiel")
                .contains("1 refus, dont : Observation 7 : HTTP 404");
    }

    @Test
    @DisplayName("#1867 : le résumé annonce ce que la phase d'ancrage a ramené, et se tait sinon")
    void resume_annonce_le_rapatriement() {
        BilanPublication sansRapatriement = new BilanPublication(2, 0, 0, 0, List.of());
        assertThat(PublicationCorrectionsViewModel.resume(sansRapatriement))
                .as("nuit déjà ancrée : rien ne s'est passé avant l'envoi, rien à en dire")
                .isEqualTo("Corrections publiées vers Vigie-Chiro : 2 envoyée(s).");

        BilanPublication avecRapatriement = sansRapatriement.avecRapatriement(
                new RapportAncrage("Observations importées depuis Vigie-Chiro : 40 observation(s)."
                        + " Le validateur s'est exprimé sur 3 observation(s)."));
        assertThat(PublicationCorrectionsViewModel.resume(avecRapatriement))
                .as("sans cette phrase, les messages du validateur se découvrent par hasard")
                .contains("2 envoyée(s)")
                .contains("Le validateur s'est exprimé sur 3 observation(s).");
    }

    @Test
    @DisplayName("echec : lève l'état en cours et restitue le message (chaîne vide = annulation)")
    void echec_restitue() {
        PublicationCorrectionsViewModel vm = new PublicationCorrectionsViewModel(Optional.empty());
        vm.marquerEnCours();

        vm.echec("Vigie-Chiro injoignable (non connecté, ou réseau indisponible).");

        assertThat(vm.enCoursProperty().get()).isFalse();
        assertThat(vm.messageProperty().get()).contains("injoignable");
    }
}

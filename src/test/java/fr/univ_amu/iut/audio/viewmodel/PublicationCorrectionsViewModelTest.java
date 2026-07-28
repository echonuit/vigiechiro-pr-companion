package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.validation.model.BilanPublication;
import fr.univ_amu.iut.validation.model.PublicationCorrections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ViewModel de la publication des corrections (#723) : disponibilité (feature/connexion), cycle
/// en cours → bilan/échec, et compte rendu structuré du bilan (écarts cités seulement s'il y en a, refus tous portés).
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
    @DisplayName("cycle : marquerEnCours efface le compte rendu précédent, appliquerBilan pose le nouveau")
    void cycle_en_cours_puis_bilan() {
        PublicationCorrections moteur = mock(PublicationCorrections.class);
        when(moteur.publier(7L)).thenReturn(new BilanPublication(2, 0, 0, 0, List.of()));
        PublicationCorrectionsViewModel vm = new PublicationCorrectionsViewModel(Optional.of(moteur));

        vm.marquerEnCours();
        assertThat(vm.enCoursProperty().get()).isTrue();
        // Démarrer n'ANNONCE plus rien : la progression a sa modale. Ce canal se contente de se taire,
        // pour que le bilan de la publication précédente ne se lise pas comme celui qui travaille.
        assertThat(vm.bilanProperty().get()).isNull();
        assertThat(vm.retourProperty().get().present()).isFalse();

        vm.appliquerBilan(vm.publier(7L));
        assertThat(vm.enCoursProperty().get()).isFalse();
        // Le ViewModel publie le BILAN BRUT depuis #2358 : c'est la surface qui en fait une bande chiffrée
        // (CompteRenduChiffrePublication). Il n'a plus de mise en forme à lui.
        assertThat(vm.bilanProperty().get().poussees()).isEqualTo(2);
    }

    @Test
    @DisplayName("echec : lève l'état en cours et pose le retour (chaîne vide = annulation, rien à dire)")
    void echec_restitue() {
        PublicationCorrectionsViewModel vm = new PublicationCorrectionsViewModel(Optional.empty());
        vm.marquerEnCours();

        vm.echec("Vigie-Chiro injoignable (non connecté, ou réseau indisponible).");

        assertThat(vm.enCoursProperty().get()).isFalse();
        assertThat(vm.retourProperty().get().texte()).contains("injoignable");
        assertThat(vm.retourProperty().get().severite()).isEqualTo(Severite.ERREUR);
        // Un échec n'est pas un compte rendu : il ne s'est rien passé, il n'y a pas de bilan à rendre.
        assertThat(vm.bilanProperty().get()).isNull();

        vm.echec("");
        assertThat(vm.retourProperty().get().present())
                .as("annuler efface le retour au lieu d'annoncer l'annulation")
                .isFalse();
    }
}

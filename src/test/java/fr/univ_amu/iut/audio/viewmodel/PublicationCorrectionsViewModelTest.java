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
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation.Severite;
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
        assertThat(vm.compteRenduProperty().get().estVide()).isTrue();
        assertThat(vm.retourProperty().get().present()).isFalse();

        vm.appliquerBilan(vm.publier(7L));
        assertThat(vm.enCoursProperty().get()).isFalse();
        CompteRendu rendu = vm.compteRenduProperty().get();
        assertThat(rendu.titre()).isEqualTo("Corrections publiées vers Vigie-Chiro");
        assertThat(rendu.constats()).extracting(Constat::fait).containsExactly("2 correction(s) envoyée(s).");
    }

    @Test
    @DisplayName("compte rendu : les écarts ne sont cités que s'il y en a")
    void compte_rendu_cite_les_ecarts_presents() {
        assertThat(PublicationCorrectionsViewModel.construire(new BilanPublication(5, 0, 0, 0, List.of()))
                        .constats())
                .as("annoncer « 0 hors référentiel » serait du bruit")
                .extracting(Constat::fait)
                .containsExactly("5 correction(s) envoyée(s).");

        assertThat(PublicationCorrectionsViewModel.construire(
                                new BilanPublication(1, 2, 1, 3, List.of("Observation 7 : HTTP 404")))
                        .constats())
                .extracting(Constat::fait)
                .containsExactly(
                        "1 correction(s) envoyée(s).",
                        "2 à compléter : certitude non déclarée.",
                        "1 sans ancrage plateforme : rattachez la nuit à sa participation Vigie-Chiro.",
                        "3 hors référentiel.",
                        "1 refus de la plateforme.");
    }

    @Test
    @DisplayName("#2004 : TOUS les refus sont portés en détails, pas seulement le premier")
    void compte_rendu_porte_tous_les_refus() {
        List<String> refus =
                List.of("Observation 7 : HTTP 404", "Observation 12 : HTTP 500", "Observation 19 : taxon inconnu");

        CompteRendu rendu = PublicationCorrectionsViewModel.construire(new BilanPublication(0, 0, 0, 0, refus));

        Constat constat = rendu.constats().stream()
                .filter(c -> c.severite() == Severite.ERREUR)
                .findFirst()
                .orElseThrow();
        // La phrase disait « 3 refus, dont : Observation 7 : HTTP 404 » : deux causes sur trois étaient
        // perdues, faute de place dans un libellé unique. C'est très exactement ce que #2004 traque.
        assertThat(constat.details()).extracting(Detail::sujet).containsExactlyElementsOf(refus);
        assertThat(rendu.severite()).isEqualTo(Severite.ERREUR);
    }

    @Test
    @DisplayName("#1867 : le compte rendu annonce ce que la phase d'ancrage a ramené, et se tait sinon")
    void compte_rendu_annonce_le_rapatriement() {
        BilanPublication sansRapatriement = new BilanPublication(2, 0, 0, 0, List.of());
        assertThat(PublicationCorrectionsViewModel.construire(sansRapatriement).constats())
                .as("nuit déjà ancrée : rien ne s'est passé avant l'envoi, rien à en dire")
                .hasSize(1);

        BilanPublication avecRapatriement = sansRapatriement.avecRapatriement(
                new RapportAncrage("Observations importées depuis Vigie-Chiro : 40 observation(s)."
                        + " Le validateur s'est exprimé sur 3 observation(s)."));
        assertThat(PublicationCorrectionsViewModel.construire(avecRapatriement).constats())
                .as("sans cette phrase, les messages du validateur se découvrent par hasard")
                .extracting(Constat::fait)
                .anySatisfy(fait -> assertThat(fait).contains("Le validateur s'est exprimé sur 3 observation(s)."));
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
        assertThat(vm.compteRenduProperty().get().estVide()).isTrue();

        vm.echec("");
        assertThat(vm.retourProperty().get().present())
                .as("annuler efface le retour au lieu d'annoncer l'annulation")
                .isFalse();
    }
}

package fr.univ_amu.iut.multisite.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.multisite.viewmodel.ReconstructionViewModel.BilanReconstructionGroupe;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.RapportReconstruction;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ViewModel de la modale « Reconstruire un passage manquant » (#1396) : on vérifie que la liste des
/// nuits manquantes est publiée, que le compte rendu **énonce les lacunes** (un passage reconstruit est
/// moins riche qu'un passage archivé par purge), et qu'un refus devient un message plutôt qu'une
/// exception muette venue du fil de fond.
class ReconstructionViewModelTest {

    private static final ParticipationOrpheline ORPHELINE =
            new ParticipationOrpheline("6a53f5faae21902a597394d3", "130711", "Z41", "2026-07-03T22:00:00+02:00", true);

    @Test
    @DisplayName("Sans connexion VigieChiro, l'action est indisponible : l'appelant la retire du menu")
    void indisponible_hors_connexion() {
        ReconstructionViewModel viewModel = new ReconstructionViewModel(Optional.empty());

        assertThat(viewModel.disponible()).isFalse();
        assertThatThrownBy(viewModel::charger)
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("connexion");
    }

    @Test
    @DisplayName("Les nuits manquantes sont publiées, et le message dit combien il en manque")
    void publie_les_orphelines() {
        ServiceReconstructionPassages service = mock(ServiceReconstructionPassages.class);
        when(service.orphelines()).thenReturn(List.of(ORPHELINE));
        ReconstructionViewModel viewModel = new ReconstructionViewModel(Optional.of(service));

        viewModel.appliquer(viewModel.charger());

        assertThat(viewModel.orphelines()).containsExactly(ORPHELINE);
        assertThat(viewModel.messageProperty().get()).contains("1 nuit(s)");
    }

    @Test
    @DisplayName("Aucune nuit manquante n'est une bonne nouvelle, pas une erreur")
    void aucune_orpheline_est_une_bonne_nouvelle() {
        ServiceReconstructionPassages service = mock(ServiceReconstructionPassages.class);
        when(service.orphelines()).thenReturn(List.of());
        ReconstructionViewModel viewModel = new ReconstructionViewModel(Optional.of(service));

        viewModel.appliquer(viewModel.charger());

        assertThat(viewModel.orphelines()).isEmpty();
        assertThat(viewModel.messageProperty().get()).contains("Aucune nuit manquante");
    }

    @Test
    @DisplayName("Le compte rendu énonce les lacunes et invite à réactiver si les fichiers reparaissent")
    void compte_rendu_enonce_les_lacunes() {
        ServiceReconstructionPassages service = mock(ServiceReconstructionPassages.class);
        when(service.orphelines()).thenReturn(List.of(ORPHELINE));
        when(service.reconstruire(eq(ORPHELINE), any(), any()))
                .thenReturn(new RapportReconstruction(7L, 56, 132, RapportReconstruction.lacunesConnues()));
        ReconstructionViewModel viewModel = new ReconstructionViewModel(Optional.of(service));
        viewModel.appliquer(viewModel.charger());

        viewModel.restituer(ORPHELINE, viewModel.reconstruire(ORPHELINE, progression -> {}, JetonAnnulation.neutre()));

        assertThat(viewModel.compteRenduProperty().get())
                .contains("56 séquence(s)")
                .contains("132 observation(s)")
                .contains("Réactiver ce passage");
        RapportReconstruction.lacunesConnues()
                .forEach(lacune ->
                        assertThat(viewModel.compteRenduProperty().get()).contains(lacune));
        assertThat(viewModel.reconstruitProperty().get())
                .as("l'appelant doit savoir qu'il faut recharger sa table")
                .isTrue();
        assertThat(viewModel.orphelines())
                .as("la nuit reconstruite ne manque plus")
                .isEmpty();
    }

    @Test
    @DisplayName("#1708 import groupé : chaque nuit est reconstruite, le bilan additionne séquences/observations")
    void reconstruire_tout_hydrate_chaque_nuit() {
        ServiceReconstructionPassages service = mock(ServiceReconstructionPassages.class);
        ParticipationOrpheline nuit2 =
                new ParticipationOrpheline("autre", "130711", "Z41", "2026-07-04T22:00:00+02:00", true);
        when(service.reconstruire(eq(ORPHELINE), any(), any()))
                .thenReturn(new RapportReconstruction(1L, 10, 20, RapportReconstruction.lacunesConnues()));
        when(service.reconstruire(eq(nuit2), any(), any()))
                .thenReturn(new RapportReconstruction(2L, 5, 8, RapportReconstruction.lacunesConnues()));
        ReconstructionViewModel viewModel = new ReconstructionViewModel(Optional.of(service));

        List<Progression> global = new ArrayList<>();
        BilanReconstructionGroupe bilan = viewModel.reconstruireTout(
                List.of(ORPHELINE, nuit2), global::add, progression -> {}, JetonAnnulation.neutre());

        assertThat(bilan.reussies()).isEqualTo(2);
        assertThat(bilan.ignorees()).isZero();
        assertThat(bilan.sequences()).isEqualTo(15);
        assertThat(bilan.observations()).isEqualTo(28);
        assertThat(global)
                .as("la progression GLOBALE annonce chaque nuit puis « Terminé »")
                .extracting(Progression::libelle)
                .contains("Nuit 1 / 2…", "Nuit 2 / 2…", "Terminé.");
    }

    @Test
    @DisplayName("#1708 import groupé : une nuit qui échoue (point inconnu) est ignorée, le lot continue")
    void reconstruire_tout_best_effort() {
        ServiceReconstructionPassages service = mock(ServiceReconstructionPassages.class);
        ParticipationOrpheline ko =
                new ParticipationOrpheline("ko", "999999", "Z9", "2026-07-04T22:00:00+02:00", false);
        when(service.reconstruire(eq(ORPHELINE), any(), any()))
                .thenReturn(new RapportReconstruction(1L, 10, 20, RapportReconstruction.lacunesConnues()));
        when(service.reconstruire(eq(ko), any(), any()))
                .thenThrow(new RegleMetierException("Le point d'écoute de cette participation n'existe pas ici."));
        ReconstructionViewModel viewModel = new ReconstructionViewModel(Optional.of(service));

        BilanReconstructionGroupe bilan = viewModel.reconstruireTout(
                List.of(ORPHELINE, ko), progression -> {}, progression -> {}, JetonAnnulation.neutre());

        assertThat(bilan.reussies()).isEqualTo(1);
        assertThat(bilan.ignorees()).isEqualTo(1);
        viewModel.restituerLot(bilan);
        assertThat(viewModel.compteRenduProperty().get())
                .contains("1 nuit(s) reconstruite(s)")
                .contains("1 nuit(s) ignorée(s)");
        assertThat(viewModel.reconstruitProperty().get())
                .as("au moins une nuit reconstruite : l'appelant doit recharger sa table")
                .isTrue();
    }

    @Test
    @DisplayName(
            "#1708 import groupé : l'annulation arrête tout le lot (les nuits restantes ne sont pas reconstruites)")
    void reconstruire_tout_annulation_arrete_le_lot() {
        ServiceReconstructionPassages service = mock(ServiceReconstructionPassages.class);
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler(); // annulé d'emblée : la boucle lève au premier tour
        ReconstructionViewModel viewModel = new ReconstructionViewModel(Optional.of(service));

        assertThatThrownBy(() ->
                        viewModel.reconstruireTout(List.of(ORPHELINE), progression -> {}, progression -> {}, jeton))
                .isInstanceOf(OperationAnnuleeException.class);
        verify(service, never()).reconstruire(any(ParticipationOrpheline.class), any(), any());
    }

    @Test
    @DisplayName("Un refus (point inconnu ici) devient un message, pas une exception muette")
    void refus_devient_un_message() {
        ServiceReconstructionPassages service = mock(ServiceReconstructionPassages.class);
        ReconstructionViewModel viewModel = new ReconstructionViewModel(Optional.of(service));

        viewModel.signalerErreur(new RegleMetierException("Le point d'écoute n'existe pas localement."));

        assertThat(viewModel.erreurProperty().get()).isEqualTo("Le point d'écoute n'existe pas localement.");
        assertThat(viewModel.messageProperty().get())
                .as("un refus n'efface pas le constat, et le constat ne devient pas un refus")
                .isEmpty();
    }
}

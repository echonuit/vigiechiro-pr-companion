package fr.univ_amu.iut.multisite.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.RapportReconstruction;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages.BilanReconstructionGroupe;
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
    @DisplayName("#1708 import groupé : le viewModel DÉLÈGUE au service (la boucle y vit) et restitue son bilan")
    void reconstruire_tout_delegue_au_service_et_restitue() {
        ServiceReconstructionPassages service = mock(ServiceReconstructionPassages.class);
        BilanReconstructionGroupe bilan = new BilanReconstructionGroupe(2, 1, 15, 28);
        when(service.reconstruireTout(any(), any(), any(), any(), any())).thenReturn(bilan);
        ReconstructionViewModel viewModel = new ReconstructionViewModel(Optional.of(service));

        BilanReconstructionGroupe rendu = viewModel.reconstruireTout(
                List.of(ORPHELINE), progression -> {}, progression -> {}, JetonAnnulation.neutre());

        assertThat(rendu).as("le viewModel renvoie le bilan du service").isSameAs(bilan);
        viewModel.restituerLot(rendu);
        assertThat(viewModel.compteRenduProperty().get())
                .contains("2 nuit(s) reconstruite(s)")
                .contains("1 nuit(s) ignorée(s)");
        assertThat(viewModel.reconstruitProperty().get())
                .as("au moins une nuit reconstruite : l'appelant doit recharger sa table")
                .isTrue();
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

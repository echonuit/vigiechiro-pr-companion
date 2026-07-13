package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.commun.model.CertitudeObservateur;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.BilanPublication;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.TriPublication;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Flux de la publication depuis la vue (#723) : tri hors fil (aperçu), confirmation récapitulative
/// **injectable** (un `Alert.showAndWait` en dur figerait les tests headless), envoi seulement après
/// accord, restitution. Logique pure (VM mocké, exécuteur synchrone) : aucun TestFX nécessaire.
class PublicationCorrectionsUITest {

    private final PublicationCorrectionsViewModel publication = mock(PublicationCorrectionsViewModel.class);
    private final SourceObservations parPassage =
            new SourceObservations.ParPassage(new ContextePassage(7L, 1, new ContexteSite("640380", "A1", "Mon site")));

    private static Observation publiable() {
        return new Observation(
                1L,
                10L,
                0.0,
                5.0,
                45,
                "Pipkuh",
                0.8,
                null,
                "Pippip",
                null,
                null,
                false,
                ModeValidation.MANUEL,
                100L,
                false,
                "d1",
                0,
                CertitudeObservateur.SUR);
    }

    @Test
    @DisplayName("source sans passage ciblé : ne fait rien (ni tri, ni confirmation)")
    void source_sans_passage() {
        SourceObservations lot = new SourceObservations.ParPassages(List.of(1L, 2L), "Lot filtré");

        PublicationCorrectionsUI.lancer(publication, lot, new ExecuteurTacheSynchrone(), message -> true);

        verify(publication, never()).trier(anyLong());
        verify(publication, never()).publier(anyLong());
    }

    @Test
    @DisplayName("rien de publiable : restitution des écarts, pas de confirmation ni d'envoi")
    void rien_de_publiable() {
        when(publication.trier(7L)).thenReturn(new TriPublication(List.of(), 2, 1, 0));

        PublicationCorrectionsUI.lancer(publication, parPassage, new ExecuteurTacheSynchrone(), message -> {
            throw new AssertionError("aucune confirmation attendue quand rien n'est publiable");
        });

        verify(publication, never()).publier(anyLong());
        verify(publication)
                .echec("Rien à publier : 2 sans certitude déclarée, 1 sans ancrage plateforme."
                        + " Déclarez la certitude des observations corrigées, ou réimportez depuis"
                        + " VigieChiro pour les ancrer.");
    }

    @Test
    @DisplayName("confirmation refusée : état effacé, aucun envoi")
    void confirmation_refusee() {
        when(publication.trier(7L)).thenReturn(new TriPublication(List.of(publiable()), 0, 0, 0));

        PublicationCorrectionsUI.lancer(publication, parPassage, new ExecuteurTacheSynchrone(), message -> false);

        verify(publication, never()).publier(anyLong());
        verify(publication).echec("");
    }

    @Test
    @DisplayName("confirmation acceptée : récapitulatif fidèle, envoi, bilan appliqué")
    void confirmation_acceptee_puis_envoi() {
        when(publication.trier(7L)).thenReturn(new TriPublication(List.of(publiable()), 1, 0, 0));
        BilanPublication bilan = new BilanPublication(1, 1, 0, 0, List.of());
        when(publication.publier(7L)).thenReturn(bilan);
        AtomicReference<String> recapitulatif = new AtomicReference<>();

        PublicationCorrectionsUI.lancer(publication, parPassage, new ExecuteurTacheSynchrone(), message -> {
            recapitulatif.set(message);
            return true;
        });

        assertThat(recapitulatif.get())
                .contains("Publier 1 correction(s)")
                .contains("Resteront à quai : 1 sans certitude déclarée.")
                .contains("ne peut pas être retirée");
        verify(publication).publier(7L);
        verify(publication).appliquerBilan(bilan);
    }
}

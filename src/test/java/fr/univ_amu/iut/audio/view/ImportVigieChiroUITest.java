package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.view.DemandeurDeChoix;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Flux du **voile d'import** depuis la vue (#1543, jumeau de [PublicationCorrectionsUITest]). On couvre le
/// chemin d'un passage **déjà rattaché** (le cœur logique du geste) : sans passage ciblé on ne fait rien ;
/// rattaché sans jeu existant on importe directement ; avec un jeu existant on **confirme le remplacement**
/// (refus → rien ; accord → import en remplaçant). Logique pure (ViewModel mockés, voile synchrone) ; la
/// plateforme JavaFX est initialisée ([ApplicationExtension]) pour que le voile d'occupation se construise,
/// mais aucun robot n'est piloté. Le chemin **non rattaché** (choix de participation, réseau) reste couvert
/// par [ImportVigieChiroViewModelTest].
@ExtendWith(ApplicationExtension.class)
class ImportVigieChiroUITest {

    private final ImportVigieChiroViewModel importVigieChiro = mock(ImportVigieChiroViewModel.class);
    private final AudioViewModel viewModel = mock(AudioViewModel.class);
    private final SourceObservations parPassage =
            new SourceObservations.ParPassage(new ContextePassage(7L, 2, new ContexteSite("640380", "A1", "Mon site")));

    /// Un passage rattaché ne demande jamais de choisir une participation : le demandeur échoue s'il est
    /// sollicité, ce qui prouve qu'on ne prend pas le chemin réseau.
    private final DemandeurDeChoix<ParticipationVigieChiro> demandeurQuiEchoue =
            (entete, question, options, libelle) -> {
                throw new AssertionError("aucun choix de participation attendu sur un passage déjà rattaché");
            };

    /// Voile synchrone : le travail s'exécute immédiatement, l'overlay n'est jamais réellement affiché.
    /// Construit dans [#demarrer] (après l'init de la plateforme JavaFX), pas en champ : le [StackPane] et le
    /// `ProgressIndicator` du voile exigent le toolkit, absent tant que l'instance se construit.
    private IndicateurOccupation voile;

    @BeforeEach
    void demarrer() {
        voile = new IndicateurOccupation(new StackPane(), new ExecuteurTacheSynchrone());
    }

    @Test
    @DisplayName("source sans passage ciblé : ne fait rien (ni rattachement, ni import)")
    void source_sans_passage() {
        SourceObservations lot = new SourceObservations.ParPassages(List.of(1L, 2L), "Lot filtré");

        ImportVigieChiroUI.lancer(importVigieChiro, viewModel, lot, voile, message -> true, demandeurQuiEchoue);

        verify(importVigieChiro, never()).rattache(anyLong());
        verify(importVigieChiro, never()).importer(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("rattaché sans jeu existant : import direct, sans confirmation, puis rechargement")
    void rattache_sans_jeu_importe_directement() {
        when(importVigieChiro.rattache(7L)).thenReturn(true);
        when(viewModel.resultatsDisponiblesProperty()).thenReturn(new SimpleBooleanProperty(false));

        ImportVigieChiroUI.lancer(
                importVigieChiro,
                viewModel,
                parPassage,
                voile,
                message -> {
                    throw new AssertionError("aucune confirmation attendue sans jeu de résultats existant");
                },
                demandeurQuiEchoue);

        verify(importVigieChiro).importer(7L, false);
        verify(viewModel).ouvrirSur(parPassage);
    }

    @Test
    @DisplayName("rattaché avec jeu existant, remplacement refusé : aucun import")
    void rattache_jeu_existant_refus() {
        when(importVigieChiro.rattache(7L)).thenReturn(true);
        when(viewModel.resultatsDisponiblesProperty()).thenReturn(new SimpleBooleanProperty(true));

        ImportVigieChiroUI.lancer(importVigieChiro, viewModel, parPassage, voile, message -> false, demandeurQuiEchoue);

        verify(importVigieChiro, never()).importer(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("rattaché avec jeu existant, remplacement accepté : import en remplaçant")
    void rattache_jeu_existant_accord() {
        when(importVigieChiro.rattache(7L)).thenReturn(true);
        when(viewModel.resultatsDisponiblesProperty()).thenReturn(new SimpleBooleanProperty(true));
        AtomicReference<String> confirmation = new AtomicReference<>();

        ImportVigieChiroUI.lancer(
                importVigieChiro,
                viewModel,
                parPassage,
                voile,
                message -> {
                    confirmation.set(message);
                    return true;
                },
                demandeurQuiEchoue);

        assertThat(confirmation.get()).contains("remplacer").contains("perdues");
        verify(importVigieChiro).importer(7L, true);
        verify(viewModel).ouvrirSur(parPassage);
    }
}

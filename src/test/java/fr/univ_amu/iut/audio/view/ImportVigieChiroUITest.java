package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.view.DemandeurDeChoix;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.PanneauCompteRendu;
import fr.univ_amu.iut.commun.view.SuiviOperation;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.BilanImport;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Flux de l'**import des observations** depuis la vue (#1543, migré en modale de progression annulable
/// #1622). On couvre le chemin d'un passage **déjà rattaché** (le cœur logique du geste) : sans passage
/// ciblé on ne fait rien ; rattaché sans jeu existant on importe directement ; avec un jeu existant on
/// **confirme le remplacement** (refus → rien ; accord → import en remplaçant).
///
/// La modale d'import est injectée par le port [SuiviOperation] : ici un **double synchrone sans fenêtre**
/// qui exécute le travail immédiatement — le geste se teste **hors du fil JavaFX**, sans ouvrir de `Stage`
/// (c'est tout l'intérêt de l'abstraction). La plateforme JavaFX reste initialisée ([ApplicationExtension])
/// pour le voile d'occupation (chemin non rattaché), mais aucun robot n'est piloté. Le chemin **non
/// rattaché** (choix de participation, réseau) reste couvert par [ImportVigieChiroViewModelTest].
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

    /// Modale de progression **synchrone et sans fenêtre** : exécute le travail immédiatement (relais de
    /// progression neutre, jeton vierge) puis restitue succès / annulation / échec, comme le ferait la vraie
    /// modale une fois le travail fini — mais sans `Stage`, donc jouable hors du fil JavaFX.
    private final SuiviOperation suiviSynchrone = new SuiviOperation() {
        @Override
        public <T> void lancer(
                Window proprietaire,
                String titre,
                BiFunction<Consumer<Progression>, JetonAnnulation, T> travail,
                Consumer<T> succes,
                Runnable annule,
                Consumer<Throwable> echec) {
            try {
                succes.accept(travail.apply(progres -> {}, new JetonAnnulation()));
            } catch (OperationAnnuleeException annulee) {
                annule.run();
            } catch (RuntimeException erreur) {
                echec.accept(erreur);
            }
        }
    };

    /// Propriétaire de la modale : sans fenêtre réelle dans ce test logique (le double l'ignore).
    private final Supplier<Window> proprietaire = () -> null;

    /// Voile synchrone (chemin non rattaché) : construit dans [#demarrer] après l'init de la plateforme
    /// JavaFX (le [StackPane] et le `ProgressIndicator` exigent le toolkit).
    private IndicateurOccupation voile;

    @BeforeEach
    void demarrer() {
        voile = new IndicateurOccupation(new StackPane(), new ExecuteurTacheSynchrone());
    }

    @Test
    @DisplayName("#2651 : le bilan traverse le câblage jusqu'à la bande, la phrase d'avancement s'efface")
    void le_bilan_atteint_la_bande() {
        // ViewModel RÉEL, pas le mock : c'est la couture entre lui et la surface qu'on vérifie.
        ImportVigieChiroViewModel vm = new ImportVigieChiroViewModel(java.util.Optional.empty());
        AudioViewModel audio = mock(AudioViewModel.class);
        when(audio.resultatsDisponiblesProperty()).thenReturn(new SimpleBooleanProperty(false));
        VBox zone = new VBox();

        ImportVigieChiroUI.cabler(new MenuItem(), zone, vm, audio);
        vm.appliquerBilan(new BilanImport(null, 128, 12, 3, 41, 2));

        PanneauCompteRendu bande = ImportVigieChiroUI.zoneDuCompteRendu(zone);
        assertThat(bande.isVisible())
                .as("la bande paraît dès qu'un bilan existe")
                .isTrue();
        assertThat(zone.isVisible()).isTrue();
        // Les six nombres jetés autrefois sont là : la proportion dans la barre, le reste en mentions.
        assertThat(textesDe(bande))
                .anyMatch(texte -> texte.contains("2 validation(s) perdue(s)"))
                .anyMatch(texte -> texte.contains("3 taxon(s) hors référentiel"))
                .anyMatch(texte -> texte.contains("12 ligne(s) ignorée(s)"));
    }

    @Test
    @DisplayName("#2651 : sans bilan ni message, la zone s'efface au lieu de laisser un blanc")
    void zone_muette_est_effacee() {
        ImportVigieChiroViewModel vm = new ImportVigieChiroViewModel(java.util.Optional.empty());
        AudioViewModel audio = mock(AudioViewModel.class);
        when(audio.resultatsDisponiblesProperty()).thenReturn(new SimpleBooleanProperty(false));
        VBox zone = new VBox();

        ImportVigieChiroUI.cabler(new MenuItem(), zone, vm, audio);

        assertThat(zone.isVisible()).isFalse();
        assertThat(zone.isManaged()).isFalse();
    }

    /// Les textes des mentions rendues par la bande, lus dans le graphe de scène.
    private static List<String> textesDe(PanneauCompteRendu bande) {
        return bande.lookupAll(".label").stream()
                .filter(Label.class::isInstance)
                .map(noeud -> ((Label) noeud).getText())
                .filter(texte -> texte != null)
                .toList();
    }

    @Test
    @DisplayName("source sans passage ciblé : ne fait rien (ni rattachement, ni import)")
    void source_sans_passage() {
        SourceObservations lot = new SourceObservations.ParPassages(List.of(1L, 2L), "Lot filtré");

        ImportVigieChiroUI.lancer(
                importVigieChiro,
                viewModel,
                lot,
                voile,
                suiviSynchrone,
                proprietaire,
                message -> true,
                demandeurQuiEchoue);

        verify(importVigieChiro, never()).rattache(anyLong());
        verify(importVigieChiro, never()).importer(anyLong(), anyBoolean(), any(), any());
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
                suiviSynchrone,
                proprietaire,
                message -> {
                    throw new AssertionError("aucune confirmation attendue sans jeu de résultats existant");
                },
                demandeurQuiEchoue);

        verify(importVigieChiro).importer(eq(7L), eq(false), any(), any());
        verify(viewModel).ouvrirSur(parPassage);
    }

    @Test
    @DisplayName("rattaché avec jeu existant, remplacement refusé : aucun import")
    void rattache_jeu_existant_refus() {
        when(importVigieChiro.rattache(7L)).thenReturn(true);
        when(viewModel.resultatsDisponiblesProperty()).thenReturn(new SimpleBooleanProperty(true));

        ImportVigieChiroUI.lancer(
                importVigieChiro,
                viewModel,
                parPassage,
                voile,
                suiviSynchrone,
                proprietaire,
                message -> false,
                demandeurQuiEchoue);

        verify(importVigieChiro, never()).importer(anyLong(), anyBoolean(), any(), any());
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
                suiviSynchrone,
                proprietaire,
                message -> {
                    confirmation.set(message);
                    return true;
                },
                demandeurQuiEchoue);

        assertThat(confirmation.get()).contains("remplacer").contains("perdues");
        verify(importVigieChiro).importer(eq(7L), eq(true), any(), any());
        verify(viewModel).ouvrirSur(parPassage);
    }
}

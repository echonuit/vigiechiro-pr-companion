package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.SuiviOperation;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.BilanPublication;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.TriPublication;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Flux de la publication depuis la vue (#723) : tri hors fil (aperçu), confirmation récapitulative
/// **injectable** (un `Alert.showAndWait` en dur figerait les tests headless), envoi seulement après
/// accord, restitution. Logique pure (VM mocké, voile synchrone) ; la plateforme JavaFX est initialisée
/// ([ApplicationExtension]) pour que le voile d'occupation se construise, mais aucun robot n'est piloté.
@ExtendWith(ApplicationExtension.class)
class PublicationCorrectionsUITest {

    private final PublicationCorrectionsViewModel publication = mock(PublicationCorrectionsViewModel.class);
    private final SourceObservations parPassage =
            new SourceObservations.ParPassage(new ContextePassage(7L, 1, new ContexteSite("640380", "A1", "Mon site")));

    /// Modale de progression **synchrone et sans fenetre** : execute le travail immediatement (relais de
    /// progression neutre, jeton vierge) puis restitue succes / annulation / echec, comme le ferait la
    /// vraie modale une fois le travail fini - mais sans `Stage`, donc jouable hors du fil JavaFX. Jumeau
    /// de celui de [ImportVigieChiroUITest], l'envoi passant par la meme modale depuis #1838.
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

    /// Proprietaire de la modale : sans fenetre reelle dans ce test logique (le double l'ignore).
    private final Supplier<Window> proprietaire = () -> null;

    /// Voile synchrone : le travail s'exécute immédiatement, l'overlay n'est jamais réellement affiché.
    /// Construit dans [#demarrer] (après l'init de la plateforme JavaFX), pas en champ : le [StackPane] et
    /// le `ProgressIndicator` du voile exigent le toolkit, absent tant que l'instance se construit.
    private IndicateurOccupation voile;

    @BeforeEach
    void demarrer() {
        voile = new IndicateurOccupation(new StackPane(), new ExecuteurTacheSynchrone());
    }

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
                Certitude.SUR,
                null,
                null);
    }

    @Test
    @DisplayName("source sans passage ciblé : ne fait rien (ni tri, ni confirmation)")
    void source_sans_passage() {
        SourceObservations lot = new SourceObservations.ParPassages(List.of(1L, 2L), "Lot filtré");

        PublicationCorrectionsUI.lancer(publication, lot, voile, suiviSynchrone, proprietaire, message -> true);

        verify(publication, never()).trier(anyLong());
        verify(publication, never()).publier(anyLong(), any(), any());
    }

    @Test
    @DisplayName("rien de publiable : restitution des écarts, pas de confirmation ni d'envoi")
    void rien_de_publiable() {
        when(publication.trier(7L)).thenReturn(new TriPublication(List.of(), 2, 1, 0));

        PublicationCorrectionsUI.lancer(publication, parPassage, voile, suiviSynchrone, proprietaire, message -> {
            throw new AssertionError("aucune confirmation attendue quand rien n'est publiable");
        });

        verify(publication, never()).publier(anyLong(), any(), any());
        verify(publication)
                .echec("Rien à publier : 2 sans certitude déclarée, 1 sans ancrage plateforme."
                        + " Déclarez la certitude des observations corrigées, ou rattachez cette nuit à sa"
                        + " participation VigieChiro pour pouvoir les ancrer.");
    }

    @Test
    @DisplayName("confirmation refusée : état effacé, aucun envoi")
    void confirmation_refusee() {
        when(publication.trier(7L)).thenReturn(new TriPublication(List.of(publiable()), 0, 0, 0));

        PublicationCorrectionsUI.lancer(publication, parPassage, voile, suiviSynchrone, proprietaire, message -> false);

        verify(publication, never()).publier(anyLong(), any(), any());
        verify(publication).echec("");
    }

    @Test
    @DisplayName("confirmation acceptée : récapitulatif fidèle, envoi, bilan appliqué")
    void confirmation_acceptee_puis_envoi() {
        when(publication.trier(7L)).thenReturn(new TriPublication(List.of(publiable()), 1, 0, 0));
        BilanPublication bilan = new BilanPublication(1, 1, 0, 0, List.of());
        when(publication.publier(eq(7L), any(), any())).thenReturn(bilan);
        AtomicReference<String> recapitulatif = new AtomicReference<>();

        PublicationCorrectionsUI.lancer(publication, parPassage, voile, suiviSynchrone, proprietaire, message -> {
            recapitulatif.set(message);
            return true;
        });

        assertThat(recapitulatif.get())
                .contains("Publier 1 correction(s)")
                .contains("Resteront à quai : 1 sans certitude déclarée.")
                .contains("ne peut pas être retirée");
        verify(publication).publier(eq(7L), any(), any());
        verify(publication).appliquerBilan(bilan);
    }

    @Test
    @DisplayName("garde : item grisé + libellé explicite quand la publication est hors d'atteinte (#1596)")
    void garde_avant_publication() {
        when(publication.enCoursProperty()).thenReturn(new SimpleBooleanProperty(false));
        when(publication.messageProperty()).thenReturn(new SimpleStringProperty(""));
        MenuItem item = new MenuItem();
        BooleanProperty publicationImpossible = new SimpleBooleanProperty(false);

        PublicationCorrectionsUI.cabler(item, new Label(), publication, publicationImpossible);

        assertThat(item.isDisable()).as("publication atteignable : offerte").isFalse();
        assertThat(item.getText()).doesNotContain("rattachez");

        publicationImpossible.set(true);
        assertThat(item.isDisable())
                .as("rien d'ancré et nuit non rattachée : publication grisée")
                .isTrue();
        // Le remède annoncé suit la cause : depuis #1838 l'ancrage s'acquiert à la publication, donc le
        // seul verrou restant est le rattachement — plus la réactivation.
        assertThat(item.getText()).contains("rattachez la nuit à sa participation");
    }

    @Test
    @DisplayName("aucun ancrage mais acquérable : on confirme et on publie au lieu de refuser (#1838)")
    void ancrage_a_venir_ne_bloque_pas_la_publication() {
        // L'aperçu ne voit rien de publiable — mais l'ancrage va être rapatrié : conclure « rien à
        // publier » ici, c'est exactement le cul-de-sac que #1838 supprime.
        when(publication.trier(7L)).thenReturn(new TriPublication(List.of(), 0, 3, 0));
        when(publication.ancrageAcquerable(7L)).thenReturn(true);
        BilanPublication bilan = new BilanPublication(3, 0, 0, 0, List.of());
        when(publication.publier(eq(7L), any(), any())).thenReturn(bilan);
        AtomicReference<String> recapitulatif = new AtomicReference<>();

        PublicationCorrectionsUI.lancer(publication, parPassage, voile, suiviSynchrone, proprietaire, message -> {
            recapitulatif.set(message);
            return true;
        });

        assertThat(recapitulatif.get())
                .as("la confirmation annonce l'ancrage à venir plutôt qu'un total qu'elle ne peut pas tenir")
                .contains("3 à ancrer d'abord")
                .contains("vos validations sont préservées")
                .doesNotContain("Resteront à quai");
        verify(publication).publier(eq(7L), any(), any());
        verify(publication).appliquerBilan(bilan);
    }
}

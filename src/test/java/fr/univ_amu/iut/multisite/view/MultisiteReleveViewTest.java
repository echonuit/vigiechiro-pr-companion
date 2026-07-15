package fr.univ_amu.iut.multisite.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import fr.univ_amu.iut.multisite.viewmodel.ReconstructionViewModel;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le geste **« ☁ Relever l'état des analyses »** (#1338) bout en bout, **connecté** : l'item est
/// présent, et le clic interroge VigieChiro pour les seules nuits déposées puis affiche le compte rendu.
///
/// Fixture distincte de [MultisiteViewTest] (qui teste l'écran **hors** connexion, où l'item se retire) :
/// ici le [SuiviTraitement] est présent. L'exécuteur de tâches est **synchrone** par défaut
/// (`@ImplementedBy(ExecuteurTacheSynchrone)`), donc `waitForFxEvents()` suffit à observer le résultat.
@ExtendWith(ApplicationExtension.class)
class MultisiteReleveViewTest {

    private SuiviTraitement suivi;

    private static LignePassage ligne(long id, int numero, StatutWorkflow statut) {
        return new LignePassage(
                id,
                "640380",
                "A1",
                2026,
                numero,
                "2026-06-2" + numero,
                statut,
                Verdict.OK,
                EtatAnalyse.SANS_OBJET,
                null);
    }

    @Start
    void start(Stage stage) throws Exception {
        ServiceMultisite service = mock(ServiceMultisite.class);
        suivi = mock(SuiviTraitement.class);
        when(suivi.releverTout(List.of(42L, 3L))).thenReturn(new SuiviTraitement.BilanReleveGroupe(2, 0));
        when(service.listerPassages(anyString()))
                .thenReturn(List.of(
                        ligne(42L, 1, StatutWorkflow.DEPOSE),
                        ligne(7L, 2, StatutWorkflow.VERIFIE),
                        ligne(3L, 3, StatutWorkflow.DEPOSE)));
        when(service.agregerPourCarte(anyString())).thenReturn(List.of());

        MultisiteViewModel viewModel =
                new MultisiteViewModel(service, mock(ServiceSites.class), Optional.of(suivi), "u-1");
        DepotVues depotVues = mock(DepotVues.class);
        when(depotVues.findByFeature(anyString())).thenReturn(List.of());
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(OuvrirPassage.class).toInstance(mock(OuvrirPassage.class));
                bind(OuvrirAudio.class).toInstance(source -> {});
                bind(DepotVues.class).toInstance(depotVues);
                bind(Navigateur.class).toInstance(mock(Navigateur.class));
            }

            @Provides
            MultisiteViewModel viewModel() {
                return viewModel;
            }

            @Provides
            ReconstructionViewModel reconstruction() {
                return new ReconstructionViewModel(Optional.empty());
            }
        });
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource("Multisite.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        stage.setScene(new Scene(vue, 1100, 680));
        stage.show();
    }

    /// L'item « Relever l'état des analyses » du menu ☰ (un `MenuItem` n'est pas un `Node` : on le
    /// retrouve par son id dans les items du `MenuButton`, comme le reste du menu).
    private static MenuItem itemRelever(FxRobot robot) {
        MenuButton menu = robot.lookup("#menuActions").queryAs(MenuButton.class);
        return menu.getItems().stream()
                .filter(item -> "itemReleverAnalyses".equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Item « Relever l'état des analyses » (#1338) absent du menu."));
    }

    @Test
    @DisplayName("#1338 : connecté, l'item « Relever l'état des analyses » est présent et visible")
    void item_present_et_visible_connecte(FxRobot robot) {
        assertThat(itemRelever(robot).isVisible()).isTrue();
    }

    @Test
    @DisplayName("#1338 : le clic relève les SEULES nuits déposées et affiche le compte rendu")
    void clic_releve_les_nuits_deposees_et_rend_compte(FxRobot robot) {
        robot.interact(itemRelever(robot)::fire);
        WaitForAsyncUtils.waitForFxEvents();

        // Seules les deux nuits déposées (42, 3), pas la nuit vérifiée (7).
        verify(suivi).releverTout(List.of(42L, 3L));
        assertThat(robot.lookup("#lblMessage").queryAs(Label.class).getText()).contains("2 nuit(s)");
    }
}

package fr.univ_amu.iut.multisite.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de l'écran **M-Multisite** : chargement du FXML via Guice (avec un
/// [ServiceMultisite] et un [OuvrirPassage] mockés), auto-chargement du tableau en `initialize()`,
/// vérification du câblage (tableau peuplé, résumé, filtre → ré-interrogation, export actif) et du
/// **drill-down** double-clic → contrat socle `OuvrirPassage`. Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class MultisiteViewTest {

    private ServiceMultisite service;
    private OuvrirPassage ouvrirPassage;

    private static LignePassage ligne(long id, String carre, String point, int annee, int numero, String date) {
        return new LignePassage(id, carre, point, annee, numero, date, StatutWorkflow.DEPOSE, Verdict.OK);
    }

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceMultisite.class);
        ouvrirPassage = mock(OuvrirPassage.class);
        when(service.listerPassages(anyString(), any(), any()))
                .thenReturn(List.of(
                        ligne(42L, "640380", "A1", 2026, 1, "2026-06-21"),
                        ligne(7L, "640381", "B2", 2025, 3, "2025-07-02")));
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(OuvrirPassage.class).toInstance(ouvrirPassage);
                bind(NavigationMultisite.class).toInstance(mock(NavigationMultisite.class));
            }

            @Provides
            MultisiteViewModel viewModel() {
                return new MultisiteViewModel(service, "u-1");
            }
        });
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource("Multisite.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        stage.setScene(new Scene(vue, 1100, 680));
        stage.show();
    }

    @Test
    @DisplayName("Le tableau liste les passages agrégés ; le résumé les compte")
    void affiche_table_et_resume(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableLignes").queryAs(TableView.class);
        Label resume = robot.lookup("#lblResume").queryAs(Label.class);

        assertThat(table.getItems()).hasSize(2);
        assertThat(resume.getText()).contains("2 passage");
    }

    @Test
    @DisplayName("Choisir un filtre de statut ré-interroge le service avec ce critère")
    void filtre_statut_re_interroge(FxRobot robot) {
        ComboBox<?> choixStatut = robot.lookup("#choixStatut").queryAs(ComboBox.class);
        // Items : [Tous(null), IMPORTE, TRANSFORME, VERIFIE, PRET_A_DEPOSER, DEPOSE] → index 4.
        robot.interact(() -> choixStatut.getSelectionModel().select(4));

        ArgumentCaptor<FiltresMultisite> capteur = ArgumentCaptor.forClass(FiltresMultisite.class);
        verify(service, atLeastOnce()).listerPassages(eq("u-1"), capteur.capture(), any());
        assertThat(capteur.getAllValues()).anyMatch(filtre -> filtre.statut() == StatutWorkflow.PRET_A_DEPOSER);
    }

    @Test
    @DisplayName("L'export est actif dès qu'il y a des passages à exporter")
    void export_actif_quand_non_vide(FxRobot robot) {
        Button boutonExporter = robot.lookup("#boutonExporter").queryAs(Button.class);
        assertThat(boutonExporter.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Réinitialiser vide aussi une saisie de carré non validée (sans Entrée)")
    void reinitialiser_vide_la_saisie_non_validee(FxRobot robot) {
        TextField champCarre = robot.lookup("#champCarre").queryAs(TextField.class);
        robot.clickOn("#champCarre").write("640380"); // saisie sans Entrée → VM inchangé
        assertThat(champCarre.getText()).isEqualTo("640380");

        robot.clickOn("#boutonReinitialiser");

        assertThat(champCarre.getText()).isEmpty();
    }

    @Test
    @DisplayName("Double-cliquer une ligne ouvre M-Passage via le contrat socle OuvrirPassage")
    void double_clic_ouvre_le_passage(FxRobot robot) {
        robot.doubleClickOn("2026-06-21"); // date unique de la ligne idPassage = 42

        verify(ouvrirPassage).ouvrir(eq(42L), any(ContexteSite.class));
    }
}

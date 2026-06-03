package fr.univ_amu.iut.validation.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ObservationStatut;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.VueValidation;
import fr.univ_amu.iut.validation.viewmodel.ValidationViewModel;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de l'écran **M-Vision-Tadarida** : chargement du FXML via Guice (avec
/// un [ServiceValidation] mocké), ouverture sur un passage, vérification du câblage (table peuplée,
/// colonne « Statut », sélection qui alimente le détail, progression). Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class ValidationViewTest {

    private ServiceValidation service;

    private static Observation observation(long id, String taxonObservateur) {
        return new Observation(
                id,
                100L + id,
                1.0,
                2.0,
                45000,
                "PIPPIP",
                0.92,
                null,
                taxonObservateur,
                0.9,
                null,
                false,
                ModeValidation.MANUEL,
                7L);
    }

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceValidation.class);
        when(service.chargerValidation(anyLong()))
                .thenReturn(new VueValidation(
                        7L,
                        List.of(
                                new ObservationStatut(observation(1L, null), StatutObservation.NON_TOUCHEE),
                                new ObservationStatut(observation(2L, "PIPPIP"), StatutObservation.VALIDEE))));
        when(service.taxonsDisponibles())
                .thenReturn(List.of(
                        new Taxon("PIPPIP", "Pipistrellus pipistrellus", "Pipistrelle commune", 1L),
                        new Taxon("NYCNOC", "Nyctalus noctula", "Noctule commune", 1L)));
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            ValidationViewModel viewModel() {
                return new ValidationViewModel(service);
            }
        });
        FXMLLoader loader = new FXMLLoader(ValidationController.class.getResource("Validation.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        ValidationController controleur = loader.getController();
        controleur.ouvrirSur(42L);
        stage.setScene(new Scene(vue, 1000, 720));
        stage.show();
    }

    @Test
    @DisplayName("La table liste les observations, la progression compte les revues")
    void affiche_table_et_progression(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        Label progression = robot.lookup("#lblProgression").queryAs(Label.class);

        assertThat(table.getItems()).hasSize(2);
        assertThat(progression.getText()).isEqualTo("1 / 2 revues");
    }

    @Test
    @DisplayName("Sélectionner une ligne alimente le panneau de détail")
    void selection_alimente_detail(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        Label detail = robot.lookup("#lblDetail").queryAs(Label.class);

        assertThat(detail.getText()).isEmpty();
        robot.interact(() -> table.getSelectionModel().select(1));
        assertThat(detail.getText()).contains("Tadarida : PIPPIP").contains("Statut : Validée");
    }

    @Test
    @DisplayName("Le sélecteur de taxon est peuplé ; les boutons de revue s'activent à la sélection")
    void revue_activee_par_la_selection(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        ComboBox<?> choixTaxon = robot.lookup("#choixTaxon").queryAs(ComboBox.class);
        Button btnValider = robot.lookup("#btnValider").queryAs(Button.class);

        assertThat(choixTaxon.getItems()).hasSize(2);
        assertThat(btnValider.isDisabled()).isTrue(); // pas de sélection au départ

        robot.interact(() -> table.getSelectionModel().select(0));
        assertThat(btnValider.isDisabled()).isFalse();

        robot.clickOn("#btnValider");
        // mode par défaut = Activité (validerSelonMode ACTIVITE = valide la seule observation)
        verify(service).validerSelonMode(1L, ModeRevue.ACTIVITE);
    }

    @Test
    @DisplayName("Barre d'actions : résultats chargés → import désactivé, export actif, case cochée")
    void barre_actions_active(FxRobot robot) {
        Button btnImporter = robot.lookup("#btnImporter").queryAs(Button.class);
        CheckBox chkInclureMode = robot.lookup("#chkInclureMode").queryAs(CheckBox.class);
        Button btnExporter = robot.lookup("#btnExporter").queryAs(Button.class);

        // Le mock fournit un jeu de résultats : un seul import permis par passage → import désactivé.
        assertThat(btnImporter.isDisabled()).isTrue();
        assertThat(chkInclureMode.isSelected()).isTrue(); // vrai par défaut (lié au VM)
        assertThat(btnExporter.isDisabled()).isFalse(); // un jeu de résultats est chargé
    }
}

package fr.univ_amu.iut.validation.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ObservationStatut;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.VueValidation;
import fr.univ_amu.iut.validation.viewmodel.ValidationViewModel;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Tests d'intégration TestFX **complémentaires** de l'écran **M-Vision-Tadarida**
/// (`Validation.fxml` + [ValidationController]).
///
/// Là où [ValidationViewTest] couvre l'affichage de base, ce fichier cible les comportements que
/// l'audit signale comme souvent absents (IHM non câblée derrière un ViewModel pourtant complet) :
/// rendu réel des colonnes (`cellValueFactory`), parcours **corriger** (activation par le taxon +
/// délégation au service), respect du **mode de revue** choisi (R18), liaison bidirectionnelle de
/// la case « Inclure le mode » (R24), converter + liaison du filtre de statut, et refus de corriger
/// vers la proposition Tadarida (message d'état visible).
///
/// Chaque test fait un **vrai lookup** des contrôles par `fx:id` (`robot.lookup("#…")`) puis exerce
/// une **interaction** (clic / sélection) : un écran resté à l'état placeholder (sans `@FXML` ni
/// `onAction`) échoue donc, contrairement aux tests qui ne liraient que les propriétés du VM.
/// Aucune base de données : le [ServiceValidation] est mocké (Mockito), le VM est injecté via Guice.
@ExtendWith(ApplicationExtension.class)
class ValidationVueIntegrationTest {

    private ServiceValidation service;
    private ValidationViewModel viewModel;
    private ValidationController controleur;

    /// Construit une observation de test : `id` technique, séquence dérivée (`100 + id`), taxon
    /// Tadarida toujours `PIPPIP`, taxon observateur paramétrable (`null` = non touchée).
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
        when(service.cheminAudio(anyLong())).thenReturn(Optional.of(Path.of("/ws/seq.wav")));

        // VM conservé dans un champ pour pouvoir asserter ses propriétés (liaisons bidirectionnelles).
        viewModel = new ValidationViewModel(service);
        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    ValidationViewModel viewModel() {
                        return viewModel;
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(ValidationController.class.getResource("Validation.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.ouvrirSur(new ContextePassage(42L, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière")));
        stage.setScene(new Scene(vue, 1000, 720));
        stage.show();
    }

    @Test
    @DisplayName("Emplacement (fil d'Ariane) : Mes sites › Carré N › Détails du passage N° X › Validation Tadarida")
    void emplacement_reflete_le_passage() {
        assertThat(controleur.emplacement())
                .extracting(Lieu::libelle)
                .containsExactly("Mes sites", "Carré 640380", "Détails du passage N° 2", "Validation Tadarida");
        assertThat(controleur.emplacement().get(0).estCliquable()).isTrue();
        assertThat(controleur.emplacement().get(3).estCliquable()).isFalse();
    }

    @Test
    @DisplayName("Les colonnes Espèce/Statut rendent les valeurs câblées (cellValueFactory)")
    void colonnes_rendent_espece_et_statut(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);

        assertThat(table.getColumns()).hasSize(2);
        @SuppressWarnings("unchecked")
        TableColumn<ObservationStatut, String> colEspece =
                (TableColumn<ObservationStatut, String>) table.getColumns().get(0);
        @SuppressWarnings("unchecked")
        TableColumn<ObservationStatut, String> colStatut =
                (TableColumn<ObservationStatut, String>) table.getColumns().get(1);

        // La colonne Espèce expose le taxon Tadarida ; la colonne Statut le libellé de revue.
        assertThat(colEspece.getCellData(0)).isEqualTo("PIPPIP");
        assertThat(colStatut.getCellData(0)).isEqualTo("À revoir"); // NON_TOUCHEE
        assertThat(colStatut.getCellData(1)).isEqualTo("Validée"); // VALIDEE
    }

    @Test
    @DisplayName("Corriger : activé par le choix d'un taxon, le clic délègue au service")
    void corriger_active_par_taxon_et_delegue_au_service(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        ComboBox<?> choixTaxon = robot.lookup("#choixTaxon").queryAs(ComboBox.class);
        Button btnCorriger = robot.lookup("#btnCorriger").queryAs(Button.class);

        assertThat(btnCorriger.isDisabled()).isTrue(); // ni sélection ni taxon
        robot.interact(() -> table.getSelectionModel().select(0)); // observation id=1
        assertThat(btnCorriger.isDisabled()).isTrue(); // sélection mais pas de taxon choisi
        robot.interact(() -> choixTaxon.getSelectionModel().select(1)); // NYCNOC (≠ Tadarida PIPPIP)
        assertThat(btnCorriger.isDisabled()).isFalse();

        robot.clickOn("#btnCorriger");
        verify(service).corriger(1L, "NYCNOC", null);
    }

    @Test
    @DisplayName("Valider respecte le mode de revue choisi (INVENTAIRE, R18)")
    void valider_respecte_le_mode_inventaire(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        @SuppressWarnings("unchecked")
        ComboBox<ModeRevue> choixMode = robot.lookup("#choixMode").queryAs(ComboBox.class);
        Button btnValider = robot.lookup("#btnValider").queryAs(Button.class);

        robot.interact(() -> choixMode.getSelectionModel().select(ModeRevue.INVENTAIRE));
        robot.interact(() -> table.getSelectionModel().select(0)); // observation id=1
        assertThat(btnValider.isDisabled()).isFalse();

        robot.clickOn("#btnValider");
        // Le mode choisi (INVENTAIRE) est propagé au service, pas le défaut ACTIVITE.
        verify(service).validerSelonMode(1L, ModeRevue.INVENTAIRE);
    }

    @Test
    @DisplayName("La case « Inclure le mode » est liée bidirectionnellement au ViewModel (R24)")
    void case_inclure_mode_liee_au_viewmodel(FxRobot robot) {
        CheckBox chkInclureMode = robot.lookup("#chkInclureMode").queryAs(CheckBox.class);

        assertThat(chkInclureMode.isSelected()).isTrue(); // vrai par défaut (côté VM)
        assertThat(viewModel.inclureModeProperty().get()).isTrue();

        robot.clickOn("#chkInclureMode"); // décoche depuis l'IHM

        assertThat(chkInclureMode.isSelected()).isFalse();
        assertThat(viewModel.inclureModeProperty().get()).isFalse(); // la case pilote bien le VM
    }

    @Test
    @DisplayName("Le filtre de statut : converter lisible, sélection liée au VM et restreint la table")
    void filtre_converter_et_liaison_vm(FxRobot robot) {
        @SuppressWarnings("unchecked")
        ComboBox<StatutObservation> choixFiltre = robot.lookup("#choixFiltre").queryAs(ComboBox.class);
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);

        // Items : [Tous(null), NON_TOUCHEE, VALIDEE, CORRIGEE].
        assertThat(choixFiltre.getItems()).hasSize(4);
        assertThat(choixFiltre.getConverter().toString(null)).isEqualTo("Tous les statuts");
        assertThat(choixFiltre.getConverter().toString(StatutObservation.VALIDEE))
                .isEqualTo("Validée");

        assertThat(table.getItems()).hasSize(2); // pas de filtre au départ
        robot.interact(() -> choixFiltre.getSelectionModel().select(StatutObservation.VALIDEE));

        assertThat(viewModel.filtreStatutProperty().get()).isEqualTo(StatutObservation.VALIDEE);
        assertThat(table.getItems()).hasSize(1); // seule l'observation validée subsiste
    }

    @Test
    @DisplayName("Corriger vers la proposition Tadarida est refusé : message visible, service non appelé")
    void corriger_vers_proposition_tadarida_refuse(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        ComboBox<?> choixTaxon = robot.lookup("#choixTaxon").queryAs(ComboBox.class);
        Button btnCorriger = robot.lookup("#btnCorriger").queryAs(Button.class);
        Label lblMessage = robot.lookup("#lblMessage").queryAs(Label.class);

        robot.interact(() -> table.getSelectionModel().select(1)); // observation id=2 (Tadarida PIPPIP)
        robot.interact(() -> choixTaxon.getSelectionModel().select(0)); // PIPPIP == proposition Tadarida
        assertThat(btnCorriger.isDisabled()).isFalse();

        robot.clickOn("#btnCorriger");

        // Corriger vers Tadarida serait une validation : refusé sans toucher au service…
        verify(service, never()).corriger(anyLong(), anyString(), any());
        // …et un message d'état s'affiche (lblMessage visible quand le message n'est pas vide).
        assertThat(lblMessage.isVisible()).isTrue();
        assertThat(lblMessage.getText()).contains("Valider");
    }
}

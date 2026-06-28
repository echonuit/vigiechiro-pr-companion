package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.analyse.viewmodel.Regroupement;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de l'écran **« Espèces & observations »** : chargement du FXML via Guice
/// (avec un [ServiceAnalyse] mocké), table par espèce affichée par défaut, et bascule Par carré qui
/// montre la table des carrés. Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class AnalyseViewTest {

    private ServiceAnalyse service;
    private OuvrirPassage ouvrirPassage;
    private AnalyseController controleur;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceAnalyse.class);
        ouvrirPassage = mock(OuvrirPassage.class);
        when(service.inventaireParEspece(anyString(), any()))
                .thenReturn(List.of(new EspeceAgregee(
                        "Pippip",
                        "Pipistrellus pipistrellus",
                        "Pipistrelle commune",
                        "Pipistrellus",
                        5,
                        2,
                        1,
                        1,
                        2026,
                        2026)));
        when(service.inventaireParCarre(anyString(), any()))
                .thenReturn(List.of(new CarreEspeces("640380", "Étang", 4, 10, 2025, 2026)));
        when(service.observationsDeLEspece(anyString(), anyString(), any()))
                .thenReturn(List.of(new ObservationEspece(
                        7L,
                        70L,
                        42L,
                        2,
                        2026,
                        "2026-06-22",
                        "640380",
                        "A1",
                        "Étang",
                        "Pippip",
                        0.9,
                        "Pippip",
                        0.95,
                        StatutObservation.VALIDEE)));
        OuvrirPassage navigation = ouvrirPassage;
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            AnalyseViewModel viewModel() {
                return new AnalyseViewModel(service, "u-1");
            }

            @Provides
            OuvrirPassage ouvrirPassage() {
                return navigation;
            }
        });
        FXMLLoader loader = new FXMLLoader(AnalyseController.class.getResource("Analyse.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        stage.setScene(new Scene(vue, 1000, 640));
        stage.show();
    }

    @Test
    @DisplayName("Par défaut : la table par espèce est affichée et peuplée ; le résumé compte les espèces")
    void affiche_inventaire_par_espece_par_defaut(FxRobot robot) {
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        TableView<?> carres = robot.lookup("#tableCarres").queryAs(TableView.class);

        assertThat(especes.isVisible()).isTrue();
        assertThat(carres.isVisible()).isFalse();
        assertThat(especes.getItems()).hasSize(1);
        assertThat(robot.lookup("#lblResume").queryAs(Label.class).getText()).contains("espèce");
    }

    @Test
    @DisplayName("Basculer Par carré affiche la table des carrés (richesse spécifique)")
    void basculer_par_carre_affiche_les_carres(FxRobot robot) {
        @SuppressWarnings("unchecked")
        ComboBox<Regroupement> choixRegroupement =
                robot.lookup("#choixRegroupement").queryAs(ComboBox.class);
        robot.interact(() -> choixRegroupement.getSelectionModel().select(Regroupement.PAR_CARRE));

        TableView<?> carres = robot.lookup("#tableCarres").queryAs(TableView.class);
        assertThat(carres.isVisible()).isTrue();
        assertThat(carres.getItems()).hasSize(1);
        assertThat(robot.lookup("#tableEspeces").queryAs(TableView.class).isVisible())
                .isFalse();
    }

    @Test
    @DisplayName("Au retour sur l'écran, l'inventaire est rechargé (RafraichirAuRetour)")
    void retour_recharge_l_inventaire(FxRobot robot) {
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        assertThat(especes.getItems()).as("état initial").hasSize(1);

        // Des observations ont été validées ailleurs : le service renvoie désormais deux espèces.
        when(service.inventaireParEspece(anyString(), any()))
                .thenReturn(List.of(
                        new EspeceAgregee(
                                "Pippip",
                                "Pipistrellus pipistrellus",
                                "Pipistrelle commune",
                                "Pipistrellus",
                                5,
                                2,
                                1,
                                1,
                                2026,
                                2026),
                        new EspeceAgregee(
                                "Nyclei",
                                "Nyctalus leisleri",
                                "Noctule de Leisler",
                                "Nyctalus",
                                3,
                                1,
                                1,
                                1,
                                2026,
                                2026)));
        robot.interact(controleur::rafraichirAuRetour);

        assertThat(especes.getItems())
                .as("le retour recharge l'inventaire (plus de compteurs périmés)")
                .hasSize(2);
    }

    @Test
    @DisplayName("Sélectionner une espèce remplit le détail ; « Ouvrir le passage » navigue vers M-Passage")
    void selectionner_espece_affiche_le_detail_et_ouvre_le_passage(FxRobot robot) {
        @SuppressWarnings("unchecked")
        TableView<Object> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        TableView<?> observations = robot.lookup("#tableObservations").queryAs(TableView.class);
        Button ouvrir = robot.lookup("#boutonOuvrirPassage").queryAs(Button.class);
        assertThat(observations.getItems()).as("détail vide au départ").isEmpty();
        assertThat(ouvrir.isDisabled()).as("rien à ouvrir sans sélection").isTrue();

        // Sélectionner l'espèce charge ses observations (à travers les passages).
        robot.interact(() -> especes.getSelectionModel().select(0));
        assertThat(observations.getItems()).hasSize(1);

        // Sélectionner une observation active le bouton, qui ouvre le bon passage avec son contexte.
        robot.interact(() -> observations.getSelectionModel().select(0));
        assertThat(ouvrir.isDisabled()).isFalse();
        robot.interact(ouvrir::fire);

        ArgumentCaptor<ContexteSite> contexte = ArgumentCaptor.forClass(ContexteSite.class);
        verify(ouvrirPassage).ouvrir(eq(42L), contexte.capture());
        assertThat(contexte.getValue().numeroCarre()).isEqualTo("640380");
        assertThat(contexte.getValue().codePoint()).isEqualTo("A1");
        assertThat(contexte.getValue().nomSite()).isEqualTo("Étang");
    }
}

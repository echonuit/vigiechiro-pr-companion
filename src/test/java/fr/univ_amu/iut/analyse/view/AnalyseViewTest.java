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
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX de l'écran **« Espèces & observations »** : chargement du FXML via Guice
/// (avec un [ServiceAnalyse] mocké), table par espèce affichée par défaut, et bascule Par carré qui
/// montre la table des carrés. Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class AnalyseViewTest {

    private ServiceAnalyse service;
    private OuvrirPassage ouvrirPassage;
    private OuvrirAudio ouvrirAudio;
    private AnalyseController controleur;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceAnalyse.class);
        ouvrirPassage = mock(OuvrirPassage.class);
        ouvrirAudio = mock(OuvrirAudio.class);
        when(service.observationsAnalyse(anyString())).thenReturn(List.of(obsAnalyse("Pippip", "Pipistrelle commune")));
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
        OuvrirPassage navigationPassage = ouvrirPassage;
        OuvrirAudio navigationAudio = ouvrirAudio;
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            AnalyseViewModel viewModel() {
                return new AnalyseViewModel(service, "u-1");
            }

            @Provides
            OuvrirPassage ouvrirPassage() {
                return navigationPassage;
            }

            @Provides
            OuvrirAudio ouvrirAudio() {
                return navigationAudio;
            }
        });
        FXMLLoader loader = new FXMLLoader(AnalyseController.class.getResource("Analyse.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        stage.setScene(new Scene(vue, 1000, 640));
        stage.show();
    }

    /// Une observation enrichie de l'espèce `taxon` sur le carré 640380 (statut validé) : matière brute que
    /// le ViewModel filtre puis agrège (#537).
    private static ObservationAnalyse obsAnalyse(String taxon, String vern) {
        return new ObservationAnalyse(
                taxon,
                taxon + " (latin)",
                vern,
                "Chiroptères",
                StatutObservation.VALIDEE,
                42L,
                2026,
                "640380",
                "Étang",
                1L);
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
        when(service.observationsAnalyse(anyString()))
                .thenReturn(List.of(
                        obsAnalyse("Pippip", "Pipistrelle commune"), obsAnalyse("Nyclei", "Noctule de Leisler")));
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

    @Test
    @DisplayName("« Écouter / valider » ouvre la vue audio sur l'espèce, ciblée sur l'observation")
    void ecouter_valider_ouvre_la_vue_audio_sur_l_espece(FxRobot robot) {
        @SuppressWarnings("unchecked")
        TableView<Object> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        TableView<?> observations = robot.lookup("#tableObservations").queryAs(TableView.class);
        Button ecouter = robot.lookup("#boutonEcouter").queryAs(Button.class);
        assertThat(ecouter.isDisabled()).as("rien à écouter sans sélection").isTrue();

        robot.interact(() -> especes.getSelectionModel().select(0));
        robot.interact(() -> observations.getSelectionModel().select(0));
        assertThat(ecouter.isDisabled()).isFalse();
        robot.interact(ecouter::fire);

        // Ouvre la vue audio sur TOUTE l'espèce (source ParEspece), focalisée sur la détection cliquée.
        ArgumentCaptor<SourceObservations> source = ArgumentCaptor.forClass(SourceObservations.class);
        verify(ouvrirAudio).ouvrir(source.capture(), eq(7L));
        assertThat(source.getValue()).isInstanceOfSatisfying(SourceObservations.ParEspece.class, espece -> {
            assertThat(espece.idUtilisateur()).isEqualTo("u-1");
            assertThat(espece.codeEspece()).isEqualTo("Pippip");
            assertThat(espece.statut()).as("aucun filtre de statut actif").isNull();
        });
    }

    @Test
    @DisplayName("La bascule « 🗺️ Carte » affiche la carte de répartition à la place des tables")
    void bascule_carte_affiche_la_carte_de_repartition(FxRobot robot) {
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        StackPane zoneCarte = robot.lookup("#zoneCarte").queryAs(StackPane.class);
        Button boutonCarte = robot.lookup("#boutonCarte").queryAs(Button.class);
        assertThat(zoneCarte.isVisible()).as("carte masquée au départ").isFalse();
        assertThat(especes.isVisible()).isTrue();

        robot.interact(boutonCarte::fire);

        assertThat(zoneCarte.isVisible()).as("la carte remplace les tables").isTrue();
        assertThat(especes.isVisible()).isFalse();
        assertThat(boutonCarte.getText()).contains("Tableau");
        assertThat(robot.lookup(".carte-legende").tryQuery())
                .as("la légende de richesse est superposée")
                .isPresent();

        robot.interact(boutonCarte::fire);

        assertThat(zoneCarte.isVisible()).isFalse();
        assertThat(especes.isVisible()).isTrue();
        assertThat(boutonCarte.getText()).contains("Carte");
    }

    @Test
    @DisplayName("#537 : ajouter un filtre « Statut » via la barre à puces filtre l'inventaire côté client")
    void filtre_statut_via_la_barre_a_puces(FxRobot robot) {
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        assertThat(especes.getItems())
                .as("l'espèce validée est affichée au départ")
                .hasSize(1);

        // Ouvrir le menu « + Filtre » et ajouter la puce Statut (l'item porte le libellé du critère).
        MenuButton menuAjout = robot.lookup("#menuAjoutFiltre").queryAs(MenuButton.class);
        MenuItem itemStatut = menuAjout.getItems().stream()
                .filter(item -> "Statut".equals(item.getText()))
                .findFirst()
                .orElseThrow();
        robot.interact(itemStatut::fire);
        WaitForAsyncUtils.waitForFxEvents();

        // Choisir « Non touchée » dans la liste de la puce : la seule observation (validée) est écartée →
        // l'inventaire se vide, ce qui prouve que la puce filtre bien la table côté client.
        FlowPane puces = robot.lookup("#pucesFiltres").queryAs(FlowPane.class);
        @SuppressWarnings("unchecked")
        ComboBox<StatutObservation> choixStatut = (ComboBox<StatutObservation>)
                robot.from(puces).lookup(".combo-box").queryAs(ComboBox.class);
        robot.interact(() -> choixStatut.setValue(StatutObservation.NON_TOUCHEE));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(especes.getItems())
                .as("le filtre statut « Non touchée » écarte l'observation validée")
                .isEmpty();
    }
}

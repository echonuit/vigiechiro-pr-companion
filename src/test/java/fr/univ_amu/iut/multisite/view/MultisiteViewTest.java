package fr.univ_amu.iut.multisite.view;

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
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import fr.univ_amu.iut.sites.model.ServiceSites;
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
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX de l'écran **M-Multisite** : chargement du FXML via Guice (avec un
/// [ServiceMultisite] et un [OuvrirPassage] mockés), auto-chargement du tableau en `initialize()`,
/// vérification du câblage (tableau peuplé, résumé, filtre → tableau filtré en mémoire, export actif)
/// et du **drill-down** double-clic → contrat socle `OuvrirPassage`. Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class MultisiteViewTest {

    private ServiceMultisite service;
    private ServiceSites serviceSites;
    private OuvrirPassage ouvrirPassage;
    private OuvrirAudio ouvrirAudio;
    private MultisiteViewModel viewModel;

    private static LignePassage ligne(long id, String carre, String point, int annee, int numero, String date) {
        return ligne(id, carre, point, annee, numero, date, StatutWorkflow.DEPOSE);
    }

    private static LignePassage ligne(
            long id, String carre, String point, int annee, int numero, String date, StatutWorkflow statut) {
        return new LignePassage(id, carre, point, annee, numero, date, statut, Verdict.OK);
    }

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceMultisite.class);
        serviceSites = mock(ServiceSites.class);
        ouvrirPassage = mock(OuvrirPassage.class);
        ouvrirAudio = mock(OuvrirAudio.class);
        when(service.listerPassages(anyString()))
                .thenReturn(List.of(
                        ligne(42L, "640380", "A1", 2026, 1, "2026-06-21"),
                        ligne(7L, "640381", "B2", 2025, 3, "2025-07-02", StatutWorkflow.VERIFIE)));
        when(service.agregerPourCarte(anyString())).thenReturn(List.of()); // carte (#152) : pas de NPE à l'init
        viewModel = new MultisiteViewModel(service, serviceSites, "u-1");
        DepotVues depotVues = mock(DepotVues.class);
        when(depotVues.findByFeature(anyString())).thenReturn(List.of());
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(OuvrirPassage.class).toInstance(ouvrirPassage);
                bind(OuvrirAudio.class).toInstance(ouvrirAudio);
                bind(DepotVues.class).toInstance(depotVues);
            }

            @Provides
            MultisiteViewModel viewModel() {
                return viewModel;
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
    @DisplayName("Ajouter une puce « Statut » et choisir VERIFIE filtre le tableau sur ce critère")
    void filtre_statut_via_la_barre_a_puces(FxRobot robot) {
        MenuButton menuAjout = robot.lookup("#menuAjoutFiltre").queryAs(MenuButton.class);
        MenuItem itemStatut = menuAjout.getItems().stream()
                .filter(i -> "Statut".equals(i.getText()))
                .findFirst()
                .orElseThrow();
        robot.interact(itemStatut::fire);
        WaitForAsyncUtils.waitForFxEvents();

        FlowPane puces = robot.lookup("#pucesFiltres").queryAs(FlowPane.class);
        @SuppressWarnings("unchecked")
        ComboBox<StatutWorkflow> choix = (ComboBox<StatutWorkflow>)
                robot.from(puces).lookup(".combo-box").queryAs(ComboBox.class);
        robot.interact(() -> choix.setValue(StatutWorkflow.VERIFIE));
        WaitForAsyncUtils.waitForFxEvents();

        @SuppressWarnings("unchecked")
        TableView<LignePassage> table = (TableView<LignePassage>)
                (TableView<?>) robot.lookup("#tableLignes").queryTableView();
        assertThat(table.getItems())
                .as("seule la ligne au statut VERIFIE reste (filtrage en mémoire)")
                .extracting(LignePassage::statut)
                .containsOnly(StatutWorkflow.VERIFIE);
    }

    @Test
    @DisplayName("La vue par défaut « Vérifiés » est un onglet qui filtre le tableau sur le statut Vérifié")
    void vue_par_defaut_verifies_filtre_le_tableau(FxRobot robot) {
        FlowPane onglets = robot.lookup("#barreOnglets").queryAs(FlowPane.class);
        // Les 4 onglets par défaut sont rendus (avant les vues de l'utilisateur).
        assertThat(robot.from(onglets).lookup(".onglet-vue-nom").queryAllAs(Label.class))
                .extracting(Label::getText)
                .containsExactly("Tout", "Déposés", "À vérifier", "Vérifiés");

        Label verifies = robot.from(onglets).lookup(".onglet-vue-nom").queryAllAs(Label.class).stream()
                .filter(label -> "Vérifiés".equals(label.getText()))
                .findFirst()
                .orElseThrow();
        robot.interact(() -> verifies.getOnMouseClicked().handle(null));
        WaitForAsyncUtils.waitForFxEvents();

        @SuppressWarnings("unchecked")
        TableView<LignePassage> table = (TableView<LignePassage>)
                (TableView<?>) robot.lookup("#tableLignes").queryTableView();
        assertThat(table.getItems())
                .as("la vue « Vérifiés » ne laisse que les passages au statut Vérifié")
                .extracting(LignePassage::statut)
                .containsOnly(StatutWorkflow.VERIFIE);
    }

    @Test
    @DisplayName("L'export (item du menu ☰) est actif dès qu'il y a des passages à exporter")
    void export_actif_quand_non_vide(FxRobot robot) {
        // #370 : Exporter est un item du menu « ☰ ». On inspecte son état désactivé directement.
        MenuButton menu = robot.lookup("#menuActions").queryAs(MenuButton.class);
        MenuItem itemExporter = menu.getItems().stream()
                .filter(item -> "itemExporter".equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(itemExporter.isDisable()).isFalse();
    }

    @Test
    @DisplayName("Réinitialiser vide la recherche et réaffiche tous les passages")
    void reinitialiser_vide_la_recherche(FxRobot robot) {
        TextField recherche = robot.lookup("#champRecherche").queryAs(TextField.class);
        robot.clickOn("#champRecherche").write("640380");
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(recherche.getText()).isEqualTo("640380");

        robot.clickOn("#boutonReinitialiser");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(recherche.getText()).isEmpty();
        @SuppressWarnings("unchecked")
        TableView<LignePassage> table = (TableView<LignePassage>)
                (TableView<?>) robot.lookup("#tableLignes").queryTableView();
        assertThat(table.getItems())
                .as("tout est réaffiché après réinitialisation")
                .hasSize(2);
    }

    @Test
    @DisplayName("Double-cliquer une ligne ouvre M-Passage via le contrat socle OuvrirPassage")
    void double_clic_ouvre_le_passage(FxRobot robot) {
        robot.doubleClickOn("2026-06-21"); // date unique de la ligne idPassage = 42

        verify(ouvrirPassage).ouvrir(eq(42L), any(ContexteSite.class));
    }

    @Test
    @DisplayName("#154 : « Éditer les positions » montre le bouton Enregistrer (inactif), activé par un déplacement")
    void mode_edition_montre_et_active_enregistrer(FxRobot robot) {
        ToggleButton editer = robot.lookup("#boutonEditerPositions").queryAs(ToggleButton.class);
        Button enregistrer = robot.lookup("#boutonEnregistrerPositions").queryAs(Button.class);
        assertThat(enregistrer.isVisible()).as("masqué hors édition").isFalse();

        robot.interact(editer::fire); // entrer en édition
        assertThat(enregistrer.isVisible()).isTrue();
        assertThat(enregistrer.isDisabled()).as("rien à enregistrer au départ").isTrue();

        // Un déplacement en attente (simulé via le ViewModel) active le bouton.
        robot.interact(() -> viewModel.positionsEnAttente().deplacer(1L, 43.40, -1.57));
        assertThat(enregistrer.isDisabled())
                .as("un déplacement en attente active Enregistrer")
                .isFalse();

        robot.interact(enregistrer::fire); // enregistrer → persistance via ServiceSites
        verify(serviceSites).deplacerPoint(1L, 43.40, -1.57);
        assertThat(enregistrer.isDisabled())
                .as("plus rien en attente après enregistrement")
                .isTrue();
    }

    @Test
    @DisplayName("« Écouter le lot filtré » ouvre la vue audio sur tous les passages affichés (ParPassages)")
    void ecouter_le_lot_ouvre_par_passages(FxRobot robot) {
        // Les MenuItem ne sont pas des Node : on passe par le MenuButton (ordre du FXML : passage, lot, …).
        MenuItem itemLot = robot.lookup("#menuActions")
                .queryAs(MenuButton.class)
                .getItems()
                .get(1);

        robot.interact(itemLot::fire);

        ArgumentCaptor<SourceObservations> source = ArgumentCaptor.forClass(SourceObservations.class);
        verify(ouvrirAudio).ouvrir(source.capture());
        assertThat(source.getValue())
                .isInstanceOfSatisfying(
                        SourceObservations.ParPassages.class,
                        lot -> assertThat(lot.idPassages()).containsExactlyInAnyOrder(42L, 7L));
    }

    @Test
    @DisplayName("« Écouter le passage sélectionné » ouvre la vue audio sur ce passage (ParPassage)")
    void ecouter_le_passage_ouvre_par_passage(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableLignes").queryAs(TableView.class);
        MenuItem itemPassage = robot.lookup("#menuActions")
                .queryAs(MenuButton.class)
                .getItems()
                .get(0);

        robot.interact(() -> table.getSelectionModel().select(0)); // ligne 42L / carré 640380 / point A1
        robot.interact(itemPassage::fire);

        ArgumentCaptor<SourceObservations> source = ArgumentCaptor.forClass(SourceObservations.class);
        verify(ouvrirAudio).ouvrir(source.capture());
        assertThat(source.getValue()).isInstanceOfSatisfying(SourceObservations.ParPassage.class, passage -> {
            assertThat(passage.contexte().idPassage()).isEqualTo(42L);
            assertThat(passage.contexte().site().numeroCarre()).isEqualTo("640380");
            assertThat(passage.contexte().site().codePoint()).isEqualTo("A1");
        });
    }
}

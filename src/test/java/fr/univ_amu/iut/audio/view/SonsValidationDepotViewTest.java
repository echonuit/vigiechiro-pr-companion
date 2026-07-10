package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import fr.univ_amu.iut.validation.model.MarquageDouteux;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
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

/// Glisser-déposer d'un CSV Tadarida sur la vue audio ouverte sur un **passage** (workflow) : le dépôt
/// d'un fichier délègue à l'import du ViewModel (alternative au `FileChooser`) et le bandeau de retour
/// confirme le succès. Services mockés, pas de base de données.
@ExtendWith(ApplicationExtension.class)
class SonsValidationDepotViewTest {

    private ServiceValidation service;
    private SonsValidationController controleur;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceValidation.class);
        ServiceBibliotheque bibliotheque = mock(ServiceBibliotheque.class);
        when(service.taxonsDisponibles()).thenReturn(List.of());
        // Passage sans résultats à l'ouverture (import permis).
        when(service.resultatsDuPassage(7L)).thenReturn(Optional.empty());
        when(service.lignesAudioDuPassage(7L)).thenReturn(List.of());
        // L'import déposé renvoie un bilan (1 observation importée).
        when(service.importer(7L, Path.of("obs.csv")))
                .thenReturn(new BilanImport(
                        new ResultatsIdentification(100L, "obs.csv", "Brut", "2026-06-30T00:00", 7L), 1, 0, 0));

        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    AudioViewModel viewModel() {
                        return new AudioViewModel(
                                service,
                                mock(ValidationManuelle.class),
                                mock(MarquageDouteux.class),
                                mock(RevueEnLot.class),
                                bibliotheque);
                    }

                    @Provides
                    DepotVues depotVues() {
                        return mock(DepotVues.class); // findByFeature -> liste vide par défaut (Mockito)
                    }

                    // Import VigieChiro **disponible** (mock) : l'item « Importer depuis VigieChiro » apparaît
                    // dans le menu ☰ pour cette source ParPassage (workflow Tadarida).
                    @Provides
                    ImportVigieChiroViewModel importVigieChiro() {
                        return new ImportVigieChiroViewModel(Optional.of(mock(ImportVigieChiro.class)));
                    }

                    // « Fiche de l'espèce » (#847) : navigateur no-op, la fiche n'est pas exercée ici.
                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        return url -> {};
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(SonsValidationController.class.getResource("SonsValidation.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.ouvrirSur(new SourceObservations.ParPassage(
                new ContextePassage(7L, 1, new ContexteSite("640380", "A1", "Site"))));
        stage.setScene(new Scene(vue, 1000, 700));
        stage.show();
    }

    @Test
    @DisplayName("Déposer un CSV sur un passage déclenche l'import et affiche le bandeau de succès")
    void depot_csv_declenche_import(FxRobot robot) {
        Node bandeau = robot.lookup("#bandeauRetour").query();
        Label message = robot.lookup("#lblMessage").queryAs(Label.class);

        robot.interact(() -> assertThat(controleur.deposerFichiers(List.of(new File("obs.csv"))))
                .isTrue());

        verify(service).importer(7L, Path.of("obs.csv"));
        assertThat(bandeau.isVisible()).isTrue();
        assertThat(message.getText()).contains("Import réussi");
        assertThat(bandeau.getStyleClass()).contains("retour-succes");
    }

    @Test
    @DisplayName("#719 : l'item « Importer depuis VigieChiro » est présent et visible (passage, connecté)")
    void item_import_vigiechiro_present(FxRobot robot) {
        MenuButton menu = robot.lookup("#menuActions").queryAs(MenuButton.class);
        MenuItem item = menu.getItems().stream()
                .filter(i -> "itemImporterVigieChiro".equals(i.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(item.isVisible()).isTrue();
        assertThat(item.getText()).contains("VigieChiro");
    }
}

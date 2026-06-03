package fr.univ_amu.iut.bibliotheque.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.bibliotheque.model.EntreeBiblio;
import fr.univ_amu.iut.bibliotheque.model.ExportBiblioSons;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.bibliotheque.viewmodel.BibliothequeViewModel;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de l'écran **M-Bibliotheque** : chargement du FXML via Guice (avec un
/// [ServiceBibliotheque] mocké), auto-chargement de la table en `initialize()`, vérification du
/// câblage (table peuplée, résumé, sélection qui alimente détail + écoute, export actif). Pas de
/// base de données.
@ExtendWith(ApplicationExtension.class)
class BibliothequeViewTest {

    private ServiceBibliotheque service;

    private static EntreeBiblio entree(String taxon, String chemin, String commentaire) {
        return new EntreeBiblio(taxon, "seq.wav", chemin, 45000, commentaire);
    }

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceBibliotheque.class);
        when(service.exporterBibliotheque())
                .thenReturn(new ExportBiblioSons(
                        List.of(entree("NYCNOC", "/ws/n.wav", "beau cri grave"), entree("PIPPIP", "/ws/p.wav", null))));
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            BibliothequeViewModel viewModel() {
                return new BibliothequeViewModel(service);
            }
        });
        FXMLLoader loader = new FXMLLoader(BibliothequeController.class.getResource("Bibliotheque.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        stage.setScene(new Scene(vue, 900, 640));
        stage.show();
    }

    @Test
    @DisplayName("La table liste les sons de référence ; le résumé les compte")
    void affiche_table_et_resume(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableEntrees").queryAs(TableView.class);
        Label resume = robot.lookup("#lblResume").queryAs(Label.class);

        assertThat(table.getItems()).hasSize(2);
        assertThat(resume.getText()).isEqualTo("2 son(s) de référence.");
    }

    @Test
    @DisplayName("Sélectionner un son alimente le détail (commentaire) et l'écoute (AudioView)")
    void selection_alimente_detail_et_audio(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableEntrees").queryAs(TableView.class);
        AudioView audio = robot.lookup("#audioView").queryAs(AudioView.class);
        Label detail = robot.lookup("#lblDetail").queryAs(Label.class);

        assertThat(audio.getAudioFile()).isNull();
        robot.interact(() -> table.getSelectionModel().select(0));
        assertThat(audio.getAudioFile().toString()).endsWith("n.wav");
        assertThat(detail.getText()).contains("beau cri grave");

        // Un son sans commentaire affiche l'invite, pas une chaîne vide.
        robot.interact(() -> table.getSelectionModel().select(1));
        assertThat(detail.getText()).contains("Aucun commentaire");
    }

    @Test
    @DisplayName("L'export est actif dès que la bibliothèque contient des sons")
    void export_actif_quand_non_vide(FxRobot robot) {
        Button btnExporter = robot.lookup("#btnExporter").queryAs(Button.class);
        assertThat(btnExporter.isDisabled()).isFalse();
    }
}

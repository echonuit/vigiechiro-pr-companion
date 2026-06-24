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
import java.nio.file.Path;
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
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de l'écran **M-Bibliotheque** centré sur le **lookup réel des `fx:id`**
/// (pas seulement la lecture des propriétés du [BibliothequeViewModel]).
///
/// Là où [BibliothequeViewTest] vérifie surtout le câblage côté propriétés, cette suite va chercher
/// les contrôles par `robot.lookup("#fx:id")`, confronte leur état à celui attendu du ViewModel, et
/// déclenche de **vraies interactions** (sélection de ligne, rechargement de la bibliothèque, export)
/// pour valider l'effet à l'écran. Objectif : qu'un FXML resté à l'état de placeholder, des colonnes
/// non câblées ou un export muet **échouent** (ces manques restent invisibles tant qu'on ne lit que
/// le VM). Chargement du FXML via Guice avec un [ServiceBibliotheque] mocké, sans base de données.
@ExtendWith(ApplicationExtension.class)
class BibliothequeVueIntegrationTest {

    private ServiceBibliotheque service;

    /// ViewModel réellement injecté dans le controller, capturé à la construction pour piloter les
    /// interactions (rechargement, export) depuis les tests.
    private BibliothequeViewModel viewModel;

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
                viewModel = new BibliothequeViewModel(service);
                return viewModel;
            }
        });
        FXMLLoader loader = new FXMLLoader(BibliothequeController.class.getResource("Bibliotheque.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        stage.setScene(new Scene(vue, 900, 640));
        stage.show();
    }

    @Test
    @DisplayName("Lookup : tous les contrôles fx:id de l'écran sont présents dans la scène")
    void tous_les_controles_fxid_sont_presents(FxRobot robot) {
        // Un écran resté à l'état de placeholder (FXML non construit) ferait échouer ces lookups.
        assertThat(robot.lookup("#tableEntrees").queryAs(TableView.class)).isNotNull();
        assertThat(robot.lookup("#lblResume").queryAs(Label.class)).isNotNull();
        assertThat(robot.lookup("#lblDetail").queryAs(Label.class)).isNotNull();
        assertThat(robot.lookup("#lblMessage").queryAs(Label.class)).isNotNull();
        assertThat(robot.lookup("#btnExporter").queryAs(Button.class)).isNotNull();
        assertThat(robot.lookup("#audioView").queryAs(AudioView.class)).isNotNull();
    }

    @Test
    @DisplayName("La table est peuplée et ses 3 colonnes sont câblées aux champs de l'entrée")
    void table_peuplee_et_colonnes_cablees(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableEntrees").queryAs(TableView.class);

        // Une cellValueFactory absente (colonnes non câblées) rendrait getCellData null malgré une
        // table peuplée : on vérifie donc le contenu réel des cellules de la première ligne.
        assertThat(table.getItems()).hasSize(2);
        var colonnes = table.getColumns();
        assertThat(colonnes).hasSize(3);
        assertThat(colonnes.get(0).getCellData(0)).isEqualTo("NYCNOC");
        assertThat(colonnes.get(1).getCellData(0)).isEqualTo("seq.wav");
        assertThat(colonnes.get(2).getCellData(0)).isEqualTo("45000");
    }

    @Test
    @DisplayName("Le résumé affiché reflète le décompte du ViewModel au format attendu")
    void resume_reflete_le_vm(FxRobot robot) {
        Label resume = robot.lookup("#lblResume").queryAs(Label.class);
        // Format exact de la spec (« N son(s) de référence. ») : un résumé vide (charger() no-op) ou
        // mal formaté (« N son ») échouerait ici.
        assertThat(resume.getText()).isEqualTo("2 son(s) de référence.");
    }

    @Test
    @DisplayName("Interaction : sélectionner puis désélectionner une ligne pilote détail + écoute")
    void selection_alimente_puis_vide_detail_et_audio(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableEntrees").queryAs(TableView.class);
        AudioView audio = robot.lookup("#audioView").queryAs(AudioView.class);
        Label detail = robot.lookup("#lblDetail").queryAs(Label.class);

        assertThat(audio.getAudioFile()).isNull();

        robot.interact(() -> table.getSelectionModel().select(0));
        assertThat(audio.getAudioFile().toString()).endsWith("n.wav");
        assertThat(detail.getText()).contains("beau cri grave");

        // Désélectionner remet l'écoute à null et vide le détail (état neutre cohérent).
        robot.interact(() -> table.getSelectionModel().clearSelection());
        assertThat(audio.getAudioFile()).isNull();
        assertThat(detail.getText()).isEmpty();
    }

    @Test
    @DisplayName("Interaction : recharger une bibliothèque remplace les entrées (setAll, pas addAll)")
    void rechargement_remplace_les_entrees(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableEntrees").queryAs(TableView.class);
        Label resume = robot.lookup("#lblResume").queryAs(Label.class);
        assertThat(table.getItems()).hasSize(2);

        // Le service renvoie désormais une seule entrée : un rechargement par addAll laisserait 3
        // lignes (doublons / entrées obsolètes), un setAll en laisse exactement 1.
        when(service.exporterBibliotheque())
                .thenReturn(new ExportBiblioSons(List.of(entree("BARBAR", "/ws/b.wav", "seule"))));
        robot.interact(() -> viewModel.charger());

        assertThat(table.getItems()).hasSize(1);
        assertThat(table.getColumns().get(0).getCellData(0)).isEqualTo("BARBAR");
        assertThat(resume.getText()).isEqualTo("1 son(s) de référence.");
    }

    @Test
    @DisplayName("Interaction : recharger une bibliothèque vide désactive l'export et l'annonce")
    void rechargement_vide_desactive_export(FxRobot robot) {
        Button btnExporter = robot.lookup("#btnExporter").queryAs(Button.class);
        Label resume = robot.lookup("#lblResume").queryAs(Label.class);
        TableView<?> table = robot.lookup("#tableEntrees").queryAs(TableView.class);
        assertThat(btnExporter.isDisabled()).isFalse();

        when(service.exporterBibliotheque()).thenReturn(new ExportBiblioSons(List.of()));
        robot.interact(() -> viewModel.charger());

        // Branche « vide » du binding disableProperty <- biblioNonVide.not(), peu couverte ailleurs.
        assertThat(btnExporter.isDisabled()).isTrue();
        assertThat(table.getItems()).isEmpty();
        assertThat(resume.getText()).contains("Aucun son de référence");
    }

    @Test
    @DisplayName("Interaction : l'export délègue au ViewModel et publie un message dans #lblMessage")
    void export_publie_un_message_dans_le_label(FxRobot robot, @TempDir Path dossierExport) {
        Label message = robot.lookup("#lblMessage").queryAs(Label.class);
        // Au départ aucun message : le label est masqué (visibleProperty/managedProperty liés à la
        // présence d'un message).
        assertThat(message.getText()).isEmpty();
        assertThat(message.isVisible()).isFalse();

        // Le handler #exporter ouvre un DirectoryChooser natif (non pilotable headless) : on appelle
        // donc directement viewModel.exporter(dossier), exactement ce que fait le bouton une fois le
        // dossier choisi, et on vérifie le retour visible côté vue.
        robot.interact(() -> viewModel.exporter(dossierExport));

        assertThat(message.getText()).contains("Bibliothèque exportée");
        assertThat(message.isVisible()).isTrue();
        assertThat(message.isManaged()).isTrue();
    }
}

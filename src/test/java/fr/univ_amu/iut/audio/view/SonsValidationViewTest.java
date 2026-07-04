package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.PickResult;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de la **vue audio unifiée** (`SonsValidation.fxml`) ouverte sur la source
/// `References` : chargement du FXML via Guice (services mockés), câblage table / sélection / détail /
/// écoute, bascule de référence, et adaptation à la source (menu « Exporter la bibliothèque » visible,
/// actions de passage masquées ; colonnes de contexte visibles car la source n'est pas un seul passage).
/// Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class SonsValidationViewTest {

    private ServiceValidation service;

    private static LigneObservationAudio ligne(
            long id, long seq, String tadarida, String observateur, String nomEspece, String nomTadarida) {
        return new LigneObservationAudio(
                id,
                seq,
                7L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Mon site",
                tadarida,
                0.9,
                observateur,
                0.95,
                StatutObservation.VALIDEE,
                true,
                "beau cri",
                45000,
                nomEspece,
                nomTadarida,
                "PaRec_" + seq + "_000.wav");
    }

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceValidation.class);
        ServiceBibliotheque bibliotheque = mock(ServiceBibliotheque.class);
        when(service.taxonsDisponibles())
                .thenReturn(List.of(new Taxon("Nyclei", "Nyctalus leisleri", "Noctule de Leisler", 1L)));
        when(service.lignesAudioReferences("u-1"))
                .thenReturn(List.of(
                        ligne(1, 10, "Pippip", "Pippip", "Pipistrelle commune", "Pipistrelle commune"),
                        ligne(2, 11, "Nyclei", "Nyclei", "Noctule de Leisler", "Noctule de Leisler")));
        when(service.cheminAudio(anyLong())).thenReturn(Optional.empty());
        when(service.cheminAudio(10L)).thenReturn(Optional.of(Path.of("/ws/transformes/p.wav")));

        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    AudioViewModel viewModel() {
                        return new AudioViewModel(service, bibliotheque);
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(SonsValidationController.class.getResource("SonsValidation.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        SonsValidationController controleur = loader.getController();
        controleur.ouvrirSur(new SourceObservations.References("u-1"));
        stage.setScene(new Scene(vue, 1000, 700));
        stage.show();
    }

    @Test
    @DisplayName("La table liste les références ; le résumé les compte")
    void affiche_table_et_resume(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        Label resume = robot.lookup("#lblResume").queryAs(Label.class);

        assertThat(table.getItems()).hasSize(2);
        // Résumé : libellé de source + total + avancement de la revue (les 2 lignes sont VALIDEE).
        assertThat(resume.getText())
                .contains("Sons de référence")
                .contains("2 observation(s)")
                .contains("2 / 2 revues");
    }

    @Test
    @DisplayName("Proposition Tadarida et Votre taxon affichent le vernaculaire ; Proba la probabilité")
    void affiche_nom_vernaculaire_et_proba(FxRobot robot) {
        // Les deux lignes sont revues (taxon observateur renseigné) : « Votre taxon » montre le
        // vernaculaire retenu, comme « Proposition Tadarida ». Plus de colonne « Espèce » redondante.
        assertThat(colonne(robot, "Proposition Tadarida").getCellData(0)).isEqualTo("Pipistrelle commune");
        assertThat(colonne(robot, "Votre taxon").getCellData(0)).isEqualTo("Pipistrelle commune");
        assertThat(colonne(robot, "Votre taxon").getCellData(1)).isEqualTo("Noctule de Leisler");
        // Proba = probabilité Tadarida formatée (0.9 → « 90 % »).
        assertThat(colonne(robot, "Proba.").getCellData(0)).isEqualTo("90 %");
    }

    @Test
    @DisplayName("La colonne « Fichier » affiche le nom de fichier de la séquence")
    void affiche_le_nom_de_fichier(FxRobot robot) {
        assertThat(colonne(robot, "Fichier").getCellData(0)).isEqualTo("PaRec_10_000.wav");
        assertThat(colonne(robot, "Fichier").getCellData(1)).isEqualTo("PaRec_11_000.wav");
    }

    @Test
    @DisplayName("Colonnes Date, Fréquence et indicateur de commentaire")
    void affiche_date_frequence_commentaire(FxRobot robot) {
        assertThat(colonne(robot, "Date").getCellData(0)).isEqualTo("2026-06-20");
        assertThat(colonne(robot, "Fréquence").getCellData(0)).isEqualTo("45000 Hz");
        // Les deux lignes de la fixture portent un commentaire (« beau cri ») → icône 💬.
        assertThat(colonne(robot, "💬").getCellData(0)).isEqualTo("💬");
    }

    @Test
    @DisplayName("Les colonnes sont triables : trier « Fichier » en décroissant réordonne les lignes")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void colonnes_triables(FxRobot robot) {
        TableView table = robot.lookup("#tableObservations").queryAs(TableView.class);
        TableColumn fichier = colonne(robot, "Fichier");

        // Sans l'enveloppe SortedList, trier une table alimentée par une FilteredList ne réordonne rien.
        robot.interact(() -> {
            table.getSortOrder().setAll(fichier);
            fichier.setSortType(TableColumn.SortType.DESCENDING);
            table.sort();
        });

        assertThat(colonne(robot, "Fichier").getCellData(0)).isEqualTo("PaRec_11_000.wav");
        assertThat(colonne(robot, "Fichier").getCellData(1)).isEqualTo("PaRec_10_000.wav");
    }

    @Test
    @DisplayName("Après un Valider, la ligne revue reste surlignée (sélection re-synchronisée VM → table)")
    void selection_reste_surlignee_apres_action(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        Button valider = robot.lookup("#btnValider").queryAs(Button.class);

        // Le rechargement post-action renvoie la même observation (id=1) mais **modifiée** (nouvelle
        // instance de record, non égale à l'ancienne) : sans resync, la table perdrait sa surbrillance.
        when(service.lignesAudioReferences("u-1"))
                .thenReturn(List.of(
                        ligne(1, 10, "Pippip", "Nyclei", "Noctule de Leisler", "Pipistrelle commune"),
                        ligne(2, 11, "Nyclei", "Nyclei", "Noctule de Leisler", "Noctule de Leisler")));

        robot.interact(() -> table.getSelectionModel().select(0)); // observation id=1
        robot.interact(valider::fire);

        Object surlignee = table.getSelectionModel().getSelectedItem();
        assertThat(surlignee).isNotNull();
        assertThat(((LigneObservationAudio) surlignee).idObservation()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Sélectionner une ligne alimente le détail et l'écoute (AudioView)")
    void selection_alimente_detail_et_audio(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        AudioView audio = robot.lookup("#audioView").queryAs(AudioView.class);
        Label detail = robot.lookup("#lblDetail").queryAs(Label.class);

        assertThat(audio.getAudioFile()).isNull();
        assertThat(audio.isNormalisation()).isTrue();
        robot.interact(() -> table.getSelectionModel().select(0));
        assertThat(audio.getAudioFile().toString()).endsWith("p.wav");
        assertThat(detail.getText()).contains("Tadarida : Pippip").contains("Référence : oui");
    }

    @Test
    @DisplayName("Le bouton de référence bascule l'archivage de l'observation sélectionnée")
    void basculer_reference(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        Button btnReference = robot.lookup("#btnReference").queryAs(Button.class);

        assertThat(btnReference.isDisabled()).isTrue(); // pas de sélection au départ
        robot.interact(() -> table.getSelectionModel().select(0));
        assertThat(btnReference.getText()).contains("Retirer la référence"); // la ligne est déjà référence

        robot.interact(btnReference::fire);
        verify(service).marquerReference(1L, false);
    }

    @Test
    @DisplayName("Un échec s'affiche dans le bandeau de retour (erreur) et la croix le ferme")
    void echec_affiche_puis_ferme_le_bandeau(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        Node bandeau = robot.lookup("#bandeauRetour").query();
        Label message = robot.lookup("#lblMessage").queryAs(Label.class);

        assertThat(bandeau.isVisible()).as("aucun retour au départ").isFalse();

        Button btnReference = robot.lookup("#btnReference").queryAs(Button.class);
        robot.interact(() -> table.getSelectionModel().select(0)); // ligne id=1, déjà référence
        doThrow(new RegleMetierException("Échec simulé")).when(service).marquerReference(1L, false);
        robot.interact(btnReference::fire);

        // Le retour d'erreur est visible et stylé erreur, indépendamment du placeholder d'état vide.
        assertThat(bandeau.isVisible()).isTrue();
        assertThat(message.getText()).contains("Échec simulé");
        assertThat(bandeau.getStyleClass()).contains("retour-erreur");

        // La croix ferme le bandeau (l'utilisateur l'a lu).
        Button btnFermer = robot.lookup("#btnFermerRetour").queryAs(Button.class);
        robot.interact(btnFermer::fire);
        assertThat(bandeau.isVisible())
                .as("le bandeau est masqué après fermeture")
                .isFalse();
    }

    @Test
    @DisplayName("Sélecteur de colonnes : menu contextuel (clic droit) + sous-menu ☰ masquent une colonne")
    void selecteur_colonnes_masque_une_colonne(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        assertThat(table.getContextMenu())
                .as("menu contextuel installé (clic droit)")
                .isNotNull();

        MenuButton menu = robot.lookup("#menuActions").queryAs(MenuButton.class);
        Menu colonnes = (Menu) menu.getItems().stream()
                .filter(item -> "Colonnes".equals(item.getText()))
                .findFirst()
                .orElseThrow();
        CheckMenuItem caseFichier = (CheckMenuItem) colonnes.getItems().stream()
                .filter(item -> "Fichier".equals(item.getText()))
                .findFirst()
                .orElseThrow();

        assertThat(colonne(robot, "Fichier").isVisible()).isTrue();
        robot.interact(() -> caseFichier.setSelected(false));
        assertThat(colonne(robot, "Fichier").isVisible())
                .as("décocher la case masque la colonne (liaison bidirectionnelle)")
                .isFalse();
    }

    @Test
    @DisplayName("Source References : menu « Exporter la bibliothèque » visible, actions passage masquées")
    void menu_adapte_a_la_source(FxRobot robot) {
        // Les MenuItem ne sont pas des Node : on passe par le MenuButton et ses items (ordre du FXML :
        // importer, inclure le mode, exporter _Vu, exporter bibliothèque).
        MenuButton menu = robot.lookup("#menuActions").queryAs(MenuButton.class);
        MenuItem importer = menu.getItems().get(0);
        MenuItem inclureMode = menu.getItems().get(1);
        MenuItem exporterVu = menu.getItems().get(2);
        MenuItem exporterBiblio = menu.getItems().get(3);

        assertThat(menu.isVisible()).isTrue();
        assertThat(exporterBiblio.isVisible()).isTrue();
        assertThat(importer.isVisible()).isFalse();
        assertThat(inclureMode.isVisible()).isFalse();
        assertThat(exporterVu.isVisible()).isFalse();
        // Source multi-passages : les colonnes de contexte restent visibles.
        assertThat(colonne(robot, "Passage").isVisible()).isTrue();
    }

    @Test
    @DisplayName("Source References : déposer un CSV est refusé (le dépôt n'est actif qu'en workflow passage)")
    void depot_refuse_hors_workflow(FxRobot robot) {
        // Câblage réel du controller : sur la source References (non workflow), le prédicat d'activation
        // du glisser-déposer est faux → un fichier déposé est refusé et n'enclenche aucun import.
        Region racine = robot.lookup("#racine").queryAs(Region.class);
        Dragboard presse = mock(Dragboard.class);
        when(presse.hasFiles()).thenReturn(true);
        when(presse.getFiles()).thenReturn(List.of(new File("obs.csv")));
        DragEvent drop = new DragEvent(
                null,
                racine,
                DragEvent.DRAG_DROPPED,
                presse,
                0,
                0,
                0,
                0,
                TransferMode.COPY,
                null,
                null,
                new PickResult(racine, 0, 0));

        robot.interact(() -> Event.fireEvent(racine, drop));

        assertThat(drop.isDropCompleted()).isFalse();
        verify(service, never()).importer(any(), any());
    }

    private static TableColumn<?, ?> colonne(FxRobot robot, String entete) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        return table.getColumns().stream()
                .filter(c -> entete.equals(c.getText()))
                .findFirst()
                .orElseThrow();
    }
}

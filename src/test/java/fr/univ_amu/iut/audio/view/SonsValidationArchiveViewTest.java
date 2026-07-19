package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.DiscussionValidateur;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.passage.model.DecompteAudio;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MarquageDouteux;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.SaisieCertitude;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Mode **archivé / audio partiel** de la vue audio unifiée (#1301) : le passage de la fixture est
/// en disponibilité `PARTIELLE` (1 séquence sur 2 encore sur disque, simulée par un prédicat de
/// présence). Le bandeau porte le décompte, l'écoute marche là où le fichier existe et l'encart
/// d'explication remplace le lecteur ailleurs, tandis que les actions sur les **données** (marquer
/// douteux…) restent actives : consulter sans écouter.
@ExtendWith(ApplicationExtension.class)
class SonsValidationArchiveViewTest {

    private static final ContextePassage PASSAGE_7 =
            new ContextePassage(7L, 1, new ContexteSite("640380", "A1", "Mon site"));
    private static final Path PRESENT = Path.of("/ws/transformes/present.wav");
    private static final Path ABSENT = Path.of("/ws/transformes/absent.wav");

    @TempDir
    Path dossierReglages;

    private SonsValidationController controleur;

    /// URLs de fiche réellement ouvertes dans le navigateur, pour distinguer « rien ne s'est ouvert »
    /// de « la fiche s'est ouverte ».
    private final List<String> urlsOuvertes = new ArrayList<>();

    private static LigneObservationAudio ligne(long id, long seq) {
        return ligne(id, seq, "Pippip", null);
    }

    /// Variante nommant le taxon Tadarida et son libellé, pour couvrir un **pseudo-taxon** sans fiche
    /// (« noise » → « Bruit ») à côté des chiroptères.
    private static LigneObservationAudio ligne(long id, long seq, String tadarida, String nomTadarida) {
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
                null,
                null,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                45,
                null,
                nomTadarida,
                null,
                "Chiroptères",
                "PaRec_" + seq + "_000.wav",
                0.20,
                0.32,
                null,
                false,
                null,
                null,
                null,
                null,
                0);
    }

    @Start
    void start(Stage stage) throws Exception {
        ServiceValidation service = mock(ServiceValidation.class);
        ProjectionsAudioDao projections = mock(ProjectionsAudioDao.class);
        PlageNuitPassage plageNuit = mock(PlageNuitPassage.class);
        ServiceDisponibiliteAudio disponibilite = mock(ServiceDisponibiliteAudio.class);
        when(service.taxonsDisponibles()).thenReturn(List.of());
        when(service.resultatsDuPassage(7L)).thenReturn(Optional.of(100L));
        // 3e ligne : pseudo-taxon « noise », sans fiche espèce (cas majoritaire d'une vraie nuit).
        when(projections.lignesAudioDuPassage(7L))
                .thenReturn(List.of(ligne(1, 10), ligne(2, 11), ligne(3, 12, "noise", "Bruit")));
        when(projections.lignesAudioNonIdentifiees(7L)).thenReturn(List.of());
        when(plageNuit.pour(7L)).thenReturn(Optional.empty());
        when(service.cheminAudio(anyLong())).thenReturn(Optional.empty());
        when(service.cheminAudio(10L)).thenReturn(Optional.of(PRESENT));
        when(service.cheminAudio(11L)).thenReturn(Optional.of(ABSENT));
        // Disponibilité PARTIELLE ré-observée à l'ouverture : 1 séquence sur 2 encore sur disque.
        when(disponibilite.decompte(7L)).thenReturn(new DecompteAudio(1, 2));
        DepotVues depotVues = mock(DepotVues.class);
        when(depotVues.findByFeature("audio")).thenReturn(List.of());

        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    AudioViewModel viewModel() {
                        return new AudioViewModel(
                                service,
                                projections,
                                plageNuit,
                                mock(ValidationManuelle.class),
                                mock(MarquageDouteux.class),
                                mock(SaisieCertitude.class),
                                mock(RevueEnLot.class),
                                mock(ServiceBibliotheque.class),
                                disponibilite,
                                PRESENT::equals,
                                mock(DiscussionValidateur.class));
                    }

                    @Provides
                    DepotVues depotVues() {
                        return depotVues;
                    }

                    @Provides
                    ImportVigieChiroViewModel importVigieChiro() {
                        return new ImportVigieChiroViewModel(Optional.empty());
                    }

                    @Provides
                    PublicationCorrectionsViewModel publicationCorrections() {
                        return new PublicationCorrectionsViewModel(Optional.empty());
                    }

                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        return urlsOuvertes::add;
                    }

                    @Provides
                    PortailVigieChiro portail() {
                        return mock(PortailVigieChiro.class);
                    }

                    @Provides
                    ReglagesReactifs reglagesReactifs() {
                        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossierReglages));
                        new MigrationSchema(source).migrer();
                        return new ReglagesReactifs(new Reglages(new ReglagesDao(source)));
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(SonsValidationController.class.getResource("SonsValidation.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.ouvrirSur(new SourceObservations.ParPassage(PASSAGE_7));
        stage.setScene(new Scene(vue, 1000, 700));
        stage.show();
    }

    private void selectionner(FxRobot robot, int index) {
        robot.interact(() -> robot.lookup("#tableObservations")
                .queryAs(TableView.class)
                .getSelectionModel()
                .select(index));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("#1301 : le bandeau annonce l'audio partiel avec le décompte présentes/total")
    void bandeau_partiel_avec_decompte(FxRobot robot) {
        Label bandeau = robot.lookup("#lblBandeauArchive").queryAs(Label.class);

        assertThat(bandeau.isVisible()).isTrue();
        assertThat(bandeau.getText()).contains("1 séquence(s) sur 2");
    }

    @Test
    @DisplayName("#1301 : séquence présente : le lecteur est là, l'encart d'explication est masqué")
    void sequence_presente_lecteur_visible(FxRobot robot) {
        selectionner(robot, 0);

        assertThat(robot.lookup("#encartAudioManquant").queryAs(Node.class).isVisible())
                .isFalse();
        assertThat(robot.lookup("#audioView").queryAs(Node.class).isVisible()).isTrue();
    }

    @Test
    @DisplayName("#1301 : séquence sans fichier : l'encart explique à la place du lecteur, les données restent actives")
    void sequence_absente_encart_et_donnees_actives(FxRobot robot) {
        selectionner(robot, 1);

        assertThat(robot.lookup("#encartAudioManquant").queryAs(Node.class).isVisible())
                .isTrue();
        assertThat(robot.lookup("#audioView").queryAs(Node.class).isVisible()).isFalse();
        assertThat(robot.lookup("#btnDouteux").queryAs(Button.class).isDisabled())
                .as("marquer douteux est un acte sur des données, pas sur du son : reste possible")
                .isFalse();
        assertThat(robot.lookup("#btnValider").queryAs(Button.class).isDisabled())
                .as("valider reste un acte sur des données (possible sans écouter, comme aujourd'hui)")
                .isFalse();
    }

    /// Double-clic **déterministe** sur la ligne d'index `index` de `idTable`.
    ///
    /// Deux fragilités sont évitées ici. Les `TableRow` sont **recyclés** : leur ordre dans le graphe de
    /// scène ne suit pas celui des lignes affichées, donc la première `.table-row-cell` venue est souvent
    /// une ligne **vide**. Et en headless, `doubleClickOn(Node)` vise le **centre du nœud en coordonnées
    /// écran** : il rate sa cible quand la mise en page n'est pas encore stabilisée. On cible donc la ligne
    /// par son **index réel** et on lui envoie l'événement directement, ce qui exerce le même gestionnaire
    /// de production (`DoubleClicLigne`) sans dépendre du placement.
    private static void doubleCliquerLigne(FxRobot robot, String idTable, int index) {
        Node ligne = robot.lookup(idTable).lookup(".table-row-cell").queryAll().stream()
                .map(noeud -> (TableRow<?>) noeud)
                .filter(rangee -> !rangee.isEmpty() && rangee.getIndex() == index)
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune ligne d'index " + index + " dans " + idTable));
        robot.interact(() -> ligne.fireEvent(new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                2,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("#1834 : double-clic sur un taxon sans fiche : rien ne s'ouvre, et le bandeau dit pourquoi")
    void double_clic_sans_fiche_affiche_le_motif(FxRobot robot) {
        Node bandeau = robot.lookup("#bandeauRetour").query();
        assertThat(bandeau.isVisible()).as("aucun retour au départ").isFalse();

        doubleCliquerLigne(robot, "#tableObservations", 2); // « Bruit » : pseudo-taxon, aucune fiche

        assertThat(urlsOuvertes)
                .as("un pseudo-taxon n'a pas de fiche : rien ne doit s'ouvrir")
                .isEmpty();
        assertThat(bandeau.isVisible())
                .as("le geste ne doit plus rester muet : sans retour, il passe pour cassé")
                .isTrue();
        assertThat(robot.lookup("#lblRetour").queryAs(Label.class).getText())
                .as("le motif nomme le taxon tel que l'utilisateur le lit dans la table")
                .contains("Aucune fiche disponible pour « Bruit »");
        assertThat(bandeau.getStyleClass())
                .as("action refusée faute de cible : guidage, pas échec technique")
                .contains("retour-info");
    }

    @Test
    @DisplayName("#1834 : double-clic sur un chiroptère ouvre sa fiche, sans message de refus")
    void double_clic_avec_fiche_ouvre_et_ne_signale_rien(FxRobot robot) {
        doubleCliquerLigne(robot, "#tableObservations", 0); // « Pippip » : chiroptère à fiche PNA

        assertThat(urlsOuvertes)
                .containsExactly(
                        "https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/");
        assertThat(robot.lookup("#bandeauRetour").query().isVisible())
                .as("la fiche s'est ouverte : il n'y a rien à signaler")
                .isFalse();
    }

    private void doubleCliquerLigne(FxRobot robot, int index) {
        Node ligne = robot.lookup("#tableObservations")
                .lookup(".table-row-cell")
                .nth(index)
                .query();
        robot.doubleClickOn(ligne);
        WaitForAsyncUtils.waitForFxEvents();
    }
}

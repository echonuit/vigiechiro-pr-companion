package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.api.ReponseApi;
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
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import fr.univ_amu.iut.validation.model.MarquageDouteux;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.SaisieCertitude;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Glisser-déposer d'un CSV Tadarida sur la vue audio ouverte sur un **passage** (workflow) : le dépôt
/// d'un fichier délègue à l'import du ViewModel (alternative au `FileChooser`) et le bandeau de retour
/// confirme le succès. Services métier mockés ; seule une base `app_setting` jetable sert aux
/// préférences de lecture (#1006).
@ExtendWith(ApplicationExtension.class)
class SonsValidationDepotViewTest {

    /// La source ouverte par la fixture : le passage 7 du carré 640380, point A1.
    private static final SourceObservations PASSAGE =
            new SourceObservations.ParPassage(new ContextePassage(7L, 1, new ContexteSite("640380", "A1", "Site")));

    /// Ce que le confirmateur a **demandé** (le message du remplacement dit ce qu'on perd).
    private final List<String> confirmations = new ArrayList<>();

    /// Ce que le double de confirmation répondra : chaque test le pose avant de déposer.
    private boolean confirme = true;

    private ServiceValidation service;
    private ProjectionsAudioDao projections;
    private ImportVigieChiro importVigieChiro;
    private SonsValidationController controleur;

    /// Base jetable pour les seules préférences (`app_setting`) : les options de lecture du menu ☰ (#1006)
    /// passent par `ReglagesReactifs`. Le reste de la vue reste mocké.
    @TempDir
    Path dossierReglages;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceValidation.class);
        projections = mock(ProjectionsAudioDao.class);
        importVigieChiro = mock(ImportVigieChiro.class);
        ServiceBibliotheque bibliotheque = mock(ServiceBibliotheque.class);
        when(service.taxonsDisponibles()).thenReturn(List.of());
        // Passage sans résultats à l'ouverture (import permis).
        when(service.resultatsDuPassage(7L)).thenReturn(Optional.empty());
        when(projections.lignesAudioDuPassage(7L)).thenReturn(List.of());
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
                                projections,
                                mock(PlageNuitPassage.class),
                                mock(ValidationManuelle.class),
                                mock(MarquageDouteux.class),
                                mock(SaisieCertitude.class),
                                mock(RevueEnLot.class),
                                bibliotheque,
                                mock(ServiceDisponibiliteAudio.class),
                                p -> true);
                    }

                    @Provides
                    DepotVues depotVues() {
                        return mock(DepotVues.class); // findByFeature -> liste vide par défaut (Mockito)
                    }

                    // Import VigieChiro **disponible** (mock, champ du test pour piloter le flux #1255) :
                    // l'item « Importer depuis VigieChiro » apparaît dans le menu ☰ pour cette source
                    // ParPassage (workflow Tadarida).
                    @Provides
                    ImportVigieChiroViewModel importVigieChiroViewModel() {
                        return new ImportVigieChiroViewModel(Optional.of(importVigieChiro));
                    }

                    @Provides
                    fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel publicationCorrections() {
                        return new fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel(Optional.empty());
                    }

                    // « Fiche de l'espèce » (#847) : navigateur no-op, la fiche n'est pas exercée ici.
                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        return url -> {};
                    }

                    // « Ouvrir les données sur Vigie-Chiro » (#1124) : portail factice (aucun lien posé).
                    @Provides
                    PortailVigieChiro portail() {
                        return mock(PortailVigieChiro.class);
                    }

                    // Réglages réactifs (#1006) : les options de lecture du menu ☰ y sont liées. Base
                    // app_setting jetable et migrée, sinon la lecture du réglage échoue au chargement.
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
        // Le oui/non du remplacement, remplacé par un double (#1405) : un vrai dialogue figerait le test.
        controleur.confirmateur().definir(message -> {
            confirmations.add(message);
            return confirme;
        });
        controleur.ouvrirSur(PASSAGE);
        stage.setScene(new Scene(vue, 1000, 700));
        stage.show();
    }

    /// Rouvre l'écran sur un passage qui porte **déjà** un jeu de résultats : le prochain import devient un
    /// **remplacement**, donc un geste destructif (les validations en cours seront perdues).
    private void avecResultatsExistants(FxRobot robot) {
        when(service.resultatsDuPassage(7L)).thenReturn(Optional.of(100L));
        robot.interact(() -> controleur.ouvrirSur(PASSAGE));
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
    @DisplayName("#1405 : réimport confirmé : les résultats sont remplacés, et la confirmation dit ce qu'on perd")
    void reimport_confirme_remplace_les_resultats(FxRobot robot) {
        avecResultatsExistants(robot);
        when(service.reimporter(7L, Path.of("obs.csv")))
                .thenReturn(new BilanImport(
                        new ResultatsIdentification(101L, "obs.csv", "Brut", "2026-06-30T00:00", 7L), 1, 0, 0));

        robot.interact(() -> assertThat(controleur.deposerFichiers(List.of(new File("obs.csv"))))
                .isTrue());

        // Le message de confirmation est le seul endroit où l'utilisateur apprend qu'il va perdre son
        // travail de validation. C'est un contenu, pas une formalité.
        assertThat(confirmations)
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .contains("existent déjà")
                        .contains("Les validations en cours sur ce passage seront perdues"));
        verify(service).reimporter(7L, Path.of("obs.csv"));
    }

    @Test
    @DisplayName("#1405 : réimport refusé : rien n'est remplacé, et le dépôt n'est pas annoncé comme réussi")
    void reimport_refuse_ne_remplace_rien(FxRobot robot) {
        avecResultatsExistants(robot);
        confirme = false;

        // `false` : sans quoi le glisser-déposer signalerait un dépôt réussi alors que rien n'a été importé.
        robot.interact(() -> assertThat(controleur.deposerFichiers(List.of(new File("obs.csv"))))
                .isFalse());

        verify(service, never()).reimporter(anyLong(), any());
        verify(service, never()).importer(anyLong(), any());
    }

    @Test
    @DisplayName("#1015 : l'astuce « déposez un CSV » est visible pour un passage, masquée hors workflow")
    void astuce_depot_suit_le_workflow(FxRobot robot) {
        Label astuce = robot.lookup("#lblAstuceDepot").queryAs(Label.class);
        assertThat(astuce.isVisible()).as("source passage : workflow Tadarida").isTrue();
        assertThat(astuce.getText()).contains("CSV Tadarida");

        // La bibliothèque de références n'offre pas le workflow : l'astuce disparaît (non managée).
        robot.interact(() -> controleur.ouvrirSur(new SourceObservations.References("user-1")));

        assertThat(astuce.isVisible()).as("bibliothèque : pas de dépôt CSV").isFalse();
        assertThat(astuce.isManaged()).isFalse();
    }

    @Test
    @DisplayName("#719 : l'item « Importer depuis VigieChiro » est présent et visible (passage, connecté)")
    void item_import_vigiechiro_present(FxRobot robot) {
        MenuItem item = itemImporterVigieChiro(robot);

        assertThat(item.isVisible()).isTrue();
        assertThat(item.getText()).contains("VigieChiro");
    }

    @Test
    @DisplayName("#1255 : import d'un passage rattaché via le socle, bilan restitué dans le libellé")
    void import_vigiechiro_passage_rattache(FxRobot robot) {
        when(importVigieChiro.estRattache(7L)).thenReturn(true);
        when(importVigieChiro.importer(7L, false))
                .thenReturn(new BilanImport(
                        new ResultatsIdentification(101L, "vigiechiro", "Brut", "2026-06-30T00:00", 7L), 3, 0, 0));

        robot.interact(() -> itemImporterVigieChiro(robot).fire());

        Label message = robot.lookup("#lblImportVigieChiro").queryAs(Label.class);
        assertThat(message.getText())
                .contains("Résultats importés depuis VigieChiro")
                .contains("3");
    }

    @Test
    @DisplayName("#1255 : passage non rattaché sans participation, le message d'échec est restitué")
    void import_vigiechiro_sans_participation(FxRobot robot) {
        when(importVigieChiro.estRattache(7L)).thenReturn(false);
        when(importVigieChiro.participationsDisponibles()).thenReturn(ReponseApi.succes(List.of()));

        robot.interact(() -> itemImporterVigieChiro(robot).fire());

        Label message = robot.lookup("#lblImportVigieChiro").queryAs(Label.class);
        assertThat(message.getText()).contains("Aucune participation VigieChiro");
    }

    private static MenuItem itemImporterVigieChiro(FxRobot robot) {
        MenuButton menu = robot.lookup("#menuActions").queryAs(MenuButton.class);
        return menu.getItems().stream()
                .filter(i -> "itemImporterVigieChiro".equals(i.getId()))
                .findFirst()
                .orElseThrow();
    }
}

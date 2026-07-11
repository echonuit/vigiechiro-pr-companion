package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX de l'assistant **M-Import** : chargement du chrome + de la vue via
/// Guice sur une base SQLite jetable, affichage de l'écran, état initial du bouton « Importer »
/// (désactivé tant que rien n'est inspecté/rattaché) et alimentation de la combo des sites.
///
/// On ne pilote pas le `DirectoryChooser` natif (non testable headless) : la chaîne complète
/// inspection → rattachement → import est couverte en unitaire par `ImportationViewModelTest`.
@ExtendWith(ApplicationExtension.class)
class ImportationViewTest {

    private static final String ID_USER = "u-test";
    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-import");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        seeder(source);
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 760));
        injector.getInstance(NavigationImportation.class).ouvrir();
        stage.show();
    }

    private void seeder(SourceDeDonnees source) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        ServiceSites service = injector.getInstance(ServiceSites.class);
        Site etang = service.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(etang.id(), "A1", 43.4010, -1.5740, "Chêne");
        service.ajouterPoint(etang.id(), "B2", 43.4055, -1.5680, "Roselière");
        service.ajouterPoint(etang.id(), "C3", null, null, "GPS à relever"); // sans GPS (centre du carré)
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("L'assistant d'import : titre retiré (#693), ligne de guidance conservée")
    void affiche_la_guidance_sans_titre(FxRobot robot) {
        // Titre « Importer une nuit d'enregistrement » retiré (redondant avec le fil d'Ariane).
        assertThat(robot.lookup(".titre-page").tryQuery()).isEmpty();
        // La ligne de guidance de l'assistant est conservée.
        Label guidance = robot.lookup(".sous-titre-page").queryAs(Label.class);
        assertThat(guidance.getText()).contains("sans jamais modifier vos fichiers d'origine");
    }

    @Test
    @DisplayName("Le bouton « Importer » est désactivé tant que rien n'est inspecté ni rattaché")
    void bouton_importer_desactive_au_depart(FxRobot robot) {
        Button importer = robot.lookup("#boutonImporter").queryAs(Button.class);

        assertThat(importer.isDisabled()).isTrue();
    }

    @Test
    @DisplayName("#801 : le bouton « Écraser et réimporter » porte la classe destructive canonique")
    void bouton_ecraser_est_style_danger(FxRobot robot) {
        // Régression : la classe « action-danger » n'existait dans aucune feuille CSS ; le bouton le plus
        // dangereux retombait sur le style par défaut. La classe destructive du dépôt est « bouton-danger ».
        Button ecraser = robot.lookup("#boutonEcraser").queryAs(Button.class);

        assertThat(ecraser.getStyleClass())
                .as("classe destructive canonique (design.css) et non 'action-danger' inexistante")
                .contains("bouton-danger");
    }

    @Test
    @DisplayName("La combo des sites est alimentée par les sites de l'utilisateur (chargerSites)")
    void combo_sites_alimentee(FxRobot robot) {
        ComboBox<?> comboSites = robot.lookup("#comboSites").queryAs(ComboBox.class);

        assertThat(comboSites.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("La combo des sites affiche un libellé lisible (et non le toString brut du record)")
    void combo_site_libelle_lisible(FxRobot robot) {
        @SuppressWarnings("unchecked")
        ComboBox<Site> comboSites = robot.lookup("#comboSites").queryAs(ComboBox.class);
        Site site = comboSites.getItems().get(0);

        assertThat(comboSites.getConverter().toString(site)).isEqualTo("Carré 640380 — Étang de la Tuilière");
    }

    @Test
    @DisplayName("La zone de progression est masquée tant qu'aucun import n'est lancé")
    void progression_cachee_au_depart(FxRobot robot) {
        VBox zoneProgression = robot.lookup("#zoneProgression").queryAs(VBox.class);

        assertThat(zoneProgression.isVisible()).isFalse();
    }

    @Test
    @DisplayName("Les avertissements mélange et incohérence sont masqués avant toute inspection")
    void avertissements_caches_au_depart(FxRobot robot) {
        Label labelMelange = robot.lookup("#labelMelange").queryAs(Label.class);
        Label labelIncoherence = robot.lookup("#labelIncoherence").queryAs(Label.class);

        assertThat(labelMelange.isVisible()).isFalse();
        assertThat(labelIncoherence.isVisible()).isFalse();
    }

    @Test
    @DisplayName("#154 : la carte de confirmation trace le carré et surligne le point choisi")
    void carte_confirmation_surligne_le_point_choisi(FxRobot robot) {
        @SuppressWarnings("unchecked")
        ComboBox<Site> comboSites = robot.lookup("#comboSites").queryAs(ComboBox.class);
        robot.interact(() -> comboSites.getSelectionModel().select(0)); // site 640380
        WaitForAsyncUtils.waitForFxEvents();

        StackPane zone = robot.lookup("#zoneCarteRattachement").queryAs(StackPane.class);
        assertThat(zone.lookupAll(".carte-carre")).as("carré du site tracé").isNotEmpty();
        assertThat(zone.lookupAll(".carte-point-libelle"))
                .extracting(noeud -> ((Label) noeud).getText())
                .as("un marqueur par point géolocalisé")
                .contains("A1", "B2");

        // Choisir A1 (1er point, tri par code) → sa pastille passe en couleur de sélection, B2 reste gris.
        @SuppressWarnings("unchecked")
        ComboBox<PointDEcoute> comboPoints = robot.lookup("#comboPoints").queryAs(ComboBox.class);
        robot.interact(() -> comboPoints.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(pastille(zone, "A1").getFill())
                .as("le point choisi est surligné (indigo)")
                .isEqualTo(Color.web("#3f51b5"));
        assertThat(pastille(zone, "B2").getFill())
                .as("les autres points restent gris")
                .isEqualTo(Color.web("#9aa0a6"));
    }

    @Test
    @DisplayName("#154 : un point SANS GPS choisi reste surligné (désempilé, anneau indigo)")
    void carte_confirmation_point_sans_gps_reste_surligne(FxRobot robot) {
        @SuppressWarnings("unchecked")
        ComboBox<Site> comboSites = robot.lookup("#comboSites").queryAs(ComboBox.class);
        robot.interact(() -> comboSites.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        @SuppressWarnings("unchecked")
        ComboBox<PointDEcoute> comboPoints = robot.lookup("#comboPoints").queryAs(ComboBox.class);
        robot.interact(() -> comboPoints.getSelectionModel().select(2)); // C3, le point sans GPS (3e par code)
        WaitForAsyncUtils.waitForFxEvents();

        // C3 est un marqueur approximatif (anneau pointillé) : sa couleur de sélection est dans le trait.
        StackPane zone = robot.lookup("#zoneCarteRattachement").queryAs(StackPane.class);
        Circle c3 = pastille(zone, "C3");
        assertThat(c3.getStrokeDashArray()).as("rendu approximatif (sans GPS)").isNotEmpty();
        assertThat(c3.getStroke())
                .as("le point sans GPS choisi reste surligné (indigo)")
                .isEqualTo(Color.web("#3f51b5"));
    }

    /// Pastille (cercle) du marqueur dont le libellé vaut `code`, dans la carte de confirmation.
    private static Circle pastille(StackPane zone, String code) {
        Node libelle = zone.lookupAll(".carte-point-libelle").stream()
                .filter(noeud -> code.equals(((Label) noeud).getText()))
                .findFirst()
                .orElseThrow();
        return (Circle) ((Group) libelle.getParent())
                .getChildren().stream()
                        .filter(Circle.class::isInstance)
                        .findFirst()
                        .orElseThrow();
    }
}

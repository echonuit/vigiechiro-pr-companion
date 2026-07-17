package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de l'**entrée de navigation M-Site-detail → M-Passage** : sur le vrai
/// injecteur ([RacineInjecteur]) avec un site + un point + un passage seedés, on ouvre
/// M-Site-detail puis on **double-clique** sur la ligne de passage. On vérifie que l'écran pivot
/// M-Passage s'affiche (son stepper de statut apparaît). Couvre la chaîne `sites → passage`.
@ExtendWith(ApplicationExtension.class)
class SiteDetailVersPassageViewTest {

    // Test d'intégration sites -> passage : pilote l'écran M-Passage (il cherche #stepper).
    private static final String ID_USER = "u-1";
    private static final String DATE = "2026-06-22";

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-nav-passage");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-01-01", ID_USER));
        Long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", 43.5, 5.4, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur("1925492", "V1.01", null));
        new PassageDao(source)
                .insert(new Passage(
                        null,
                        2,
                        2026,
                        DATE,
                        "20:25:00",
                        "07:47:00",
                        null,
                        StatutWorkflow.TRANSFORME,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        "1925492"));

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 760));
        injector.getInstance(NavigationSites.class).ouvrirDetail(site);
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Double-cliquer une ligne de passage ouvre l'écran pivot M-Passage")
    void double_clic_ouvre_m_passage(FxRobot robot) {
        robot.doubleClickOn(DATE);

        HBox stepper = robot.lookup("#stepper").queryAs(HBox.class);
        assertThat(stepper.getChildren()).isNotEmpty();
    }

    @Test
    @DisplayName("#1796 : « Ouvrir le passage » du menu de ligne ouvre l'écran M-Passage")
    void menu_de_ligne_ouvre_m_passage(FxRobot robot) {
        TableView<?> table = robot.lookup("#tablePassages").queryAs(TableView.class);
        robot.interact(() -> table.getSelectionModel().select(0));
        MenuItem ouvrir = table.getContextMenu().getItems().get(0);
        assertThat(ouvrir.getText()).isEqualTo("Ouvrir le passage");

        robot.interact(ouvrir::fire);

        HBox stepper = robot.lookup("#stepper").queryAs(HBox.class);
        assertThat(stepper.getChildren()).isNotEmpty();
    }
}

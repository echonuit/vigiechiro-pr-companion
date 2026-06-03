package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX du chrome (`MainView`) : l'affordance **« 🏠 Accueil »** est masquée
/// sur l'accueil, apparaît dès qu'une feature prend la main sur la zone centrale, et ramène à
/// l'accueil au clic (via le socle [Navigateur#afficherAccueil]). Couvre #22.
@ExtendWith(ApplicationExtension.class)
class MainViewTest {

    private Navigateur navigateur;
    private NavigationViewModel navigation;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-main");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        navigateur = injector.getInstance(Navigateur.class);
        navigation = injector.getInstance(NavigationViewModel.class);
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1000, 700));
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Le lien « Accueil » est masqué sur l'accueil et apparaît dans une feature")
    void lien_accueil_visible_hors_accueil(FxRobot robot) {
        Hyperlink lien = robot.lookup("#lienAccueil").queryAs(Hyperlink.class);
        assertThat(lien.isVisible()).isFalse();

        robot.interact(() -> navigateur.afficher(new Group(), "sites", "Mes sites de suivi"));

        assertThat(lien.isVisible()).isTrue();
    }

    @Test
    @DisplayName("Cliquer « Accueil » ramène à l'accueil et masque de nouveau le lien")
    void clic_accueil_revient_a_l_accueil(FxRobot robot) {
        robot.interact(() -> navigateur.afficher(new Group(), "sites", "Mes sites de suivi"));
        Hyperlink lien = robot.lookup("#lienAccueil").queryAs(Hyperlink.class);

        robot.interact(lien::fire);

        assertThat(lien.isVisible()).isFalse();
        assertThat(robot.lookup("#cartesActivites").tryQuery()).isPresent();
    }

    @Test
    @DisplayName("#54 : le lien « Accueil » est grisé quand la navigation est verrouillée")
    void lien_accueil_grise_si_navigation_verrouillee(FxRobot robot) {
        robot.interact(() -> navigateur.afficher(new Group(), "import", "Importer une nuit"));
        Hyperlink lien = robot.lookup("#lienAccueil").queryAs(Hyperlink.class);
        assertThat(lien.isDisabled()).isFalse();

        robot.interact(() -> navigation.setNavigationVerrouillee(true));
        assertThat(lien.isDisabled()).isTrue();

        robot.interact(() -> navigation.setNavigationVerrouillee(false));
        assertThat(lien.isDisabled()).isFalse();
    }
}

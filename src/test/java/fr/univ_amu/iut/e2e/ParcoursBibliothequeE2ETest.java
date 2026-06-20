package fr.univ_amu.iut.e2e;

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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// **Test E2E (smoke) de la feature `bibliotheque`** (COULD) : depuis le **tableau de bord**, un
/// clic réel sur la carte **« Bibliothèque de sons »** ouvre l'écran **M-Bibliotheque**
/// (`accueil → bibliotheque`). Vérifie le câblage carte d'accueil → navigation et le chargement
/// sans erreur de l'écran (table des entrées présente), sur une base vide : il s'agit d'un fumigène,
/// pas d'un parcours de données (la bibliothèque de sons de référence sera alimentée par les
/// étudiants).
@Tag("conformite")
@ExtendWith(ApplicationExtension.class)
class ParcoursBibliothequeE2ETest {

    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-biblio");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        new MigrationSchema(injector.getInstance(SourceDeDonnees.class)).migrer();

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1280, 860));
        stage.show();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Tableau de bord : la carte « Bibliothèque de sons » ouvre M-Bibliotheque")
    void accueil_ouvre_bibliotheque(FxRobot robot) {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);
        assertThat(navigation.getVueCourante()).isEqualTo("accueil");

        robot.clickOn("Bibliothèque de sons");

        assertThat(navigation.getVueCourante()).isEqualTo("bibliotheque");
        assertThat(robot.lookup("#tableEntrees").queryAs(TableView.class)).isNotNull();
    }
}

package fr.univ_amu.iut.qualification.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de la **façade** [NavigationQualification] : démarre le vrai
/// injecteur applicatif ([RacineInjecteur]) sur une base SQLite jetable, charge le chrome, puis
/// appelle `ouvrir(idPassage)`. On vérifie que la vue M-Qualification se charge et s'affiche via
/// Guice + le `Navigateur` du socle (chemin de ressource FXML, controllerFactory, publication),
/// comme `ImportationViewTest` / `MesSitesViewTest` pour leurs façades.
///
/// Le passage demandé est absent : les deux ViewModel neutralisent l'erreur dans leur message, ce
/// qui suffit à exercer toute la chaîne de navigation sans seeding d'un passage vérifiable complet
/// (ce flux nominal est couvert par `QualificationViewTest` et `ServiceQualificationTest`).
@ExtendWith(ApplicationExtension.class)
class NavigationQualificationViewTest {

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-qualif");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 760));
        injector.getInstance(NavigationQualification.class)
                .ouvrir(new ContextePassage(999L, 1, new ContexteSite("640380", "A1", "Étang de la Tuilière")));
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("ouvrir(idPassage) charge et affiche l'écran de qualification via Guice")
    void ouvrir_affiche_l_ecran(FxRobot robot) {
        // Titre retiré (#693) : l'écran de qualification s'identifie par sa ligne de guidance.
        Label guidance = robot.lookup(".sous-titre-page").queryAs(Label.class);

        assertThat(guidance.getText()).contains("Écoutez quelques séquences");
    }
}

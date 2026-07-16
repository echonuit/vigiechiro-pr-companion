package fr.univ_amu.iut.diagnostic.view;

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
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de la **façade** [NavigationDiagnostic] : sur le vrai injecteur
/// ([RacineInjecteur]) + chrome, on appelle `ouvrir(idPassage)`. On vérifie que l'écran
/// M-Diagnostic se charge via Guice + le `Navigateur` du socle (ressource FXML, controllerFactory,
/// publication), comme `NavigationQualificationViewTest`. Le passage est absent : le ViewModel
/// neutralise l'erreur dans son message, ce qui exerce toute la chaîne sans seeding complet.
@ExtendWith(ApplicationExtension.class)
class NavigationDiagnosticViewTest {

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-diagnostic");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 760));
        injector.getInstance(NavigationDiagnostic.class)
                .ouvrir(new ContextePassage(999L, 1, new ContexteSite("640380", "A1", "Étang de la Tuilière")));
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("ouvrir(idPassage) charge l'écran M-Diagnostic via Guice")
    void ouvrir_affiche_l_ecran(FxRobot robot) {
        Label message = robot.lookup("#lblMessage").queryAs(Label.class);
        HBox ligneGps = robot.lookup("#ligneGps").queryAs(HBox.class);

        assertThat(message.getText()).contains("introuvable");
        // Pas de ligne GPS tant qu'aucun diagnostic n'est chargé (visibilité liée à l'enregistreur).
        assertThat(ligneGps.isVisible()).isFalse();
    }
}

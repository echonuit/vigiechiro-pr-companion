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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.NodeQueryUtils;
import org.testfx.util.WaitForAsyncUtils;

/// **Test E2E (smoke) de l'entrée « Sons & validation »** (#audio) : depuis le **tableau de bord**, un
/// clic réel sur la carte **« Sons & validation »** ouvre la **vue audio unifiée** (sur la source `References`)
/// sur la source `References` (`accueil → audio`). Vérifie le câblage carte d'accueil → contrat socle
/// `OuvrirAudio` → navigation et le chargement sans erreur de l'écran (table des observations présente),
/// sur une base vide : il s'agit d'un fumigène, pas d'un parcours de données (le corpus de référence est
/// alimenté séparément). Remplace l'ancien parcours « accueil → bibliotheque » depuis que la carte est
/// repointée vers la vue audio unifiée.
@ExtendWith(ApplicationExtension.class)
class ParcoursSonsReferenceE2ETest {

    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-sons-reference");
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
    @DisplayName("Tableau de bord : la carte « Sons & validation » ouvre la vue audio unifiée")
    void accueil_ouvre_vue_audio(FxRobot robot) throws TimeoutException {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);
        assertThat(navigation.getVueCourante()).isEqualTo("accueil");

        // ⚠️ Attendre EXACTEMENT ce que le clic exige (#3836). L'attente posée par #3823 vérifiait
        // `Node::isVisible`, le drapeau **local** du nœud. Or `clickOn` filtre avec
        // `NodeQueryUtils.isVisible()`, qui exige **en plus** que le nœud **intersecte le rectangle de
        // la scène** - mesuré en lisant le bytecode de `FxRobot`, puis vérifié par une sonde : un nœud
        // posé hors cadre porte un drapeau local à `true` et n'est **pas** cliquable.
        //
        // Une carte d'accueil tardive passe donc l'ancienne attente alors qu'elle est encore sous la
        // ligne de flottaison, et le clic échoue - « returned 1 nodes, but no nodes were visible ».
        // C'est pour cela que #3823 n'avait pas suffi : la règle était bonne, le prédicat non.
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup("Sons & validation")
                        .match(NodeQueryUtils.isVisible())
                        .tryQuery()
                        .isPresent());
        robot.clickOn("Sons & validation");

        assertThat(navigation.getVueCourante()).isEqualTo("audio");
        assertThat(robot.lookup("#tableObservations").queryAs(TableView.class)).isNotNull();
    }
}

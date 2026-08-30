package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.DefilementChrome;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.recette.Attente;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

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
        FenetreAjustable.poser(stage, racine, 1280, 860);
        FenetreAjustable.afficher(stage);
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

        // L'attente exige EXACTEMENT ce que le clic exige (#3836) : pas seulement le drapeau local du
        // nœud, mais l'intersection avec le rectangle de la scène. Une carte encore sous la ligne de
        // flottaison porte un drapeau à `true` et n'est pourtant pas cliquable.
        //
        // Elle passe par [AttenteAvantClic] parce qu'elle a expiré deux fois en CI sans rien laisser
        // d'exploitable (#3911) : même prédicat, même butoir, mais elle dit ce qu'elle a vu.
        AttenteAvantClic.attendreCliquable(
                robot, "Sons & validation", 10, injector.getInstance(DefilementChrome.class));
        robot.clickOn("Sons & validation");

        // Le clic parti, l'ecran n'est pas ouvert pour autant. Des sept cartes de l'accueil, celle-ci
        // est la SEULE dont l'ouverture fasse de l'entree-sortie avant d'afficher : une requete en base
        // pour l'identifiant courant, puis `occuper(...)` qui charge les sons hors du fil JavaFX
        // (#1214). `ScenarioAccueilTest` l'avait mesure en #4408 ; ce banc-ci affirmait sans attendre,
        // et il est tombe 4 fois sur 1 150 (#4814, mesure par #4811).
        //
        // La lecture se fait SUR le fil FX : le graphe de scene n'est pas partageable, et `waitFor`
        // rappelle le predicat depuis le fil du test.
        Attente.queSurLeFil(() -> "audio".equals(navigation.getVueCourante()), "que la vue audio s'ouvre");
        Attente.queSurLeFil(
                () -> !robot.lookup("#tableObservations").queryAll().isEmpty(),
                "que la table des observations soit posée");

        assertThat(navigation.getVueCourante()).isEqualTo("audio");
        assertThat(robot.lookup("#tableObservations").queryAs(TableView.class)).isNotNull();
    }
}

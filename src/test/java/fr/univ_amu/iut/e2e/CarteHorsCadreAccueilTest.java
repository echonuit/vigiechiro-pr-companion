package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.DefilementChrome;
import fr.univ_amu.iut.commun.view.TailleOuverture;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Une carte d'accueil **hors cadre au repos** reste atteignable (#3925, suite de #3911).
///
/// ## La largeur choisie n'est pas arbitraire
///
/// La scène est montée à **`TailleOuverture.LARGEUR_MINIMALE`** : c'est le plancher que l'application
/// s'autorise, et c'est la largeur à laquelle les runners tournent - l'écran headless étant plus petit
/// que la taille demandée, la fenêtre y est ramenée. Mesuré à cette largeur : le `FlowPane` des cartes
/// enroule sur une rangée de plus, et « Sons & validation » tombe à `y=951` dans une scène de 860.
///
/// Les tests de parcours montaient tous une scène **large** : le défaut leur échappait sur un poste et
/// les cueillait en CI, trois fois en deux jours.
///
/// ## Ce que ce test tient
///
/// Que l'attente avant clic **fait défiler** vers sa cible, par le port de révélation du chrome, plutôt
/// que de conclure à l'inaccessibilité. Le contenu défile : la barre verticale du `ScrollPane` central
/// est visible, et après défilement les sept cartes sont dans le cadre.
@ExtendWith(ApplicationExtension.class)
class CarteHorsCadreAccueilTest {

    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-carte-hors-cadre");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        new MigrationSchema(injector.getInstance(SourceDeDonnees.class)).migrer();
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injector));
        Parent racine = loader.load();
        // La largeur du plancher, et une hauteur qui laisse la dernière rangée dehors.
        stage.setScene(new Scene(racine, TailleOuverture.LARGEUR_MINIMALE, 860));
        stage.show();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#3925 : à la largeur minimale, la carte hors cadre est amenée dans le champ")
    void la_carte_hors_cadre_est_amenee_dans_le_champ(FxRobot robot) {
        assertThatCode(() -> AttenteAvantClic.attendreCliquable(
                        robot, "Sons & validation", 5, injector.getInstance(DefilementChrome.class)))
                .as("la carte est sous la ligne de flottaison au repos ; l'attente doit faire défiler"
                        + " vers elle, comme un utilisateur, au lieu de conclure qu'elle est inatteignable")
                .doesNotThrowAnyException();
    }
}

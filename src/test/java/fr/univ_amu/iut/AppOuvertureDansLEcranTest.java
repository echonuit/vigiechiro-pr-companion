package fr.univ_amu.iut;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.TailleOuverture;
import java.nio.file.Path;
import javafx.geometry.Rectangle2D;
import javafx.scene.layout.Pane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// La fenêtre principale s'ouvre **entièrement dans l'écran**, même ouverte d'abord à son plancher
/// (#5074).
///
/// `TailleOuverture` borne la **taille**, jamais la **place** : une fenêtre aussi large que l'écran
/// mais posée à `x = 50` en sort quand même. C'est ce que la javadoc de cette classe-là dit vouloir
/// éviter, « une fenêtre dont le bas passe sous la barre des tâches, hors d'atteinte ».
///
/// Le banc **pose lui-même** l'état que la course de #5018 produit - une fenêtre montrée au plancher,
/// donc centrée pour 900 x 600, soit `(50, 133)` - parce qu'une course ne se commande pas : la
/// laisser décider ferait un banc qui rougit une fois sur deux.
///
/// Sans le remède, la fenêtre finissait en `x=50 y=133 1000x900`, débordait l'écran de 1000 x 1000,
/// et le rétrécissement suivant faisait sortir `clearRect` du tampon de JavaFX : toute la série
/// rendait alors `BUILD FAILURE` sur `AppTest`.
@ExtendWith(ApplicationExtension.class)
class AppOuvertureDansLEcranTest {

    private Stage stage;

    @TempDir
    private Path dossierTemporaire;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        System.setProperty("vigiechiro.workspace", dossierTemporaire.toString());
        // L'état que la course de #5018 produit : une fenêtre montrée à la taille du plancher, donc
        // centrée POUR cette taille-là. Il se pose par une SCÈNE et jamais par `setWidth`/`setHeight`,
        // qui figeraient le Stage partagé du fork et casseraient les classes suivantes - défaut déjà
        // venu quatre fois (#1940, #1967, #3452, #4130) et refusé par `ConventionsDEcritureTest`.
        // Par `Habillage`, jamais `new Scene` : un banc qui MESURE une géométrie sur une scène nue
        // mesurerait la police de la machine et non celle du produit (#3773).
        stage.setScene(Habillage.scene(new Pane(), TailleOuverture.LARGEUR_MINIMALE, TailleOuverture.HAUTEUR_MINIMALE));
        stage.show();
        stage.centerOnScreen();
        new App().start(stage);
    }

    /// Relâche ce que `App.start` a posé sur le Stage **partagé** du fork.
    ///
    /// Sans cela, les tailles minimales (#3452) restent collées et la classe suivante hérite d'un
    /// plancher qui empêche sa fenêtre de grandir : son cas de croissance échoue sur « 600 n'est pas
    /// supérieur à 600 », très loin de la cause. `AppTest` porte le même nettoyage, pour la même
    /// raison ; toute classe qui démarre l'application le doit.
    @AfterEach
    void relacherLesContraintes(FxRobot robot) {
        System.clearProperty("vigiechiro.workspace");
        robot.interact(() -> {
            stage.setMinWidth(0);
            stage.setMinHeight(0);
            stage.sizeToScene();
            stage.setScene(null);
        });
    }

    @Test
    @DisplayName("#5074 : la fenêtre ouverte au plancher puis agrandie tient encore dans l'écran")
    void la_fenetre_ouverte_tient_dans_l_ecran() {
        Rectangle2D ecran = Screen.getPrimary().getVisualBounds();
        String geometrie = String.format(
                "écran %.0fx%.0f, fenêtre x=%.0f y=%.0f %.0fx%.0f",
                ecran.getWidth(), ecran.getHeight(), stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());

        assertThat(stage.getX() + stage.getWidth())
                .as("le bord droit sort de l'écran (%s)", geometrie)
                .isLessThanOrEqualTo(ecran.getMaxX());
        assertThat(stage.getY() + stage.getHeight())
                .as("le bas sort de l'écran, hors d'atteinte sous la barre des tâches (%s)", geometrie)
                .isLessThanOrEqualTo(ecran.getMaxY());
    }
}

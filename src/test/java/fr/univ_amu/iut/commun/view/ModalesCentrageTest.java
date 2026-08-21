package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Une fenêtre modale se pose **au centre de son propriétaire**, qu'elle soit un [Stage] ou un
/// [Dialog].
///
/// ## Pourquoi ce test existe
///
/// #4084 a posé le placement pour les neuf modales qui construisent un `Stage`, et rien ne le
/// vérifiait : il a été éprouvé par un **clip de recette**, donc à l'oeil, une fois. La correction a
/// par ailleurs écarté les quatre modales qui construisent un `Dialog`, sur une supposition jamais
/// mesurée - JavaFX les centrerait déjà. Le clip S1-37 l'a démentie : le dialogue « Carré récupéré »
/// s'y affichait dans le coin haut-gauche, bord gauche rogné, propriétaire pourtant déclaré.
///
/// Une règle vérifiée par une capture est une règle vérifiée **une fois**. Celle-ci se remesure à
/// chaque exécution, et sur les deux formes de fenêtre, puisque c'est la seconde qui manquait.
///
/// ## Ce que « centré » veut dire ici
///
/// Le centre du propriétaire, à un pixel près : la tolérance absorbe l'arrondi de la division par
/// deux, sans absorber un placement en (0, 0), qui est le défaut recherché.
@ExtendWith(ApplicationExtension.class)
class ModalesCentrageTest {

    /// Coin et taille de l'hôte, choisis **non nuls et non centrés** : un propriétaire posé en (0, 0)
    /// rendrait le défaut indiscernable du remède sur l'axe des X.
    private static final double HOTE_X = 120;
    private static final double HOTE_Y = 60;
    private static final double HOTE_LARGEUR = 800;
    private static final double HOTE_HAUTEUR = 600;

    private Stage hote;

    @Start
    void start(Stage stage) {
        hote = stage;
        stage.setScene(new Scene(new StackPane(), HOTE_LARGEUR, HOTE_HAUTEUR));
        stage.show();
    }

    /// Pose l'hôte **et attend que ce soit fait**, avant d'ouvrir quoi que ce soit par-dessus.
    ///
    /// ⚠️ Poser la position dans le `@Start` ne suffit pas, et le test l'a montré en rougissant d'un
    /// écart de 120 px - exactement [#HOTE_X]. La modale se centrait sur un hôte encore en x = 0
    /// pendant que l'assertion lisait déjà 120 : le harnais déplace la fenêtre primaire après
    /// l'amorçage. Le test passait ou non selon l'ordre d'arrivée, ce qui est la pire des deux issues :
    /// un vert obtenu par hasard aurait fait croire la mesure faite.
    private void poserLHote(FxRobot robot) {
        robot.interact(() -> {
            hote.setX(HOTE_X);
            hote.setY(HOTE_Y);
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("#4084 : une modale Stage se pose au centre de son propriétaire")
    void une_modale_stage_se_centre(FxRobot robot) {
        poserLHote(robot);
        AtomicReference<Stage> porteur = new AtomicReference<>();
        robot.interact(() -> {
            Stage nouvelle = new Stage();
            nouvelle.initOwner(hote);
            nouvelle.initModality(Modality.WINDOW_MODAL);
            nouvelle.setScene(new Scene(new StackPane(), 400, 300));
            Modales.centrerSur(nouvelle, hote);
            nouvelle.show();
            porteur.set(nouvelle);
        });
        WaitForAsyncUtils.waitForFxEvents();
        Stage modale = porteur.get();

        assertThat(modale.getX() + modale.getWidth() / 2)
                .as("le centre horizontal de la modale rejoint celui de son propriétaire")
                .isCloseTo(hote.getX() + hote.getWidth() / 2, within(1.0));
        assertThat(modale.getY() + modale.getHeight() / 2)
                .as("le centre vertical de la modale rejoint celui de son propriétaire")
                .isCloseTo(hote.getY() + hote.getHeight() / 2, within(1.0));

        robot.interact(modale::close);
    }

    @Test
    @DisplayName("#4092 : un Dialog se pose au centre de son propriétaire, comme un Stage")
    void un_dialogue_se_centre(FxRobot robot) {
        poserLHote(robot);
        AtomicReference<Alert> porteur = new AtomicReference<>();
        robot.interact(() -> {
            Alert nouvelle = new Alert(AlertType.INFORMATION, "Carré 640380 récupéré.", ButtonType.OK);
            nouvelle.initOwner(hote);
            nouvelle.show();
            porteur.set(nouvelle);
        });
        WaitForAsyncUtils.waitForFxEvents();
        Alert alerte = porteur.get();

        assertThat(alerte.getX() + alerte.getWidth() / 2)
                .as("le centre horizontal du dialogue rejoint celui de son propriétaire")
                .isCloseTo(hote.getX() + hote.getWidth() / 2, within(1.0));
        assertThat(alerte.getY() + alerte.getHeight() / 2)
                .as("le centre vertical du dialogue rejoint celui de son propriétaire")
                .isCloseTo(hote.getY() + hote.getHeight() / 2, within(1.0));

        robot.interact(alerte::close);
    }

    @Test
    @DisplayName("#4092 : fenetreDe rend la fenêtre d'un noeud attaché, et null sinon")
    void fenetre_de_rend_la_fenetre_du_noeud(FxRobot robot) {
        assertThat(Modales.fenetreDe(hote.getScene().getRoot()))
                .as("un noeud attaché désigne la fenêtre qui le porte")
                .isSameAs(hote);
        assertThat(Modales.fenetreDe(new StackPane()))
                .as("un noeud jamais attaché n'a pas de fenêtre, et cela n'est pas une erreur")
                .isNull();
        assertThat(Modales.fenetreDe(null)).as("un noeud absent non plus").isNull();
    }
}

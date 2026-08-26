package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// La fenêtre d'un cas reste ajustable pour la classe suivante du fork (ADR 4475).
///
/// Ce cas rejoue la contamination elle-même : une première scène est posée grande, puis une seconde
/// petite. Sur un stage figé, la seconde est relue à la taille de la première, et c'est ce qui a
/// coûté quatre venues du même défaut.
@ExtendWith(ApplicationExtension.class)
class FenetreAjustableTest {

    private static final double GRANDE = 900;
    private static final double PETITE = 320;

    private Stage fenetre;

    @Start
    void start(Stage stage) {
        fenetre = stage;
        FenetreAjustable.poser(stage, new VBox(new Label("premiere")), GRANDE, GRANDE);
        FenetreAjustable.afficher(stage);
    }

    @Test
    void la_premiere_scene_garde_la_taille_demandee() {
        assertThat(fenetre.getScene().getWidth())
                .as("une scène posée à %s doit être lue à %s", GRANDE, GRANDE)
                .isEqualTo(GRANDE);
    }

    @Test
    void une_fenetre_figee_par_la_classe_precedente_s_ajuste(FxRobot robot) {
        // Ce que laisse une classe qui a exercé le dimensionnement de l'application : un stage
        // dimensionné à la main. C'est CE geste qui fige, et sans lui le cas ne verrait rien.
        robot.interact(() -> {
            fenetre.setWidth(GRANDE);
            fenetre.setHeight(GRANDE);
        });

        robot.interact(() -> {
            FenetreAjustable.poser(fenetre, new VBox(new Label("seconde")), PETITE, PETITE);
            FenetreAjustable.afficher(fenetre);
        });

        assertThat(fenetre.getScene().getWidth())
                .as("la fenêtre héritée doit s'ajuster à la seconde scène, et non la figer à %s", GRANDE)
                .isEqualTo(PETITE);
    }
}

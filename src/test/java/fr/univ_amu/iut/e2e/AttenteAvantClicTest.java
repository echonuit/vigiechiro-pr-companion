package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// L'instrument de #3911 se vérifie lui-même : un rapport qui ne nomme pas la bonne cause vaut la
/// `TimeoutException` nue qu'il remplace.
///
/// Les deux situations que l'attente doit distinguer sont montées **pour de bon** : un libellé absent,
/// et un libellé présent mais posé hors du cadre de la scène. La seconde est celle qui a coûté trois
/// enquêtes, et c'est celle qu'aucun message ne nommait.
@ExtendWith(ApplicationExtension.class)
class AttenteAvantClicTest {

    /// La scene montee par ce test, retenue pour que l'assertion LISE sa taille au lieu de la
    /// recopier - voir [#un_libelle_hors_cadre_est_nomme].
    private Scene scene;

    @Start
    void start(Stage stage) {
        Pane racine = new Pane();
        Label horsCadre = new Label("cible hors cadre");
        // Posé bien au-delà du bas de la scène : son drapeau `visible` reste vrai, et pourtant le clic
        // le refuse. C'est exactement la forme du défaut observé en CI.
        horsCadre.setLayoutY(2000);
        racine.getChildren().add(horsCadre);
        scene = new Scene(racine, 400, 300);
        stage.setScene(scene);
        stage.show();
    }

    @Test
    @DisplayName("#3911 : un libellé absent est nommé comme absent")
    void un_libelle_absent_est_nomme(FxRobot robot) {
        assertThatThrownBy(() -> AttenteAvantClic.attendreCliquable(robot, "libellé qui n'existe pas", 1))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Aucun nœud ne porte ce libellé")
                .hasMessageContaining("n'est pas devenu cliquable en 1 s");
    }

    @Test
    @DisplayName("#3911 : un libellé présent mais hors cadre est nommé comme tel, avec ses bornes")
    void un_libelle_hors_cadre_est_nomme(FxRobot robot) {
        assertThatThrownBy(() -> AttenteAvantClic.attendreCliquable(robot, "cible hors cadre", 1))
                .isInstanceOf(AssertionError.class)
                .as("le rapport doit distinguer « absent » de « présent mais hors cadre »")
                .hasMessageContaining("HORS CADRE")
                .hasMessageContaining("visible=true")
                // ATTENTION : la taille attendue se LIT sur la scene, elle ne se recopie pas depuis le
                // `new Scene(racine, 400, 300)` ci-dessus. Les 400 demandes ne sont pas ceux qu'on
                // obtient partout : sur un runner, la fenetre est rabattue a
                // `TailleOuverture.LARGEUR_MINIMALE` (900), et ce test rougissait alors sur
                // « scene 900x300 » la ou il attendait « scene 400x300 » - vert en local, rouge en CI.
                //
                // Ce qu'il garde n'est pas la largeur de la scene, c'est que le rapport la NOMME.
                .hasMessageContaining(String.format("scène %.0fx%.0f", scene.getWidth(), scene.getHeight()));
    }
}

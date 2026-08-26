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

    @Start
    void start(Stage stage) {
        Pane racine = new Pane();
        Label horsCadre = new Label("cible hors cadre");
        // Posé bien au-delà du bas de la scène : son drapeau `visible` reste vrai, et pourtant le clic
        // le refuse. C'est exactement la forme du défaut observé en CI.
        horsCadre.setLayoutY(2000);
        racine.getChildren().add(horsCadre);
        stage.setScene(new Scene(racine, 400, 300));
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
                // Un MOTIF, ni un littéral ni une lecture de la scène.
                //
                // La ligne disait `scène 400x300`, recopié du `new Scene(racine, 400, 300)` ci-dessus.
                // Les 400 demandés ne sont pas ceux qu'on obtient partout : sur un runner la fenêtre
                // est rabattue à `TailleOuverture.LARGEUR_MINIMALE` (900), et le test rougissait sur
                // « scène 900x300 » là où il attendait « scène 400x300 » - vert en local, rouge en CI.
                //
                // Interroger la scène sur sa largeur au moment d'asserter aurait déplacé le défaut
                // sans le
                // supprimer : cette lecture se fait depuis le fil de TEST, alors qu'AttenteAvantClic
                // prend soin de lire ses bornes sur le fil JavaFX, « des bornes lues depuis le fil de
                // test peuvent être en cours de recalcul ».
                //
                // Ce que ce test garde n'est ni la largeur de la scène ni sa lecture : c'est que le
                // rapport la NOMME. Un motif le dit exactement, et ne dépend d'aucun des deux.
                .hasMessageFindingMatch("scène \\d+x\\d+");
    }
}

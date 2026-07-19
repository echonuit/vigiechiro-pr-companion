package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Garde-fous des patrons communs aux modales.
///
/// - **Fermeture par Échap** (#1505) : on équipe un Stage réel via [Modales#fermerParEchap] puis on vérifie
///   qu'Échap le ferme et qu'une autre touche ne fait rien.
/// - **Croissance du contenu** (#1931, #1940) : la fenêtre suit ce qui paraît après son ouverture, **sans**
///   pour autant se figer - c'est la seconde moitié qui avait manqué, et qui coûtait des échecs dans des
///   classes de test sans rapport.
@ExtendWith(ApplicationExtension.class)
class ModalesTest {

    private Stage modale;

    @Start
    void demarrer(Stage stage) {
        modale = stage;
        modale.setScene(new Scene(new StackPane(new Button("OK")), 240, 140));
        Modales.fermerParEchap(modale);
        modale.show();
    }

    private void frapper(KeyCode code) {
        Event.fireEvent(
                modale.getScene().getRoot(),
                new KeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, "", code, false, false, false, false));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("#1505 : Échap ferme la modale")
    void echap_ferme_la_modale(FxRobot robot) {
        assertThat(modale.isShowing()).isTrue();

        robot.interact(() -> frapper(KeyCode.ESCAPE));

        assertThat(modale.isShowing()).as("Échap ferme la fenêtre modale").isFalse();
    }

    @Test
    @DisplayName("#1505 : une autre touche ne ferme pas la modale")
    void autre_touche_ne_ferme_pas(FxRobot robot) {
        robot.interact(() -> frapper(KeyCode.A));

        assertThat(modale.isShowing()).as("seule Échap ferme").isTrue();
    }

    @Test
    @DisplayName("#1931 : la fenêtre suit le contenu qui paraît après son ouverture")
    void la_fenetre_suit_la_croissance_du_contenu(FxRobot robot) {
        SimpleBooleanProperty revelation = new SimpleBooleanProperty(false);
        Label cache = etiquetteCachee();
        VBox racine = new VBox(new Label("visible dès l'ouverture"), cache);
        Stage fenetre = ouvrirAjustee(racine, robot);
        Modales.suivreLaCroissance(racine, revelation);
        double aLOuverture = fenetre.getHeight();

        reveler(cache, revelation, robot);

        assertThat(fenetre.getHeight())
                .as("le contenu a grandi d'une ligne : la fenêtre aussi")
                .isGreaterThan(aLOuverture);
        robot.interact(fenetre::close);
    }

    @Test
    @DisplayName("#1940 : après une croissance, la fenêtre s'ajuste encore aux scènes suivantes")
    void la_croissance_ne_fige_pas_le_dimensionnement(FxRobot robot) {
        SimpleBooleanProperty revelation = new SimpleBooleanProperty(false);
        Label cache = etiquetteCachee();
        VBox racine = new VBox(new Label("visible dès l'ouverture"), cache);
        Stage fenetre = ouvrirAjustee(racine, robot);
        Modales.suivreLaCroissance(racine, revelation);
        reveler(cache, revelation, robot);
        double apresCroissance = fenetre.getHeight();

        // La propriété qui compte n'est pas la taille obtenue, c'est que la fenêtre reste AJUSTABLE : un
        // Stage passé en dimensionnement explicite (setWidth / setHeight) cesse définitivement de suivre
        // les scènes qu'on lui pose ensuite. Sans conséquence pour une modale que l'on jette après usage,
        // mais le Stage du harnais TestFX est partagé par toutes les classes d'un même fork : figé, il
        // faisait échouer les suivantes sur des noeuds « invisibles », très loin de la cause.
        robot.interact(() -> fenetre.setScene(new Scene(new VBox(dixLignes()))));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(fenetre.getHeight())
                .as("une scène bien plus haute est posée : la fenêtre doit s'y ajuster encore")
                .isGreaterThan(apresCroissance);
        robot.interact(fenetre::close);
    }

    /// Étiquette présente dans l'arbre mais **ni visible ni prise en compte** par la mise en page : c'est la
    /// forme qu'ont les révélations des modales (`visible`/`managed` liés à une propriété).
    private static Label etiquetteCachee() {
        Label cache = new Label("la ligne qui paraîtra ensuite");
        cache.setVisible(false);
        cache.setManaged(false);
        return cache;
    }

    /// Ouvre une fenêtre **à part** - jamais le Stage du harnais, partagé entre classes de test - ajustée à
    /// son contenu, comme une modale à son ouverture.
    private static Stage ouvrirAjustee(VBox racine, FxRobot robot) {
        AtomicReference<Stage> fenetre = new AtomicReference<>();
        robot.interact(() -> {
            Stage propre = new Stage();
            propre.setScene(new Scene(racine));
            propre.show();
            fenetre.set(propre);
        });
        WaitForAsyncUtils.waitForFxEvents();
        return fenetre.get();
    }

    private static void reveler(Label cache, SimpleBooleanProperty revelation, FxRobot robot) {
        robot.interact(() -> {
            cache.setVisible(true);
            cache.setManaged(true);
            revelation.set(true);
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static Label[] dixLignes() {
        Label[] lignes = new Label[10];
        for (int i = 0; i < lignes.length; i++) {
            lignes[i] = new Label("ligne " + (i + 1));
        }
        return lignes;
    }
}

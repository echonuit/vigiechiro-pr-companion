package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Contrat de [ApercuFx#enregistrerFenetreSurgissante] (#5861) : l'aperçu est celui de la fenêtre que le
/// produit ouvre, et non d'un contenu reconstruit ailleurs.
@ExtendWith(ApplicationExtension.class)
class ApercuFxFenetreSurgissanteTest {

    private StackPane racine;

    @Start
    void start(Stage stage) {
        racine = new StackPane();
        FenetreAjustable.poser(stage, racine, 320, 260);
        FenetreAjustable.afficher(stage);
    }

    private static long fenetresSurgies() {
        return Window.getWindows().stream()
                .filter(PopupWindow.class::isInstance)
                .filter(Window::isShowing)
                .count();
    }

    @Test
    @DisplayName("le popup du réglage des colonnes est photographié, puis refermé")
    void le_popup_des_colonnes_est_photographie_puis_referme(FxRobot robot, @TempDir Path tmp) throws IOException {
        Path fichier = tmp.resolve("popup.png");
        AtomicBoolean ecrit = new AtomicBoolean();
        robot.interact(() -> {
            TableView<String> table = new TableView<>();
            table.getColumns().add(new TableColumn<>("Espèce"));
            table.getColumns().add(new TableColumn<>("Détections"));
            racine.getChildren().setAll(table);
            ecrit.set(ApercuFx.enregistrerFenetreSurgissante(
                    () -> GestionnaireColonnes.ouvrir(table, GestionnaireColonnes.colonnesParDefaut(table), table),
                    fichier));
        });

        assertThat(ecrit)
                .as("le geste a ouvert un popup, donc l'aperçu est écrit")
                .isTrue();
        Image image = new Image(Files.newInputStream(fichier));
        // Le panneau fixe sa liste à 240 de large : une image plus étroite ne serait pas la sienne.
        assertThat(image.getWidth()).as("l'image a la largeur du panneau").isGreaterThanOrEqualTo(240.0);
        assertThat(image.getHeight()).as("titre, deux lignes et bouton").isGreaterThan(100.0);
        assertThat(fenetresSurgies())
                .as("la capture referme ce qu'elle a ouvert")
                .isZero();
    }

    @Test
    @DisplayName("un popup déjà ouvert avant le geste n'est pas pris pour le sien")
    void un_popup_deja_ouvert_n_est_pas_pris_pour_celui_du_geste(FxRobot robot, @TempDir Path tmp) {
        Path fichier = tmp.resolve("ancien.png");
        AtomicBoolean ecrit = new AtomicBoolean(true);
        Popup ancien = new Popup();
        robot.interact(() -> {
            ancien.getContent().add(new Label("déjà là"));
            ancien.show(racine.getScene().getWindow());
            ecrit.set(ApercuFx.enregistrerFenetreSurgissante(() -> {}, fichier));
        });

        assertThat(ecrit).as("le geste n'a rien ouvert").isFalse();
        assertThat(fichier).doesNotExist();
        assertThat(ancien.isShowing())
                .as("et le popup étranger n'est pas refermé")
                .isTrue();
        robot.interact(ancien::hide);
    }

    @Test
    @DisplayName("un geste qui n'ouvre rien ne produit aucun aperçu")
    void un_geste_qui_n_ouvre_rien_ne_produit_aucun_apercu(FxRobot robot, @TempDir Path tmp) {
        Path fichier = tmp.resolve("rien.png");
        AtomicBoolean ecrit = new AtomicBoolean(true);
        robot.interact(() -> ecrit.set(ApercuFx.enregistrerFenetreSurgissante(() -> {}, fichier)));

        assertThat(ecrit).as("rien ne s'est ouvert").isFalse();
        assertThat(fichier).as("et rien n'est écrit").doesNotExist();
    }
}

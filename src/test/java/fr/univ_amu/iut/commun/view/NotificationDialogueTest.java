package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.Alert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

/// L'adaptateur qui **rend** un compte rendu à l'écran (#3148).
///
/// Il n'avait aucun test, parce qu'il ne savait qu'ouvrir un dialogue : un `showAndWait` fige un test
/// headless, et rien ne pouvait donc observer ce qu'il fabrique. Il décide pourtant de quelque chose
/// qui se voit, le **type** du dialogue, d'après le niveau du compte rendu : un avertissement doit se
/// distinguer d'une information au premier coup d'œil, sans quoi « votre nuit a changé de disque » se
/// lit comme « c'est fait ».
///
/// Séparer la fabrication de l'ouverture rend ce choix observable, et rend l'aperçu documentaire
/// possible. L'`Alert` se construit sur le fil JavaFX, d'où le `robot.interact` ; on ne l'affiche
/// jamais.
@ExtendWith(ApplicationExtension.class)
class NotificationDialogueTest {

    @Test
    @DisplayName("un avertissement est rendu comme un avertissement, pas comme une information")
    void avertissement_rendu_comme_tel(FxRobot robot) {
        Alert alerte =
                fabriquer(robot, NiveauNotification.AVERTISSEMENT, "Sauvegarde incomplète", "Une racine manque.");

        assertThat(alerte.getAlertType())
                .as("noyer un avertissement dans le pictogramme d'une information, c'est le taire")
                .isEqualTo(Alert.AlertType.WARNING);
    }

    @Test
    @DisplayName("une information reste une information")
    void information_rendue_comme_telle(FxRobot robot) {
        Alert alerte = fabriquer(robot, NiveauNotification.INFORMATION, "Sauvegarde créée", "Tout est là.");

        assertThat(alerte.getAlertType()).isEqualTo(Alert.AlertType.INFORMATION);
    }

    @Test
    @DisplayName("l'en-tête et le message arrivent tels qu'on les a donnés")
    void entete_et_message_portes_tels_quels(FxRobot robot) {
        Alert alerte = fabriquer(
                robot, NiveauNotification.AVERTISSEMENT, "Sauvegarde restaurée", "2 nuits ont changé de place.");

        assertThat(alerte.getHeaderText()).isEqualTo("Sauvegarde restaurée");
        assertThat(alerte.getContentText())
                .as("c'est ce texte que l'utilisateur lit : il ne doit être ni tronqué ni reformulé ici")
                .isEqualTo("2 nuits ont changé de place.");
    }

    private static Alert fabriquer(FxRobot robot, NiveauNotification niveau, String entete, String message) {
        AtomicReference<Alert> rendu = new AtomicReference<>();
        robot.interact(() -> rendu.set(NotificationDialogue.sansProprietaire().dialogue(niveau, entete, message)));
        return rendu.get();
    }
}

package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// [InspectionConclue#attendre] attend-elle quelque chose (#6002) ?
///
/// L'attente qu'elle remplace lisait un libellé qui n'est jamais vide, et ne pouvait pas expirer. Ce
/// banc pose l'écran dans l'état où cette attente-là réussissait à tort : la section d'inspection
/// est dans le graphe, **invisible**, et son libellé dit déjà quelque chose.
@ExtendWith(ApplicationExtension.class)
class InspectionConclueTest {

    private static final long COURT_MS = 400;

    private static final long RETARD_MS = 300;

    private static final String SINON = "l'inspection n'a jamais conclu sur ce banc";

    private VBox section;

    @Start
    void start(Stage stage) {
        Label originaux = new Label("0 enregistrement(s) WAV détecté(s)");
        originaux.setId("labelOriginaux");
        section = new VBox(originaux);
        section.setId("sectionInspection");
        section.setVisible(false);
        section.setManaged(false);
        FenetreAjustable.poser(stage, new VBox(new Label("L'assistant"), section), 400, 200);
        FenetreAjustable.afficher(stage);
    }

    @Test
    @DisplayName("#6002 : tant que la section n'a pas paru, l'attente expire, et avec son message")
    void sans_inspection_l_attente_expire_avec_son_message(FxRobot robot) {
        assertThatThrownBy(() -> InspectionConclue.attendre(robot, SINON, COURT_MS))
                .as("le libellé dit déjà « 0 enregistrement(s) » : l'ancienne attente réussissait ici, à"
                        + " son premier sondage. Si celle-ci réussit aussi, elle n'attend rien de plus")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(SINON);
    }

    @Test
    @DisplayName("#6002 : une inspection qui tarde est attendue, et l'attente ne rend la main qu'après elle")
    void une_inspection_qui_tarde_est_attendue(FxRobot robot) {
        AtomicBoolean conclue = new AtomicBoolean();
        CompletableFuture.delayedExecutor(RETARD_MS, TimeUnit.MILLISECONDS)
                .execute(() -> Platform.runLater(() -> {
                    conclue.set(true);
                    section.setManaged(true);
                    section.setVisible(true);
                }));

        InspectionConclue.attendre(robot, SINON, 10 * RETARD_MS);

        assertThat(conclue)
                .as("l'attente a rendu la main : la section doit avoir paru avant, sans quoi elle est"
                        + " revenue sur autre chose que la conclusion")
                .isTrue();
    }
}

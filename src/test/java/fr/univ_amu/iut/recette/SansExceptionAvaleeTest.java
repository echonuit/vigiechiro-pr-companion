package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Ce que [SansExceptionAvalee] attrape, et ce qu il epargne (#4409).
///
/// Un garde qui ne sait que reussir ne garde rien. Ce cas plante une exception dans le fil JavaFX
/// et exige que le garde la voie ; le second exige qu il se taise quand il n y a rien.
@ExtendWith(ApplicationExtension.class)
class SansExceptionAvaleeTest {

    @Start
    void start(Stage stage) {
        FenetreAjustable.poser(stage, new StackPane(), 80, 60);
        FenetreAjustable.afficher(stage);
    }

    @Test
    @DisplayName("une exception morte dans le fil JavaFX fait rougir le cas, avec sa cause")
    void un_rejet_du_fil_fx_fait_rougir() {
        SansExceptionAvalee garde = new SansExceptionAvalee();
        garde.beforeEach(null);

        // `runLater` et non `asyncFx` : ce dernier CAPTURE l exception au lieu de la laisser au
        // gestionnaire du fil, et le temoin passerait alors au vert sans rien avoir eprouve.
        Platform.runLater(() -> {
            throw new IllegalStateException("temoin du garde");
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThatThrownBy(() -> garde.afterEach(null))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("morte dans un gestionnaire du fil JavaFX")
                .hasRootCauseMessage("temoin du garde");
    }

    @Test
    @DisplayName("un cas sans rejet ne rougit pas, et le fil retrouve son gestionnaire")
    void un_cas_sain_reste_vert() {
        Thread.UncaughtExceptionHandler avant = surLeFilFx();

        SansExceptionAvalee garde = new SansExceptionAvalee();
        garde.beforeEach(null);
        WaitForAsyncUtils.waitForFxEvents();
        assertThatCode(() -> garde.afterEach(null)).doesNotThrowAnyException();

        // Le gestionnaire est RENDU : sans cela, la classe suivante porterait celui-ci, et TestFX
        // reutilise le meme fil d une classe a l autre dans un meme fork.
        assertThat(surLeFilFx())
                .as("le gestionnaire du fil JavaFX doit etre celui d avant le cas")
                .isSameAs(avant);
    }

    private static Thread.UncaughtExceptionHandler surLeFilFx() {
        try {
            return WaitForAsyncUtils.asyncFx(() -> Thread.currentThread().getUncaughtExceptionHandler())
                    .get();
        } catch (Exception e) {
            throw new AssertionError("lecture du gestionnaire sur le fil JavaFX", e);
        }
    }
}

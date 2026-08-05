package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;

/// Garde-fou du garde-fou, sur l'**invite** d'un champ de recherche (#3170).
///
/// Le contrôle d'élision d'[ApercuFx] refuse un libellé rendu **avec** une ellipse. Une invite de
/// `TextField` n'en pose aucune : JavaFX la **rogne net**, « Rechercher (espèce, fichier, comm ». Le
/// garde ne la voyait donc pas, et la capture partait avec une invite amputée - qui ne dit plus ce que
/// la recherche couvre, c'est-à-dire tout ce qu'elle avait à dire.
///
/// Le correctif a élargi le contrôle aux `TextInputControl`. **Rien ne le vérifiait** : c'est ce que
/// cette clôture de suites a trouvé, et la raison d'être de ce fichier. Sans lui, retirer le contrôle
/// de l'invite ne casserait rien de visible, et la panne réapparaîtrait sous la forme exacte qu'elle
/// avait déjà prise.
@ExtendWith(ApplicationExtension.class)
class ApercuFxInviteTest {

    /// L'invite réelle de « Sons & validation », celle qui a motivé #3170.
    private static final String INVITE = "Rechercher (espèce, fichier, commentaire…)";

    /// Exécute `capture` **sur le fil JavaFX** et rend ce qu'elle a jeté, ou `null`.
    private static Throwable executerSurLeFilFx(Runnable capture) throws InterruptedException {
        AtomicReference<Throwable> jetee = new AtomicReference<>();
        CountDownLatch fini = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                capture.run();
            } catch (RuntimeException | Error probleme) {
                jetee.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        assertThat(fini.await(30, TimeUnit.SECONDS))
                .as("la capture doit rendre la main")
                .isTrue();
        return jetee.get();
    }

    private static Scene sceneAvecChamp(double largeurChamp) {
        TextField champ = new TextField();
        champ.setPromptText(INVITE);
        champ.setMinWidth(Double.NEGATIVE_INFINITY);
        champ.setPrefWidth(largeurChamp);
        champ.setMaxWidth(largeurChamp);
        return new Scene(new VBox(champ), largeurChamp + 40, 120);
    }

    @Test
    @DisplayName("#3170 : une invite rognée fait échouer la capture, alors qu'aucune ellipse ne la signale")
    void invite_rognee_refuse(@TempDir Path dossier) throws InterruptedException {
        Path fichier = dossier.resolve("apercu.png");

        Throwable refus = executerSurLeFilFx(() -> ApercuFx.enregistrerPng(sceneAvecChamp(140), fichier));

        assertThat(refus)
                .as("c'est précisément le cas que l'ancien contrôle laissait passer")
                .isInstanceOf(IllegalStateException.class);
        assertThat(refus.getMessage()).contains("Rechercher");
        assertThat(fichier)
                .as("une capture dont l'invite est amputée ne doit pas être écrite")
                .doesNotExist();
    }

    @Test
    @DisplayName("#3170 : un champ assez large passe, le contrôle ne crie pas sur un rendu sain")
    void invite_entiere_passe(@TempDir Path dossier) throws InterruptedException {
        // Sans ce second cas, le contrôle pourrait refuser TOUTE invite et le premier resterait vert :
        // un garde qui refuse tout ne garde rien, il bloque.
        Path fichier = dossier.resolve("apercu.png");

        Throwable refus = executerSurLeFilFx(() -> ApercuFx.enregistrerPng(sceneAvecChamp(420), fichier));

        assertThat(refus).isNull();
        assertThat(fichier).exists();
    }
}

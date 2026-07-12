package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

/// Exécution **asynchrone** ([ExecuteurTacheAsynchrone], #1014, production) : le travail tourne hors du
/// fil JavaFX, résultat et erreur sont reprogrammés **sur** le fil JavaFX. [ApplicationExtension]
/// initialise le toolkit ; on attend le fil de fond + `runLater` via [WaitForAsyncUtils].
@ExtendWith(ApplicationExtension.class)
class ExecuteurTacheAsynchroneTest {

    private final ExecuteurTache executeur = new ExecuteurTacheAsynchrone();

    @Test
    @DisplayName("succès : travail hors fil FX, résultat appliqué SUR le fil FX")
    void succes_applique_sur_le_fil_fx() {
        AtomicReference<String> resultat = new AtomicReference<>();
        AtomicBoolean surFilFx = new AtomicBoolean();
        AtomicBoolean travailHorsFilFx = new AtomicBoolean();

        WaitForAsyncUtils.waitForAsyncFx(
                5_000,
                () -> executeur.executer(
                        () -> {
                            travailHorsFilFx.set(!Platform.isFxApplicationThread());
                            return "ok";
                        },
                        valeur -> {
                            resultat.set(valeur);
                            surFilFx.set(Platform.isFxApplicationThread());
                        },
                        erreur -> {}));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(travailHorsFilFx).as("travail exécuté hors du fil JavaFX").isTrue();
        assertThat(resultat.get()).isEqualTo("ok");
        assertThat(surFilFx).as("callback de succès sur le fil JavaFX").isTrue();
    }

    @Test
    @DisplayName("échec : l'exception du travail est remise à `echec` sur le fil FX")
    void echec_applique_sur_le_fil_fx() {
        AtomicReference<Throwable> capturee = new AtomicReference<>();
        AtomicBoolean surFilFx = new AtomicBoolean();
        RuntimeException panne = new IllegalStateException("réseau coupé");

        WaitForAsyncUtils.waitForAsyncFx(
                5_000,
                () -> executeur.executer(
                        () -> {
                            throw panne;
                        },
                        valeur -> {},
                        erreur -> {
                            capturee.set(erreur);
                            surFilFx.set(Platform.isFxApplicationThread());
                        }));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(capturee.get()).isSameAs(panne);
        assertThat(surFilFx).as("callback d'erreur sur le fil JavaFX").isTrue();
    }
}

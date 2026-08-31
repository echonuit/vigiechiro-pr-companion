package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.recette.Attente;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

/// Exécution **asynchrone** ([ExecuteurTacheAsynchrone], #1014, production) : le travail tourne hors du
/// fil JavaFX, résultat et erreur sont reprogrammés **sur** le fil JavaFX. [ApplicationExtension]
/// initialise le toolkit ; on attend le fil de fond + `runLater` via [WaitForAsyncUtils]. Les
/// extensions #1252 (progression, annulation coopérative) reviennent elles aussi sur le fil JavaFX.
@ExtendWith(ApplicationExtension.class)
class ExecuteurTacheAsynchroneTest {

    private final ExecuteurTache executeur = new ExecuteurTacheAsynchrone();

    @Test
    @DisplayName("succès : travail hors fil FX, résultat appliqué SUR le fil FX")
    void succes_applique_sur_le_fil_fx() {
        AtomicReference<String> resultat = new AtomicReference<>();
        AtomicBoolean surFilFx = new AtomicBoolean();
        AtomicBoolean travailHorsFilFx = new AtomicBoolean();

        Attente.surLeFil(
                () -> executeur.executer(
                        () -> {
                            travailHorsFilFx.set(!Platform.isFxApplicationThread());
                            return "ok";
                        },
                        valeur -> {
                            resultat.set(valeur);
                            surFilFx.set(Platform.isFxApplicationThread());
                        },
                        erreur -> {}),
                "que l'exécuteur applique le succès sur le fil",
                5_000L);
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

        Attente.surLeFil(
                () -> executeur.executer(
                        () -> {
                            throw panne;
                        },
                        valeur -> {},
                        erreur -> {
                            capturee.set(erreur);
                            surFilFx.set(Platform.isFxApplicationThread());
                        }),
                "que l'exécuteur applique l'échec sur le fil",
                5_000L);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(capturee.get()).isSameAs(panne);
        assertThat(surFilFx).as("callback d'erreur sur le fil JavaFX").isTrue();
    }

    @Test
    @DisplayName("#1252 : la progression émise hors fil est appliquée SUR le fil FX, dans l'ordre")
    void progression_appliquee_sur_le_fil_fx() {
        List<Progression> points = new CopyOnWriteArrayList<>();
        AtomicBoolean toutSurFilFx = new AtomicBoolean(true);
        Consumer<Progression> relais = executeur.relaisProgression(point -> {
            toutSurFilFx.compareAndSet(true, Platform.isFxApplicationThread());
            points.add(point);
        });

        Attente.surLeFil(
                () -> executeur.executer(
                        () -> {
                            relais.accept(new Progression("1/2", 0.5));
                            relais.accept(new Progression("2/2", 1.0));
                            return "fini";
                        },
                        valeur -> {},
                        erreur -> {}),
                "que l'exécuteur relaie sa progression sur le fil",
                5_000L);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(points).extracting(Progression::libelle).containsExactly("1/2", "2/2");
        assertThat(toutSurFilFx).as("chaque point appliqué sur le fil JavaFX").isTrue();
    }

    @Test
    @DisplayName("#1252 : une annulation coopérative conclut par `annule` sur le fil FX, pas par `echec`")
    void annulation_conclut_par_annule_sur_le_fil_fx() {
        AtomicBoolean annuleSurFilFx = new AtomicBoolean();
        AtomicReference<Throwable> echec = new AtomicReference<>();
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler();

        Attente.surLeFil(
                () -> executeur.executer(
                        () -> {
                            jeton.leverSiAnnule();
                            return "jamais atteint";
                        },
                        valeur -> {},
                        () -> annuleSurFilFx.set(Platform.isFxApplicationThread()),
                        echec::set),
                "que l'exécuteur applique l'annulation sur le fil",
                5_000L);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(annuleSurFilFx).as("callback d'annulation sur le fil JavaFX").isTrue();
        assertThat(echec.get()).as("une annulation n'est pas un échec").isNull();
    }
}

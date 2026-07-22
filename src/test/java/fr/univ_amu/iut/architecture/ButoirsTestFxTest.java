package fr.univ_amu.iut.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;

/// Les **coupe-circuits d'interblocage de TestFX** sont bien ceux que le `pom.xml` a calibrés, et ils
/// atteignent bien la JVM forkée.
///
/// **Ce qu'ils sont.** `FxToolkit` borne deux attentes : le démarrage du toolkit JavaFX
/// (`testfx.launch.timeout`) et la mise en place d'un test, c'est-à-dire l'exécution de la méthode
/// `@Start` par `ApplicationExtension.beforeEach` (`testfx.setup.timeout`). Leur seul rôle est
/// d'empêcher un fil FX bloqué de figer le build indéfiniment. Ils ne mesurent rien.
///
/// **Pourquoi ce garde-fou existe (#2120).** Leurs valeurs par défaut (30 s et 60 s) supposent une JVM
/// seule sur sa machine. Le dépôt lance une JVM par coeur sur un runner partagé, et la mise en place la
/// plus lente de la suite coûte 6,9 s quand la machine est au repos : la marge n'était que de 4,3x,
/// alors que la durée mesurée du job `build` varie d'un facteur 5,4 d'un run à l'autre. Le butoir était
/// **dans le bruit de la machine**, et expirait par intermittence sans qu'aucune assertion ne soit
/// fausse. Un rouge que l'on apprend à écarter finit par couvrir celui qui compte.
///
/// **Ce que ce test attrape.** Les deux valeurs sont posées par `systemPropertyVariables` : elles ne
/// s'appliquent qu'à la JVM **forkée** par Surefire, pas à celle de Maven. Une ligne retirée du
/// `pom.xml`, un profil qui redéfinit `systemPropertyVariables` sans les reprendre, et TestFX retombe
/// **en silence** sur ses défauts : rien ne rougit, jusqu'au prochain runner lent. Ce test lit la valeur
/// que TestFX appliquera réellement, et non la propriété que l'on croit avoir posée.
///
/// Il vit avec les règles d'architecture parce qu'il est de la même famille : une contrainte que le
/// dépôt s'impose à lui-même et que personne ne verrait disparaître autrement.
class ButoirsTestFxTest {

    /// Plancher calibré dans le `pom.xml` : 17x la mise en place la plus lente mesurée, soit 3x au-delà
    /// de la pire dégradation de runner observée. On vérifie un **plancher**, pas une égalité : relever
    /// un butoir reste libre, le laisser retomber sous ce seuil ne l'est pas.
    private static final long PLANCHER_MILLIS = 120_000L;

    @Test
    @DisplayName("le butoir de mise en place d'un test dépasse le plancher calibré")
    void butoir_de_mise_en_place() {
        assertThat(FxToolkit.toolkitContext().getSetupTimeoutInMillis())
                .as("testfx.setup.timeout n'atteint pas la JVM forkée : TestFX applique son défaut de"
                        + " 30 s, qui expire dès qu'un runner ralentit (#2120)")
                .isGreaterThanOrEqualTo(PLANCHER_MILLIS);
    }

    @Test
    @DisplayName("le butoir de démarrage du toolkit dépasse le plancher calibré")
    void butoir_de_demarrage_du_toolkit() {
        assertThat(FxToolkit.toolkitContext().getLaunchTimeoutInMillis())
                .as("testfx.launch.timeout n'atteint pas la JVM forkée : TestFX applique son défaut"
                        + " de 60 s (#2120)")
                .isGreaterThanOrEqualTo(PLANCHER_MILLIS);
    }
}

package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Garde la seule chose qui compte vraiment sur [AttenteTuiles] : **qu'elle rende la main**.
///
/// ## Pourquoi ce test existe
///
/// En corrigeant #3068, j'ai d'abord rythmé l'observation par une `Timeline`, au motif - correct - que
/// le graphe de scène n'est pas thread-safe et ne doit pas être lu depuis un fil de veille.
///
/// Sous la **Headless Platform**, une `Timeline` ne tique **jamais** : le fil JavaFX est bloqué dans la
/// boucle d'évènements imbriquée et aucune pulsation ne le réveille. `exitNestedEventLoop` n'était donc
/// jamais appelé. **Interblocage franc** : quarante minutes sans qu'une seule capture se termine.
///
/// Rien ne l'aurait signalé. Une capture qui ne rend pas la main ne rougit pas, elle **pend** - et en CI
/// cela se lirait comme un job lent avant de se lire comme une panne.
@ExtendWith(ApplicationExtension.class)
class AttenteTuilesTest {

    @Start
    void start(Stage stage) {
        FenetreAjustable.poser(stage, new StackPane(), 200, 200);
        FenetreAjustable.afficher(stage);
    }

    @Test
    @DisplayName("#3068 : sans image à attendre, l'attente rend la main vite, et la rend")
    void rend_la_main(FxRobot robot) {
        Instant debut = Instant.now();

        // Sur le fil JavaFX, comme le veut le contrat : c'est là que la boucle imbriquée s'ouvre.
        robot.interact(AttenteTuiles::attendre);

        Duration duree = Duration.between(debut, Instant.now());
        assertThat(duree)
                .as("l'attente a rendu la main, ce que la version à Timeline ne faisait plus")
                .isLessThan(Duration.ofSeconds(15));
        assertThat(duree)
                .as("aucune image à charger : la stabilité est atteinte en quelques pas, pas au plafond "
                        + "de 20 s - sinon la condition ne sert à rien et on est revenu à un délai fixe")
                .isLessThan(Duration.ofSeconds(5));
    }
}

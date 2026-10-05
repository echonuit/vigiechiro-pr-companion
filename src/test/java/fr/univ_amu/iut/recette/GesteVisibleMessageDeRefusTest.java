package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import java.util.concurrent.Callable;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le refus d'un geste dit-il UNE fois sur quel fil il attendait, ou deux (#5878) ?
///
/// [Attente#surLeFil] compose « n'a pas pu <ce qu'on faisait> sur le fil JavaFX en <n> ms », et les
/// trois surcharges de `pointSurLeFil` passaient un libellé qui portait déjà la phrase.
///
/// **Aucun cas n'attend l'expiration** : `surLeFil` attrape `Throwable`, et chaque entrée est forcée
/// à lever DANS sa callable - sélecteur introuvable, nœud sans scène, prédicat qui ne reconnaît rien.
/// Un cas par surcharge, parce que trois copies exactes ne rougissent jamais ensemble.
@ExtendWith(ApplicationExtension.class)
class GesteVisibleMessageDeRefusTest {

    /// La phrase qu'[Attente#surLeFil] ajoute elle-même, et qu'un libellé ne doit donc pas porter.
    private static final String PHRASE = "sur le fil JavaFX";

    private Button bouton;

    @Start
    void start(Stage stage) {
        bouton = new Button("ici");
        bouton.setId("bouton");
        FenetreAjustable.poser(stage, new VBox(bouton), 400, 200);
        stage.show();
    }

    @Test
    @DisplayName("#5878 : le refus d'un clic PAR SÉLECTEUR ne dit qu'une fois sur quel fil")
    void le_refus_par_selecteur_ne_se_repete_pas(FxRobot robot) {
        Throwable refus = catchThrowable(() -> GesteVisible.cliquer(robot, "#nexistePas"));

        assertThat(refus).isInstanceOf(AssertionError.class);
        assertThat(combienDeFois(refus.getMessage()))
                .as("le message doit porter la phrase une fois, celle que `surLeFil` ajoute : %s", refus.getMessage())
                .isEqualTo(1);
        assertThat(refus.getMessage())
                .as("et il doit toujours nommer ce qu'on cherchait, sans quoi la correction aurait pris l'information")
                .contains("#nexistePas");
    }

    @Test
    @DisplayName("#5878 : le refus d'un clic sur un NŒUD EN MAIN ne dit qu'une fois sur quel fil")
    void le_refus_sur_un_noeud_en_main_ne_se_repete_pas(FxRobot robot) {
        // Un bouton qu'on n'a jamais posé n'a pas de scène, et `robot.point(Node)` lit les bornes
        // dans la fenêtre de sa scène : la lecture lève, DANS la callable, sans rien attendre.
        Callable<Button> creation = () -> new Button("jamais posé");
        Button orphelin = Attente.surLeFil(creation, "créer un bouton hors de toute scène", 5_000);

        Throwable refus = catchThrowable(() -> GesteVisible.cliquer(robot, orphelin));

        assertThat(refus).isInstanceOf(AssertionError.class);
        assertThat(combienDeFois(refus.getMessage()))
                .as("le message doit porter la phrase une fois : %s", refus.getMessage())
                .isEqualTo(1);
        assertThat(refus.getMessage())
                .as("et il doit toujours dire de quel genre de cible il parlait")
                .contains("nœud en main");
    }

    @Test
    @DisplayName("#5878 : le refus d'un clic PAR PRÉDICAT ne dit qu'une fois sur quel fil")
    void le_refus_par_predicat_ne_se_repete_pas(FxRobot robot) {
        Throwable refus = catchThrowable(
                () -> GesteVisible.cliquerLaCible(robot, noeud -> false, "la carte du site introuvable"));

        assertThat(refus).isInstanceOf(AssertionError.class);
        assertThat(combienDeFois(refus.getMessage()))
                .as("le message doit porter la phrase une fois : %s", refus.getMessage())
                .isEqualTo(1);
        assertThat(refus.getMessage())
                .as("et il doit reprendre ce que le banc disait chercher, qui est l'objet de cette surcharge")
                .contains("la carte du site introuvable");
    }

    /// Combien de fois le message porte [#PHRASE]. Deux valent le défaut de #5878, une vaut le remède.
    private static int combienDeFois(String message) {
        int compte = 0;
        for (int i = message.indexOf(PHRASE); i >= 0; i = message.indexOf(PHRASE, i + PHRASE.length())) {
            compte++;
        }
        return compte;
    }
}

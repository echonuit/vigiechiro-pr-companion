package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// [GesteVisible#amenerDansLeCadre] fait-il défiler le panneau dont la cible **descend** (#4778) ?
///
/// Il prenait le premier `.scroll-pane` du graphe. L'écran de vérification en porte trois - celui du
/// chrome, celui que sa mise en page pose, celui d'un champ de texte - et rien ne dit lequel un
/// `lookup` rend.
///
/// ## Pourquoi ce cas se juge sur le CLIC et non sur la visibilité
///
/// `NodeQueryUtils.isVisible()` vérifie que les bornes coupent le rectangle de la scène. Il **ignore
/// le rognage** d'un `ScrollPane`. Mesuré sur les 25 combinaisons de défilement de ce banc : six le
/// disent « dans le cadre », **une seule** laisse le clic atteindre la cible. Les cinq autres
/// acceptent le clic et ne déclenchent rien.
///
/// Un banc qui croit avoir agi est pire qu'un banc qui échoue : rien ne proteste, et le clip montre un
/// geste qui n'a pas eu lieu.
@ExtendWith(ApplicationExtension.class)
class GesteVisibleCadreImbriqueTest {

    private static final double REMPLISSAGE = 1200;

    private static final double LARGEUR = 600;

    private static final double HAUTEUR = 400;

    private static final double HAUTEUR_INTERIEURE = 300;

    private ScrollPane exterieur;

    private ScrollPane interieur;

    private Button cible;

    @Start
    void start(Stage stage) {
        cible = new Button("Au fond");
        cible.setId("cible");
        interieur = new ScrollPane(new VBox(remplissage(), cible, remplissage()));
        interieur.setPrefViewportHeight(HAUTEUR_INTERIEURE);
        interieur.setMinHeight(HAUTEUR_INTERIEURE);
        interieur.setMaxHeight(HAUTEUR_INTERIEURE);
        exterieur = new ScrollPane(new VBox(remplissage(), interieur, remplissage()));
        exterieur.setPrefViewportHeight(HAUTEUR);
        FenetreAjustable.poser(stage, exterieur, LARGEUR, HAUTEUR);
        FenetreAjustable.afficher(stage);
    }

    private static Region remplissage() {
        Region region = new Region();
        region.setMinHeight(REMPLISSAGE);
        region.setPrefHeight(REMPLISSAGE);
        return region;
    }

    @Test
    @DisplayName("#4778 : la cible au fond de deux panneaux est réellement CLIQUABLE après le geste")
    void la_cible_au_fond_de_panneaux_imbriques_est_cliquable(FxRobot robot) {
        AtomicInteger declenchements = new AtomicInteger();
        robot.interact(() -> {
            exterieur.setVvalue(0);
            interieur.setVvalue(0);
            cible.setOnAction(evenement -> declenchements.incrementAndGet());
        });

        GesteVisible.amenerDansLeCadre(robot, "#cible");
        robot.clickOn("#cible");

        assertThat(declenchements.get())
                .as("le clic DÉCLENCHE la cible. Ne défiler que le panneau extérieur la laisse rognée"
                        + " par l'intérieur : TestFX la voit alors dans la scène et accepte le clic, qui"
                        + " part dans le vide")
                .isEqualTo(1);
    }
}

package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
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
import org.testfx.util.NodeQueryUtils;

/// [GesteVisible#amenerDansLeCadre] tient-il ce qu'il annonce (#4723) ?
///
/// Son en-tête promet de défiler « **jusqu'à ce que** `selecteur` soit dans le cadre ». Il ne faisait
/// qu'une passe, calculée sur les bornes de l'instant : quand l'écran vient de paraître, elles ne sont
/// pas encore établies, le panneau s'arrête à mi-course et le clic suivant est refusé.
///
/// ## Pourquoi un saboteur plutôt que la vraie course
///
/// Le défaut réel ne se reproduit que **sous charge** - trois rouges en intégration sur la suite
/// entière, une dizaine de relances isolées vertes. Un garde qui en dépendrait ne garderait rien.
///
/// Le saboteur remet donc `vvalue` à zéro **une seule fois**, ce qui reproduit exactement la
/// conséquence : la première passe ne porte pas. Ce que le garde vérifie n'est pas la course, c'est la
/// promesse - après l'appel, la cible EST dans le cadre.
@ExtendWith(ApplicationExtension.class)
class GesteVisibleCadreTest {

    /// La cible est ENCADRÉE de remplissage : dernière, `vvalue` saturerait à 1 et une passe unique
    /// suffirait toujours. Mesuré en montant ce banc.
    private static final double REMPLISSAGE = 1500;

    private static final double LARGEUR = 600;

    private static final double HAUTEUR = 400;

    private ScrollPane pane;

    @Start
    void start(Stage stage) {
        Button cible = new Button("La cible");
        cible.setId("cible");
        pane = new ScrollPane(new VBox(remplissage(), cible, remplissage()));
        pane.setPrefViewportHeight(HAUTEUR);
        // L'idiome de l'ADR 4475 : la fenêtre tient la taille demandée sans se figer. Un stage hérité
        // du fork mesure 900 x 600, et un nœud pose alors hors du cadre pour une raison qui n'est pas
        // celle qu'on éprouve ici.
        FenetreAjustable.poser(stage, pane, LARGEUR, HAUTEUR);
        FenetreAjustable.afficher(stage);
    }

    private static Region remplissage() {
        Region region = new Region();
        region.setMinHeight(REMPLISSAGE);
        region.setPrefHeight(REMPLISSAGE);
        return region;
    }

    @Test
    @DisplayName("#4723 : une passe qui ne porte pas est REJOUÉE, et la cible finit dans le cadre")
    void le_defilement_se_rejoue_jusqu_a_ce_que_la_cible_soit_atteignable(FxRobot robot) {
        AtomicInteger sabotages = poserUnSaboteur(robot, 1);

        GesteVisible.amenerDansLeCadre(robot, "#cible");

        assertThat(sabotages.get())
                .as("le saboteur a bien annulé une passe : sans cela ce cas ne prouverait rien, une"
                        + " passe unique suffisant quand elle porte du premier coup")
                .isEqualTo(1);

        assertThat(estDansLeCadre(robot))
                .as("et la cible est dans le cadre malgré cela. Une passe unique laisse le panneau à"
                        + " mi-course, et TestFX refuse ensuite le clic - « no nodes were visible » -"
                        + " message qui se lit comme une absence alors que c'est un hors-cadre")
                .isTrue();
    }

    @Test
    @DisplayName("#4723 : une cible qui ne vient JAMAIS dans le cadre est dénoncée, pas tue")
    void une_cible_qui_ne_vient_jamais_est_denoncee(FxRobot robot) {
        poserUnSaboteur(robot, Integer.MAX_VALUE);

        assertThatThrownBy(() -> GesteVisible.amenerDansLeCadre(robot, "#cible"))
                .as("rendre la main sans avoir amené la cible reporte l'échec sur le clic suivant, qui"
                        + " l'annoncera comme une absence de nœud. Le geste doit dire lui-même qu'il n'a"
                        + " pas tenu, et NOMMER ce qu'il n'a pas pu amener")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("#cible");
    }

    /// Un saboteur qui remet le panneau à zéro les `coups` premières fois qu'il bouge.
    private AtomicInteger poserUnSaboteur(FxRobot robot, int coups) {
        AtomicInteger faits = new AtomicInteger();
        ChangeListener<Number> saboteur = new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number avant, Number apres) {
                if (faits.get() < coups && apres.doubleValue() != 0) {
                    faits.incrementAndGet();
                    pane.setVvalue(0);
                }
            }
        };
        robot.interact(() -> {
            pane.setVvalue(0);
            pane.vvalueProperty().addListener(saboteur);
        });
        return faits;
    }

    private static boolean estDansLeCadre(FxRobot robot) {
        Node cible = robot.lookup("#cible").query();
        return NodeQueryUtils.isVisible().test(cible);
    }
}

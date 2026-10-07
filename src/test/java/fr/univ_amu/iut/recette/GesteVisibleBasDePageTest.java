package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import javafx.scene.control.Label;
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
import org.testfx.util.WaitForAsyncUtils;

/// [GesteVisible#allerAuBasDeLaPage] fixe-t-il la dernière image d'un clip (#5870) ?
///
/// Deux tournages du même commit rendaient le clip de `S4-47` à 20 % d'écart. Le contenu était le
/// même, la page n'était pas défilée au même endroit. Le scénario amenait la carte du traitement dans
/// le cadre **avant** qu'elle ait pris sa hauteur finale, et JavaFX garde alors le décalage en pixels :
/// quand la carte grandit, la page reste là où elle était, en deçà de son nouveau bas.
///
/// ## Ce que ce banc reproduit, et ce qu'il ne reproduit pas
///
/// La course elle-même ne se reproduit pas à la demande : cinq tournages sur six étaient tombés du
/// même côté. Le premier cas en fabrique donc la **conséquence** dans l'ordre fautif, en faisant
/// grandir la carte après l'avoir amenée. C'est le témoin : sans lui, le second cas passerait aussi
/// sur un banc où rien ne bouge.
@ExtendWith(ApplicationExtension.class)
class GesteVisibleBasDePageTest {

    private static final double REMPLISSAGE = 1500;

    private static final double LARGEUR = 600;

    private static final double HAUTEUR = 400;

    /// De quoi la carte grandit quand son état arrive. Douze pixels suffisaient au défaut réel.
    private static final double CROISSANCE = 60;

    private ScrollPane pane;

    private VBox carte;

    @Start
    void start(Stage stage) {
        carte = new VBox(new Label("La carte du verdict"));
        carte.setId("carte");
        // La carte est le DERNIER élément de sa page, comme le compte rendu d'un import ou la carte
        // « Traitement Vigie-Chiro » : c'est la condition d'emploi de l'aide.
        pane = new ScrollPane(new VBox(remplissage(REMPLISSAGE), carte));
        pane.setPrefViewportHeight(HAUTEUR);
        FenetreAjustable.poser(stage, pane, LARGEUR, HAUTEUR);
        FenetreAjustable.afficher(stage);
    }

    private static Region remplissage(double hauteur) {
        Region region = new Region();
        region.setMinHeight(hauteur);
        region.setPrefHeight(hauteur);
        return region;
    }

    @Test
    @DisplayName("#5870 : amenée dans le cadre PUIS agrandie, la carte laisse la page en deçà de son bas")
    void le_defaut_d_origine_se_reproduit(FxRobot robot) {
        GesteVisible.amenerDansLeCadre(robot, "#carte");
        faireGrandirLaCarte(robot);

        assertThat(ecartAuBas())
                .as("c'est le défaut : JavaFX garde le décalage en pixels quand le contenu grandit. Si"
                        + " cette attente tombe, le banc ne reproduit plus rien et le cas suivant ne"
                        + " prouve plus que l'aide est nécessaire")
                .isGreaterThan(1.0);
    }

    @Test
    @DisplayName("#5870 : appelée une fois la carte agrandie, l'aide cale la page sur son bas")
    void l_aide_cale_la_page_sur_son_bas(FxRobot robot) {
        GesteVisible.amenerDansLeCadre(robot, "#carte");
        faireGrandirLaCarte(robot);

        GesteVisible.allerAuBasDeLaPage(robot, "#carte");

        assertThat(ecartAuBas())
                .as("la page est à son bas, au pixel près : c'est ce qui rend la dernière image d'un"
                        + " clip indépendante de l'instant où la carte a grandi")
                .isLessThan(0.5);
    }

    @Test
    @DisplayName("#5870 : une cible qui n'est pas au bas de sa page est dénoncée, pas laissée hors cadre")
    void une_cible_qui_n_est_pas_au_bas_est_denoncee(FxRobot robot) {
        robot.interact(() -> ((VBox) pane.getContent()).getChildren().add(remplissage(REMPLISSAGE)));
        WaitForAsyncUtils.waitForFxEvents();

        assertThatThrownBy(() -> GesteVisible.allerAuBasDeLaPage(robot, "#carte"))
                .as("l'aide ne vaut que pour le dernier élément d'une page. Employée ailleurs, elle"
                        + " mettrait le verdict HORS du cadre en ayant l'air de l'y amener, et le clip"
                        + " finirait sur autre chose")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("#carte")
                .hasMessageContaining("dernier élément");
    }

    @Test
    @DisplayName("#5982 : une cible qui ne descend d'aucun panneau de défilement est refusée, pas déclarée calée")
    void une_cible_sans_panneau_est_refusee(FxRobot robot) {
        robot.interact(() -> {
            Label seule = new Label("Hors de tout panneau de défilement");
            seule.setId("seule");
            pane.getScene().setRoot(new VBox(seule));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThatThrownBy(() -> GesteVisible.allerAuBasDeLaPage(robot, "#seule"))
                .as("la cible est dans le cadre et aucune page ne la porte : il n'y a rien à caler. Rendre"
                        + " un succès ici, c'était « tous les panneaux sont au bas » dit d'une liste vide")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("#seule")
                .hasMessageContaining("aucun panneau de défilement");
    }

    private void faireGrandirLaCarte(FxRobot robot) {
        robot.interact(() -> carte.getChildren().add(remplissage(CROISSANCE)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// De combien de pixels la page est en deçà de son bas. Zéro quand elle y est.
    private double ecartAuBas() {
        return Attente.surLeFil(
                () -> {
                    double course = pane.getContent().getBoundsInLocal().getHeight()
                            - pane.getViewportBounds().getHeight();
                    double plage = pane.getVmax() - pane.getVmin();
                    return (pane.getVmax() - pane.getVvalue()) / plage * course;
                },
                "lire la position de la page",
                5_000L);
    }
}

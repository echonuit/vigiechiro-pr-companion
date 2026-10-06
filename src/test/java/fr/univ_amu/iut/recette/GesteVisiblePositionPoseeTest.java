package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import javafx.application.Platform;
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

/// [GesteVisible#poserDansLeCadre] fixe-t-il la dernière image d'un clip (#6069) ?
///
/// Deux tournages sur douze du même commit rendaient le clip de `S2-48` la page en haut, à 21 % des
/// dix autres. Le cas finissait par [GesteVisible#amenerDansLeCadre], juste après que l'inspection
/// rend la section visible.
///
/// ## Ce que ce banc reproduit, et ce qu'il ne reproduit pas
///
/// Il reproduit le **geste** dans l'ordre fautif : la section paraît, et le geste part avant la mise
/// en page qui la place. Le premier cas en est le témoin, et il est sûr : la section paraît dans un
/// `Platform.runLater` que rien ne sépare du premier `interact` du geste.
///
/// Il ne reproduit pas la **course** du cas réel, dont la fenêtre est d'une pulsation : vingt-deux
/// passes filmées sur un poste ne l'ont pas montrée une fois. Que cet ordre soit bien celui des deux
/// tournages n'est donc pas observé ; c'est ce que leurs images et le code laissent conclure.
@ExtendWith(ApplicationExtension.class)
class GesteVisiblePositionPoseeTest {

    private static final double LARGEUR = 600;

    private static final double HAUTEUR = 400;

    /// Ce qui précède la section. Avec ce qui la suit, le contenu tient dans le cadre tant que la
    /// section est absente, et le déborde dès qu'elle paraît : c'est la géométrie de l'écran d'import.
    private static final double AU_DESSUS = 150;

    private static final double SECTION = 120;

    private static final double AU_DESSOUS = 200;

    /// De quoi le contenu grandit après le geste, dans le cas qui éprouve le prédicat.
    private static final double CROISSANCE = 60;

    private ScrollPane pane;

    private VBox section;

    @Start
    void start(Stage stage) {
        section = new VBox(remplissage(SECTION));
        section.setId("section");
        section.setVisible(false);
        section.setManaged(false);
        pane = new ScrollPane(new VBox(remplissage(AU_DESSUS), section, remplissage(AU_DESSOUS)));
        pane.setId("panneau");
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
    @DisplayName("#6069 : amenée avant la mise en page qui la fait paraître, la section laisse la page en haut")
    void le_defaut_d_origine_se_reproduit(FxRobot robot) {
        faireParaitreLaSectionSansAttendre();
        GesteVisible.amenerDansLeCadre(robot, "#section");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(ecartALaPlaceVoulue())
                .as("c'est le défaut : le geste a réglé la page sur les bornes d'avant, puis a conclu"
                        + " parce que la section est dans le cadre. Si cette attente tombe, le banc ne"
                        + " reproduit plus rien et le cas suivant ne prouve plus que le geste neuf est"
                        + " nécessaire")
                .isGreaterThan(1.0);
        assertThat(GesteVisible.estPoseDansLeCadre(robot, "#section"))
                .as("et le prédicat le voit : la section est dans le cadre, la page n'est pas posée")
                .isFalse();
    }

    @Test
    @DisplayName("#6069 : dans le même ordre, le geste qui pose met la page à sa place")
    void le_geste_pose_la_page_meme_avant_la_mise_en_page(FxRobot robot) {
        faireParaitreLaSectionSansAttendre();
        GesteVisible.poserDansLeCadre(robot, "#section");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(ecartALaPlaceVoulue())
                .as("la page est à la place que le réglage lui donne, au pixel près : c'est ce qui rend"
                        + " la dernière image d'un clip indépendante de l'instant où la section a paru")
                .isLessThan(0.5);
        assertThat(GesteVisible.estPoseDansLeCadre(robot, "#section"))
                .as("et le prédicat le confirme")
                .isTrue();
    }

    @Test
    @DisplayName("#6069 : le contrôle, la section déjà mise en page, l'ancien geste pose aussi la page")
    void deja_mise_en_page_l_ancien_geste_suffit(FxRobot robot) {
        faireParaitreLaSection(robot);
        GesteVisible.amenerDansLeCadre(robot, "#section");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(ecartALaPlaceVoulue())
                .as("hors de l'ordre fautif, l'ancien geste met la page au même endroit : le premier"
                        + " cas tient donc à l'ORDRE, et non à la géométrie de ce banc")
                .isLessThan(0.5);
    }

    @Test
    @DisplayName("#6069 : une page qui bouge après le geste n'est plus posée, et le prédicat le dit")
    void une_page_qui_bouge_apres_le_geste_n_est_plus_posee(FxRobot robot) {
        faireParaitreLaSection(robot);
        GesteVisible.poserDansLeCadre(robot, "#section");

        robot.interact(() -> ((VBox) pane.getContent()).getChildren().add(remplissage(CROISSANCE)));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(GesteVisible.estPoseDansLeCadre(robot, "#section"))
                .as("le contenu a grandi sous la section, et JavaFX a gardé le décalage en pixels : la"
                        + " section est toujours dans le cadre, la page n'est plus où le geste l'avait"
                        + " mise. Une assertion de fin qui ne lirait que la présence dans le cadre"
                        + " resterait verte")
                .isFalse();
    }

    @Test
    @DisplayName("#6069 : une cible qui ne descend d'aucun panneau est refusée, pas posée en silence")
    void une_cible_sans_panneau_est_refusee(FxRobot robot) {
        assertThatThrownBy(() -> GesteVisible.poserDansLeCadre(robot, "#panneau"))
                .as("il n'y a aucune position à poser pour un nœud que rien ne fait défiler. Réussir"
                        + " quand même ferait lire « posé » là où rien n'a été réglé")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("#panneau")
                .hasMessageContaining("aucun panneau de défilement");
    }

    /// La section paraît, et RIEN ne sépare ce changement du geste qui suit : ni attente, ni
    /// pulsation. `robot.interact` en laisserait passer une, et le défaut ne se verrait plus.
    private void faireParaitreLaSectionSansAttendre() {
        Platform.runLater(this::rendreLaSectionVisible);
    }

    private void faireParaitreLaSection(FxRobot robot) {
        robot.interact(this::rendreLaSectionVisible);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void rendreLaSectionVisible() {
        section.setManaged(true);
        section.setVisible(true);
    }

    /// De combien de pixels la page est écartée de la place qui met la section en haut du champ,
    /// bornée au bas de la page. Calculé ici sans passer par le geste, pour ne pas le juger avec
    /// lui-même.
    private double ecartALaPlaceVoulue() {
        return Attente.surLeFil(
                () -> {
                    double course = Math.max(
                            1,
                            pane.getContent().getBoundsInLocal().getHeight()
                                    - pane.getViewportBounds().getHeight());
                    double voulue = Math.clamp(section.getBoundsInParent().getMinY() / course, 0, 1);
                    return Math.abs(pane.getVvalue() - voulue) * course;
                },
                "lire la position de la page",
                5_000L);
    }
}

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
/// en page qui la place. Le premier cas en est le témoin. Il gagne une course, il ne l'évite pas : une
/// pulsation peut passer entre le `Platform.runLater` et le premier `interact` du geste, 14 fois sur
/// 1 500 sur un poste (#6143). Il rejoue donc sa fabrication, et exige une reproduction sur cinq.
///
/// Il ne reproduit pas la **course** du cas réel, dont la fenêtre est d'une pulsation : dix passes
/// filmées sur un poste ne l'ont pas montrée une fois. Que cet ordre soit bien celui des deux
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

    /// Combien de fois le témoin rejoue sa fabrication avant de conclure que le défaut ne se
    /// reproduit plus. Une course se perd moins d'une fois sur cent ; cinq de suite ne se sont pas vues.
    private static final int ESSAIS = 5;

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
        assertThat(leDefautSeReproduit(robot))
                .as("c'est le défaut : le geste a réglé la page sur les bornes d'avant, puis a conclu"
                        + " parce que la section est dans le cadre. Aucun de " + ESSAIS + " essais ne l'a"
                        + " montré : le banc ne reproduit plus rien, et le cas suivant ne prouve plus que"
                        + " le geste neuf est nécessaire")
                .isTrue();
        assertThat(GesteVisible.estPoseDansLeCadre(robot, "#section"))
                .as("et le prédicat le voit : la section est dans le cadre, la page n'est pas posée")
                .isFalse();
    }

    @Test
    @DisplayName("#6143 : après un ordre sain, le témoin sait encore reproduire le défaut")
    void apres_un_ordre_sain_le_defaut_se_reproduit_encore(FxRobot robot) {
        faireParaitreLaSection(robot);
        GesteVisible.amenerDansLeCadre(robot, "#section");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(leDefautSeReproduit(robot))
                .as("la section est mise en page et la page est à sa place : c'est l'état que laisse"
                        + " une course perdue. Si un essai ne remettait pas le banc à son départ, plus"
                        + " aucun ne reproduirait le défaut, et rejouer ne servirait à rien")
                .isTrue();
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
    @DisplayName("#6069 : le prédicat met en page avant de juger, il ne lit pas les bornes d'avant")
    void le_predicat_ne_juge_pas_des_bornes_perimees(FxRobot robot) {
        faireParaitreLaSection(robot);
        GesteVisible.poserDansLeCadre(robot, "#section");

        // Le contenu grandit sans que le banc attende avant la lecture. Une pulsation peut s'y glisser
        // (#6143) : le cas reste vert dans les deux ordres, il prouve seulement moins ce passage-là.
        Platform.runLater(() -> ((VBox) pane.getContent()).getChildren().add(remplissage(CROISSANCE)));

        assertThat(GesteVisible.estPoseDansLeCadre(robot, "#section"))
                .as("lu sur les bornes d'avant, le réglage aurait l'air en place : la page n'a pas encore"
                        + " bougé, le contenu n'a pas encore grandi. Une assertion de fin posée juste"
                        + " après un changement d'écran jugerait alors l'écran précédent")
                .isFalse();
    }

    @Test
    @DisplayName("#6069 : une section qui n'a pas paru n'est pas posée, même si la page n'a pas à bouger")
    void une_section_qui_n_a_pas_paru_n_est_pas_posee(FxRobot robot) {
        assertThat(GesteVisible.estPoseDansLeCadre(robot, "#section"))
                .as("la page est bien là où le réglage la mettrait, puisqu'il n'y a rien à régler : c'est"
                        + " la présence dans le cadre qui manque. Le prédicat demande les deux, sans quoi"
                        + " un verdict absent passerait pour un verdict posé")
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

    /// Le défaut d'origine s'est-il reproduit en [#ESSAIS] essais au plus (#6143) ?
    ///
    /// Un essai remet le banc à son départ, fabrique l'ordre fautif et lit où la page est restée. Il
    /// s'arrête au premier qui reproduit : le banc est alors dans l'état fautif, que l'appelant relit.
    private boolean leDefautSeReproduit(FxRobot robot) {
        for (int essai = 0; essai < ESSAIS; essai++) {
            remettreAuDepart(robot);
            faireParaitreLaSectionSansAttendre();
            GesteVisible.amenerDansLeCadre(robot, "#section");
            WaitForAsyncUtils.waitForFxEvents();
            if (ecartALaPlaceVoulue() > 1.0) {
                return true;
            }
        }
        return false;
    }

    private void remettreAuDepart(FxRobot robot) {
        robot.interact(() -> {
            section.setVisible(false);
            section.setManaged(false);
            pane.setVvalue(pane.getVmin());
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// La section paraît sans que le banc attende quoi que ce soit avant le geste qui suit.
    /// `robot.interact` laisserait passer une pulsation à coup sûr, et le défaut ne se verrait plus.
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

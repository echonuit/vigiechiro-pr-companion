package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le garde qui dit « c'est à l'image » n'avait **aucun test à lui** (#4149).
///
/// ## Pourquoi il en faut un
///
/// [CadreVisible] est un garde **au service d'autres gardes** : quand il se trompe, il ne se trompe pas
/// tout seul. Sa première version exigeait une marge de lecture des **deux** côtés du cadre, ce qui
/// rendait le **premier élément d'une liste** définitivement inatteignable - rien ne peut se placer
/// au-dessus de lui quand le défilement est en butée haute. `amener` tournait alors jusqu'à sa borne et
/// échouait sur un noeud parfaitement visible.
///
/// Le défaut ne s'est pas vu pendant deux chantiers, parce que les écrans où il servait tenaient tous
/// dans leur cadre. Il est sorti sur la première liste qui commence en haut.
///
/// ## Ce que ce test fixe
///
/// Les quatre positions qui décident, sur un vrai [ScrollPane] mesuré par la plateforme.
@ExtendWith(ApplicationExtension.class)
class CadreVisibleTest {

    private static final double HAUTEUR_CADRE = 300;
    private static final double HAUTEUR_LIGNE = 60;

    /// Assez de lignes pour que le contenu dépasse le cadre : sans cela il n'y a pas de « hors cadre ».
    private static final int LIGNES = 20;

    private Stage fenetre;
    private ScrollPane cadre;
    private VBox contenu;

    @Start
    void start(Stage stage) {
        contenu = new VBox();
        for (int i = 0; i < LIGNES; i++) {
            Label ligne = new Label("ligne " + i);
            ligne.setMinHeight(HAUTEUR_LIGNE);
            ligne.setPrefHeight(HAUTEUR_LIGNE);
            contenu.getChildren().add(ligne);
        }
        cadre = new ScrollPane(contenu);
        cadre.setPrefViewportHeight(HAUTEUR_CADRE);
        cadre.setFitToWidth(true);

        // ⚠️ Une fenêtre À SOI : ce banc dimensionne, et le Stage du harnais est partagé ([ADR 4134]).
        fenetre = new Stage();
        fenetre.initOwner(stage);
        fenetre.setScene(new Scene(new VBox(cadre), 400, HAUTEUR_CADRE));
        fenetre.show();
    }

    @AfterEach
    void refermer(FxRobot robot) {
        robot.interact(fenetre::close);
    }

    private Label ligne(int rang) {
        return (Label) contenu.getChildren().get(rang);
    }

    @Test
    @DisplayName("#4149 : la première ligne, en butée haute, est à l'image")
    void la_premiere_ligne_est_a_l_image(FxRobot robot) {
        robot.interact(() -> cadre.setVvalue(0));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(CadreVisible.contient(ligne(0)))
                .as("rien ne peut se placer au-dessus du premier élément : exiger une marge en haut le"
                        + " rendrait inatteignable, et c'était le défaut")
                .isTrue();
    }

    @Test
    @DisplayName("Une ligne défilée au-dessus du cadre n'est pas à l'image")
    void une_ligne_au_dessus_du_cadre_n_est_pas_a_l_image(FxRobot robot) {
        robot.interact(() -> cadre.setVvalue(1));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(CadreVisible.contient(ligne(0)))
                .as("le contenu a défilé : la première ligne est passée sous le bord haut")
                .isFalse();
    }

    @Test
    @DisplayName("#4128 : une ligne coupée par le bord BAS n'est pas à l'image")
    void une_ligne_coupee_par_le_bord_bas_n_est_pas_a_l_image(FxRobot robot) {
        robot.interact(() -> cadre.setVvalue(0));
        WaitForAsyncUtils.waitForFxEvents();

        // La première ligne dont le bas DÉPASSE la zone d'affichage : visible au sens de `lookup`, et
        // coupée à l'écran. C'est le défaut que ce garde existe pour voir (#4128).
        Label coupee = premiereLigneCoupee();
        assertThat(CadreVisible.contient(coupee))
                .as("« %s » déborde le bas du cadre : elle est coupée, donc pas à l'image", coupee.getText())
                .isFalse();
    }

    @Test
    @DisplayName("`amener` fait défiler jusqu'à ce que la cible entre dans le cadre")
    void amener_fait_defiler_jusqu_a_la_cible(FxRobot robot) {
        robot.interact(() -> cadre.setVvalue(0));
        WaitForAsyncUtils.waitForFxEvents();
        Label lointaine = ligne(LIGNES - 1);
        assertThat(CadreVisible.contient(lointaine))
                .as("point de départ : la dernière ligne est hors du cadre, sinon ce test ne montre rien")
                .isFalse();

        CadreVisible.amener(lointaine, robot);

        assertThat(CadreVisible.contient(lointaine)).isTrue();
    }

    /// La première ligne dont le bas dépasse la zone d'affichage, cadre en butée haute.
    private Label premiereLigneCoupee() {
        double basDuCadre = cadre.localToScene(cadre.getBoundsInLocal()).getMaxY();
        for (int rang = 0; rang < LIGNES; rang++) {
            Label ligne = ligne(rang);
            double bas = ligne.localToScene(ligne.getBoundsInLocal()).getMaxY();
            if (bas > basDuCadre) {
                return ligne;
            }
        }
        throw new AssertionError("aucune ligne ne dépasse le bas du cadre : le contenu ne le dépasse pas");
    }
}

package fr.univ_amu.iut.multisite.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.framework.junit5.ApplicationExtension;

/// Garde-fou du **rendu CSS des icônes** des contrôles de carte (#1564).
///
/// Ces boutons portaient un caractère (`⤢`, `✎`, `💾`, `▾`) que leur classe CSS colorait par
/// `-fx-text-fill: #34495e`. En passant à une [FontIcon], cette règle **cesse de s'appliquer** : une
/// icône ignore `-fx-text-fill` et n'obéit qu'à `-fx-icon-color`. Sans la règle ajoutée, les icônes
/// seraient **noires** sur des contrôles dont tout le reste est gris ardoise - un écart que rien ne
/// signale, puisque la substitution compile et que tous les autres tests restent verts.
///
/// La **taille**, elle, n'a pas besoin de règle : mesure faite, une `FontIcon` se dimensionne sur la
/// police héritée, donc le `-fx-font-size` déjà présent sur ces classes la porte. C'est le contraire de
/// ce que l'on pourrait croire en lisant que « le CSS d'une icône est séparé » : seule la couleur l'est.
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
class StyleControlesCarteTest {

    /// Gris ardoise des contrôles de carte, aligné sur le `-fx-text-fill` de leurs classes dans
    /// `multisite.css`. En dur : le test doit rougir si la feuille change sans qu'on y pense.
    private static final Color GRIS_ARDOISE = Color.web("#34495e");

    @Test
    @DisplayName("L'icône d'un bouton d'overlay prend la couleur du contrôle, pas le noir par défaut")
    void icone_d_overlay_coloree_par_le_css() {
        Button recadrer = new Button();
        StyleControlesCarte.overlay(recadrer, "bouton-recadrer", "fas-expand", "Recadrer la carte");
        FontIcon icone = (FontIcon) recadrer.getGraphic();
        Color avant = (Color) icone.getIconColor();

        appliquerFeuille(recadrer);

        assertThat(icone.getIconColor())
                .as(
                        "sans `-fx-icon-color`, l'icône reste %s alors que le libellé qu'elle remplace était "
                                + "gris ardoise : `-fx-text-fill` ne colore pas une FontIcon",
                        avant)
                .isEqualTo(GRIS_ARDOISE);
    }

    @Test
    @DisplayName("Le chevron de la légende est une icône, colorée elle aussi par le CSS")
    void chevron_de_legende_colore_par_le_css() {
        Node legende = LegendeCarte.creer();
        appliquerFeuille(legende);

        FontIcon chevron = (FontIcon) legende.lookup(".bascule-legende .ikonli-font-icon");
        assertThat(chevron)
                .as("la bascule de la légende doit porter une FontIcon atteignable par le CSS")
                .isNotNull();
        assertThat(chevron.getIconColor()).isEqualTo(GRIS_ARDOISE);
    }

    /// Monte le nœud dans une scène portant la feuille de la feature, puis applique le CSS. Sans scène,
    /// aucune règle ne s'applique et le test mesurerait la valeur par défaut des deux côtés.
    private static void appliquerFeuille(Node noeud) {
        Scene scene = new Scene(new StackPane(noeud));
        scene.getStylesheets()
                .addAll(
                        MultisiteController.class
                                .getResource("../../commun/view/palette.css")
                                .toExternalForm(),
                        MultisiteController.class.getResource("multisite.css").toExternalForm());
        scene.getRoot().applyCss();
        scene.getRoot().layout();
    }
}

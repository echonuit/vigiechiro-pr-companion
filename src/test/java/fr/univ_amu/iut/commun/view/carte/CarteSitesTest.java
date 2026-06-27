package fr.univ_amu.iut.commun.view.carte;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Tests TestFX (headless) du composant [CarteSites] (#152) : carrés et points apparaissent dans le graphe
/// de scène, les marqueurs sont **focusables** et porteurs d'un `accessibleText` (#163), et le clic d'un
/// point déclenche le callback. **Sans tuiles** (réseau) : on n'asserte jamais le fond OSM.
@ExtendWith(ApplicationExtension.class)
class CarteSitesTest {

    private CarteSites carte;

    @Start
    void start(Stage stage) {
        carte = new CarteSites();
        stage.setScene(new Scene(carte, 640, 480));
        stage.show();
    }

    private static DonneesCarte deuxPointsUnCarre() {
        List<PointGeo> points = List.of(
                new PointGeo("Z1", 43.300, -0.360, Color.GREEN), new PointGeo("Z2", 43.310, -0.350, Color.ORANGE));
        EmpriseCarre emprise =
                new EmpriseAutourDesPoints().emprise("640380", points).orElseThrow();
        CarreGeo carre = new CarreGeo("640380", emprise, Color.color(0.2, 0.4, 0.8, 0.3));
        return new DonneesCarte(List.of(carre), points);
    }

    @Test
    void affiche_carres_et_points_avec_accessibilite(FxRobot robot) {
        robot.interact(() -> carte.setDonnees(deuxPointsUnCarre()));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(carte.lookupAll(".carte-carre"))
                .as("un rectangle de carré tracé")
                .hasSize(1);
        var libelles = carte.lookupAll(".carte-point-libelle");
        assertThat(libelles)
                .as("un libellé visible par point (#163, pas que la couleur)")
                .hasSize(2);

        // Accessibilité (#163) : le marqueur (parent du libellé) est focusable et a un libellé accessible.
        Node marqueur = libelles.iterator().next().getParent();
        assertThat(marqueur.isFocusTraversable())
                .as("marqueur navigable au clavier")
                .isTrue();
        assertThat(marqueur.getAccessibleText()).contains("Point d'écoute");
    }

    @Test
    void point_sans_gps_non_place(FxRobot robot) {
        List<PointGeo> points = List.of(
                new PointGeo("Z1", 43.300, -0.360, Color.GREEN),
                new PointGeo("SansGPS", Double.NaN, Double.NaN, Color.GRAY));
        robot.interact(() -> carte.setDonnees(new DonneesCarte(List.of(), points)));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(carte.lookupAll(".carte-point-libelle"))
                .as("un point au GPS manquant (NaN) n'est pas placé sur la carte (#152 P2)")
                .hasSize(1);
    }

    @Test
    void clic_point_declenche_le_callback(FxRobot robot) {
        List<PointGeo> cliques = new ArrayList<>();
        robot.interact(() -> {
            carte.setOnPointClic(cliques::add);
            carte.setDonnees(deuxPointsUnCarre());
        });
        WaitForAsyncUtils.waitForFxEvents();

        Node marqueur =
                carte.lookupAll(".carte-point-libelle").iterator().next().getParent();
        // Le handler ignore l'évènement → handle(null) suffit à exercer le câblage du callback.
        robot.interact(() -> marqueur.getOnMouseClicked().handle(null));

        assertThat(cliques).as("le clic d'un point remonte le PointGeo").hasSize(1);
    }
}

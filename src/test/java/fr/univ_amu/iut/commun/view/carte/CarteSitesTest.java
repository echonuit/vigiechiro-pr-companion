package fr.univ_amu.iut.commun.view.carte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
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
                new PointGeo("Z1", 43.4010, -1.5740, Color.GREEN), new PointGeo("Z2", 43.4055, -1.5680, Color.ORANGE));
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
                new PointGeo("Z1", 43.4010, -1.5740, Color.GREEN),
                new PointGeo("SansGPS", Double.NaN, Double.NaN, Color.GRAY));
        robot.interact(() -> carte.setDonnees(new DonneesCarte(List.of(), points)));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(carte.lookupAll(".carte-point-libelle"))
                .as("un point au GPS manquant (NaN) n'est pas placé sur la carte (#152 P2)")
                .hasSize(1);
    }

    @Test
    void point_approximatif_rendu_distinctement(FxRobot robot) {
        // Un point sans GPS posé au centre de son carré (#153) est marqué `approximatif` : la couche doit le
        // rendre distinctement (anneau pointillé creux) ET l'annoncer (accessibilité #163, pas que la forme).
        PointGeo approche = new PointGeo("A3", 43.4031, -1.5708, Color.GREEN, "A3\nPosition approximative", true);
        robot.interact(() -> carte.setDonnees(new DonneesCarte(List.of(), List.of(approche))));
        WaitForAsyncUtils.waitForFxEvents();

        Node marqueur =
                carte.lookupAll(".carte-point-libelle").iterator().next().getParent();
        assertThat(marqueur.getAccessibleText())
                .as("l'état approché est annoncé aux lecteurs d'écran")
                .contains("approximative");
        Circle pastille = (Circle) ((Group) marqueur)
                .getChildren().stream()
                        .filter(Circle.class::isInstance)
                        .findFirst()
                        .orElseThrow();
        assertThat(pastille.getStrokeDashArray()).as("anneau pointillé").isNotEmpty();
        assertThat(pastille.getStroke()).as("anneau coloré par le statut").isEqualTo(Color.GREEN);
        assertThat(pastille.getFill())
                .as("fond blanc contrastant, distinct d'une pastille pleine colorée")
                .isEqualTo(Color.WHITE)
                .isNotEqualTo(approche.couleur());
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

    @Test
    void clic_reel_sur_marqueur_positionne_remonte_le_point(FxRobot robot) {
        List<PointGeo> cliques = new ArrayList<>();
        robot.interact(() -> {
            carte.setOnPointClic(cliques::add);
            carte.setDonnees(deuxPointsUnCarre());
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Clic RÉEL à l'écran sur le libellé « Z1 » : ne réussit que si le marqueur a été **projeté** à une
        // position d'écran valide (couvre le positionnement, pas seulement la présence — vigilance #294).
        robot.clickOn("Z1");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(cliques).extracting(PointGeo::libelle).contains("Z1");
    }

    @Test
    void clic_carre_declenche_le_callback(FxRobot robot) {
        List<CarreGeo> cliques = new ArrayList<>();
        robot.interact(() -> {
            carte.setOnCarreClic(cliques::add);
            carte.setDonnees(deuxPointsUnCarre());
        });
        WaitForAsyncUtils.waitForFxEvents();

        Node rectangle = carte.lookup(".carte-carre");
        robot.interact(() -> rectangle.getOnMouseClicked().handle(null));

        assertThat(cliques).extracting(CarreGeo::numeroCarre).containsExactly("640380");
    }

    @Test
    void infobulle_posee_sur_carre_et_point(FxRobot robot) {
        PointGeo point = new PointGeo("Z1", 43.4010, -1.5740, Color.GREEN, "Z1\n5 passages\nStatut : Déposé");
        EmpriseCarre emprise =
                new EmpriseAutourDesPoints().emprise("640380", List.of(point)).orElseThrow();
        CarreGeo carre = new CarreGeo("640380", emprise, Color.color(0.2, 0.4, 0.8, 0.3), "Étang (640380)\n9 passages");
        robot.interact(() -> carte.setDonnees(new DonneesCarte(List.of(carre), List.of(point))));
        WaitForAsyncUtils.waitForFxEvents();

        // Les mini-stats au survol (#152) sont aussi exposées en accessibleHelp (#163) → testables headless.
        Node rectangle = carte.lookup(".carte-carre");
        assertThat(rectangle.getAccessibleHelp()).as("stats du carré au survol").contains("9 passages");
        Node marqueur =
                carte.lookupAll(".carte-point-libelle").iterator().next().getParent();
        assertThat(marqueur.getAccessibleHelp()).as("stats du point au survol").contains("5 passages");
    }

    @Test
    void surbrillance_met_le_carre_en_evidence(FxRobot robot) {
        robot.interact(() -> carte.setDonnees(deuxPointsUnCarre()));
        WaitForAsyncUtils.waitForFxEvents();
        Rectangle rectangle = (Rectangle) carte.lookup(".carte-carre");
        assertThat(rectangle.getStrokeWidth()).isEqualTo(1.5);

        robot.interact(() -> carte.surbrillanceCarre("640380"));

        assertThat(rectangle.getStrokeWidth())
                .as("le carré sélectionné est mis en évidence (bordure épaissie)")
                .isEqualTo(3.0);
    }

    @Test
    void surbrillance_survit_a_un_refresh(FxRobot robot) {
        robot.interact(() -> {
            carte.setDonnees(deuxPointsUnCarre());
            carte.surbrillanceCarre("640380");
        });
        WaitForAsyncUtils.waitForFxEvents();

        // setDonnees recrée les rectangles : la surbrillance mémorisée doit être réappliquée.
        robot.interact(() -> carte.setDonnees(deuxPointsUnCarre()));
        WaitForAsyncUtils.waitForFxEvents();

        Rectangle rectangle = (Rectangle) carte.lookup(".carte-carre");
        assertThat(rectangle.getStrokeWidth())
                .as("la surbrillance survit à un refresh de la carte (#152)")
                .isEqualTo(3.0);
    }

    @Test
    void centrer_sur_recentre_et_zoome(FxRobot robot) {
        robot.interact(() -> carte.centrerSur(43.4031, -1.5708, 14));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(carte.vueCarte().getCenter().getLatitude()).isCloseTo(43.4031, within(1e-6));
        assertThat(carte.vueCarte().getCenter().getLongitude()).isCloseTo(-1.5708, within(1e-6));
        assertThat(carte.vueCarte().getZoom()).isEqualTo(14.0);
    }

    // #339 : recadrer() revient sur l'emprise des données après un déplacement manuel.
    @Test
    void recadrer_revient_sur_les_donnees(FxRobot robot) {
        robot.interact(() -> carte.setDonnees(deuxPointsUnCarre()));
        WaitForAsyncUtils.waitForFxEvents();
        double latFit = carte.vueCarte().getCenter().getLatitude();
        double lonFit = carte.vueCarte().getCenter().getLongitude();
        double zoomFit = carte.vueCarte().getZoom();

        // Déplacement/zoom manuel ailleurs.
        robot.interact(() -> carte.centrerSur(10.0, 10.0, 4));
        assertThat(carte.vueCarte().getZoom())
                .as("déplacement manuel : la vue a bougé")
                .isEqualTo(4.0);

        robot.interact(carte::recadrer);
        assertThat(carte.vueCarte().getCenter().getLatitude())
                .as("recadrer revient au centre de l'emprise")
                .isCloseTo(latFit, within(1e-6));
        assertThat(carte.vueCarte().getCenter().getLongitude()).isCloseTo(lonFit, within(1e-6));
        assertThat(carte.vueCarte().getZoom()).as("et au zoom englobant").isEqualTo(zoomFit);
    }

    @Test
    void mode_edition_deplace_le_marqueur_au_clavier(FxRobot robot) {
        AtomicReference<double[]> dernier = new AtomicReference<>();
        PointGeo z1 = new PointGeo("Z1", 43.4031, -1.5708, Color.GREEN);
        robot.interact(() -> {
            carte.setEditionActive(true);
            carte.setOnPointDeplace((point, lat, lon) -> dernier.set(new double[] {lat, lon}));
            carte.setDonnees(new DonneesCarte(List.of(), List.of(z1)));
            carte.centrerSur(43.4031, -1.5708, 14); // marqueur près du centre, déplaçable sans sortir
        });
        WaitForAsyncUtils.waitForFxEvents();

        Node marqueur =
                carte.lookupAll(".carte-point-libelle").iterator().next().getParent();
        robot.interact(marqueur::requestFocus);
        robot.type(KeyCode.RIGHT); // vers l'est → longitude augmente
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(dernier.get())
                .as("le déplacement clavier remonte une nouvelle position")
                .isNotNull();
        assertThat(dernier.get()[1]).as("→ longitude vers l'est").isGreaterThan(-1.5708);

        robot.type(KeyCode.DOWN); // vers le sud → latitude diminue
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(dernier.get()[0]).as("↓ latitude vers le sud").isLessThan(43.4031);
    }

    @Test
    void hors_edition_le_marqueur_ne_se_deplace_pas(FxRobot robot) {
        AtomicReference<double[]> deplace = new AtomicReference<>();
        List<PointGeo> clics = new ArrayList<>();
        PointGeo z1 = new PointGeo("Z1", 43.4031, -1.5708, Color.GREEN);
        robot.interact(() -> {
            carte.setEditionActive(false);
            carte.setOnPointDeplace((point, lat, lon) -> deplace.set(new double[] {lat, lon}));
            carte.setOnPointClic(clics::add);
            carte.setDonnees(new DonneesCarte(List.of(), List.of(z1)));
        });
        WaitForAsyncUtils.waitForFxEvents();

        Node marqueur =
                carte.lookupAll(".carte-point-libelle").iterator().next().getParent();
        robot.interact(marqueur::requestFocus);
        robot.type(KeyCode.RIGHT);
        robot.interact(() -> marqueur.getOnMouseClicked().handle(null));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(deplace.get())
                .as("hors édition, aucune flèche ne déplace le point")
                .isNull();
        assertThat(clics).as("hors édition, le clic reste un clic").hasSize(1);
    }

    @Test
    void edition_un_clic_apres_deplacement_ne_redeplace_pas(FxRobot robot) {
        AtomicInteger nbDeplacements = new AtomicInteger();
        List<PointGeo> clics = new ArrayList<>();
        PointGeo z1 = new PointGeo("Z1", 43.4031, -1.5708, Color.GREEN);
        robot.interact(() -> {
            carte.setEditionActive(true);
            carte.setOnPointDeplace((point, lat, lon) -> nbDeplacements.incrementAndGet());
            carte.setOnPointClic(clics::add);
            carte.setDonnees(new DonneesCarte(List.of(), List.of(z1)));
            carte.centrerSur(43.4031, -1.5708, 14);
        });
        WaitForAsyncUtils.waitForFxEvents();
        Node marqueur =
                carte.lookupAll(".carte-point-libelle").iterator().next().getParent();

        robot.interact(marqueur::requestFocus);
        robot.type(KeyCode.RIGHT); // un déplacement clavier
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(nbDeplacements.get()).isEqualTo(1);

        robot.clickOn(marqueur); // un simple clic ensuite (press+release sans glisser)
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(nbDeplacements.get())
                .as("un clic, même après un déplacement, ne valide pas un nouveau déplacement (#330)")
                .isEqualTo(1);
        assertThat(clics)
                .as("en édition, le clic n'est pas un déclencheur de navigation (#330)")
                .isEmpty();
    }

    @Test
    void edition_entree_ne_declenche_pas_le_clic(FxRobot robot) {
        List<PointGeo> clics = new ArrayList<>();
        PointGeo z1 = new PointGeo("Z1", 43.4031, -1.5708, Color.GREEN);
        robot.interact(() -> {
            carte.setEditionActive(true);
            carte.setOnPointClic(clics::add);
            carte.setDonnees(new DonneesCarte(List.of(), List.of(z1)));
        });
        WaitForAsyncUtils.waitForFxEvents();
        Node marqueur =
                carte.lookupAll(".carte-point-libelle").iterator().next().getParent();

        robot.interact(marqueur::requestFocus);
        robot.type(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(clics)
                .as("en édition, Entrée ne déclenche pas le clic (#330)")
                .isEmpty();
    }
}

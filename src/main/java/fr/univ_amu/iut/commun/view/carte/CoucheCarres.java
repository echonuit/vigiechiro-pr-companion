package fr.univ_amu.iut.commun.view.carte;

import com.gluonhq.maps.MapLayer;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/// Couche des **tracés de carrés** (emprises 2 km) sur la [CarteSites]. Un tracé = rectangle rempli
/// (couleur de densité via [CarreGeo#remplissage()]) + bordure, porteur d'un `accessibleText` (#163).
/// Le rectangle est reprojeté à chaque mise en page depuis l'emprise (coins haut-gauche / bas-droite).
final class CoucheCarres extends MapLayer {

    private static final Color BORDURE = Color.web("#2c3e50");
    private final List<TraceCarre> traces = new ArrayList<>();

    /// Remplace les carrés tracés. Re-déclenche une mise en page de la couche.
    void definirCarres(List<CarreGeo> carres) {
        getChildren().clear();
        traces.clear();
        for (CarreGeo carre : carres) {
            TraceCarre trace = new TraceCarre(carre);
            traces.add(trace);
            getChildren().add(trace.rectangle);
        }
        markDirty();
    }

    /// Nombre de carrés actuellement tracés (utile aux tests).
    int nombreCarres() {
        return traces.size();
    }

    @Override
    protected void layoutLayer() {
        for (TraceCarre trace : traces) {
            EmpriseCarre emprise = trace.carre.emprise();
            // Coin haut-gauche = (latMax, lonMin) ; bas-droite = (latMin, lonMax) — la latitude croît vers
            // le nord, donc vers le HAUT de l'écran (Y plus petit).
            Point2D hautGauche = getMapPoint(emprise.latMax(), emprise.lonMin());
            Point2D basDroite = getMapPoint(emprise.latMin(), emprise.lonMax());
            if (hautGauche == null || basDroite == null) {
                trace.rectangle.setVisible(false);
                continue;
            }
            trace.rectangle.setVisible(true);
            trace.rectangle.setTranslateX(hautGauche.getX());
            trace.rectangle.setTranslateY(hautGauche.getY());
            trace.rectangle.setWidth(Math.abs(basDroite.getX() - hautGauche.getX()));
            trace.rectangle.setHeight(Math.abs(basDroite.getY() - hautGauche.getY()));
        }
    }

    private static final class TraceCarre {
        private final CarreGeo carre;
        private final Rectangle rectangle;

        private TraceCarre(CarreGeo carre) {
            this.carre = carre;
            Rectangle rectangle = new Rectangle();
            rectangle.setFill(carre.remplissage());
            rectangle.setStroke(BORDURE);
            rectangle.setStrokeWidth(1.5);
            rectangle.getStyleClass().add("carte-carre");
            rectangle.setAccessibleText("Carré " + carre.numeroCarre());
            this.rectangle = rectangle;
        }
    }
}

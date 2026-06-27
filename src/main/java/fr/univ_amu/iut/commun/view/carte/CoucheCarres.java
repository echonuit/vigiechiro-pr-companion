package fr.univ_amu.iut.commun.view.carte;

import com.gluonhq.maps.MapLayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/// Couche des **tracés de carrés** (emprises 2 km) sur la [CarteSites]. Un tracé = rectangle rempli
/// (couleur de densité via [CarreGeo#remplissage()]) + bordure, **focusable au clavier** et porteur d'un
/// `accessibleText` (#163). Le rectangle est reprojeté à chaque mise en page depuis l'emprise. Clic souris
/// ou Entrée/Espace → callback. Une [#surbrillance(String)] met en évidence un carré (sélection liée).
final class CoucheCarres extends MapLayer {

    private static final Color BORDURE = Color.web("#2c3e50");
    private static final Color BORDURE_SURBRILLANCE = Color.web("#3f51b5");

    private final List<TraceCarre> traces = new ArrayList<>();
    private Consumer<CarreGeo> onClic = carre -> {};

    void setOnClic(Consumer<CarreGeo> onClic) {
        this.onClic = Objects.requireNonNull(onClic, "onClic");
    }

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

    /// Met en **surbrillance** le carré `numeroCarre` (bordure indigo épaisse) et rend les autres à leur
    /// bordure normale. `null` ou inconnu : aucun carré surligné.
    void surbrillance(String numeroCarre) {
        for (TraceCarre trace : traces) {
            boolean actif = trace.carre.numeroCarre().equals(numeroCarre);
            trace.rectangle.setStroke(actif ? BORDURE_SURBRILLANCE : BORDURE);
            trace.rectangle.setStrokeWidth(actif ? 3.0 : 1.5);
        }
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

    /// Un tracé : rectangle cliquable/focusable, lié à son [CarreGeo].
    private final class TraceCarre {
        private final CarreGeo carre;
        private final Rectangle rectangle;

        private TraceCarre(CarreGeo carre) {
            this.carre = carre;
            Rectangle rectangle = new Rectangle();
            rectangle.setFill(carre.remplissage());
            rectangle.setStroke(BORDURE);
            rectangle.setStrokeWidth(1.5);
            rectangle.getStyleClass().add("carte-carre");
            rectangle.setFocusTraversable(true); // navigation clavier (#163)
            rectangle.setAccessibleText("Carré " + carre.numeroCarre());
            rectangle.setOnMouseClicked(evenement -> onClic.accept(carre));
            rectangle.setOnKeyPressed(evenement -> {
                if (evenement.getCode() == KeyCode.ENTER || evenement.getCode() == KeyCode.SPACE) {
                    onClic.accept(carre);
                }
            });
            this.rectangle = rectangle;
        }
    }
}

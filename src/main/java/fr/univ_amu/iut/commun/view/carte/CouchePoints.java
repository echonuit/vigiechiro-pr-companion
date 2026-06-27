package fr.univ_amu.iut.commun.view.carte;

import com.gluonhq.maps.MapLayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/// Couche de **marqueurs de points** sur la [CarteSites]. Un marqueur = pastille colorée (statut, via
/// [PointGeo#couleur()]) **+ libellé visible** (pour ne pas dépendre de la seule couleur, #163),
/// **focusable au clavier** et porteur d'un `accessibleText`. Clic souris ou Entrée/Espace → callback.
final class CouchePoints extends MapLayer {

    private final List<Marqueur> marqueurs = new ArrayList<>();
    private Consumer<PointGeo> onClic = point -> {};

    void setOnClic(Consumer<PointGeo> onClic) {
        this.onClic = Objects.requireNonNull(onClic, "onClic");
    }

    /// Remplace les marqueurs affichés. Re-déclenche une mise en page de la couche.
    void definirPoints(List<PointGeo> points) {
        getChildren().clear();
        marqueurs.clear();
        for (PointGeo point : points) {
            // Un point sans coordonnées exploitables (GPS manquant → NaN) n'est PAS placé sur la carte :
            // aucun marqueur n'est créé (donc rien de visible/focusable/cliquable), conformément au contrat.
            if (!Double.isFinite(point.latitude()) || !Double.isFinite(point.longitude())) {
                continue;
            }
            Marqueur marqueur = new Marqueur(point);
            marqueurs.add(marqueur);
            getChildren().add(marqueur.noeud);
        }
        markDirty();
    }

    /// Nombre de marqueurs actuellement gérés (utile aux tests).
    int nombreMarqueurs() {
        return marqueurs.size();
    }

    @Override
    protected void layoutLayer() {
        for (Marqueur marqueur : marqueurs) {
            Point2D position = getMapPoint(marqueur.point.latitude(), marqueur.point.longitude());
            if (position == null) {
                marqueur.noeud.setVisible(false);
                continue;
            }
            marqueur.noeud.setVisible(true);
            marqueur.noeud.setTranslateX(position.getX());
            marqueur.noeud.setTranslateY(position.getY());
        }
    }

    /// Un marqueur : pastille + libellé, dans un [Group] positionné par [#layoutLayer()].
    private final class Marqueur {
        private final PointGeo point;
        private final Group noeud;

        private Marqueur(PointGeo point) {
            this.point = point;
            Circle pastille = new Circle(6, point.couleur());
            pastille.setStroke(Color.WHITE);
            pastille.setStrokeWidth(1.5);
            Label libelle = new Label(point.libelle());
            libelle.getStyleClass().add("carte-point-libelle");
            libelle.setTranslateX(9);
            libelle.setTranslateY(-8);
            Group groupe = new Group(pastille, libelle);
            groupe.setFocusTraversable(true); // navigation clavier (#163)
            groupe.setAccessibleText("Point d'écoute " + point.libelle());
            groupe.setOnMouseClicked(evenement -> onClic.accept(point));
            groupe.setOnKeyPressed(evenement -> {
                if (evenement.getCode() == KeyCode.ENTER || evenement.getCode() == KeyCode.SPACE) {
                    onClic.accept(point);
                }
            });
            this.noeud = groupe;
        }
    }
}

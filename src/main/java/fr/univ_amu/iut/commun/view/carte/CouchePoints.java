package fr.univ_amu.iut.commun.view.carte;

import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/// Couche de **marqueurs de points** sur la [CarteSites]. Un marqueur = pastille colorée (statut, via
/// [PointGeo#couleur()]) **+ libellé visible** (pour ne pas dépendre de la seule couleur, #163),
/// **focusable au clavier** et porteur d'un `accessibleText`. Clic souris ou Entrée/Espace → callback.
///
/// En **mode édition** ([#setEditable(boolean)]), un marqueur se **déplace** au glisser ou aux flèches du
/// clavier : la position écran est reconvertie en WGS84 via [MapView#getMapPosition(double, double)] et
/// remontée par [#setOnDeplace]. La conversion écran → géo nécessite le [MapView] (fourni au constructeur).
final class CouchePoints extends MapLayer {

    /// Pas de déplacement au clavier, en pixels écran (reconverti en degrés selon le zoom courant).
    private static final double PAS_CLAVIER_PX = 4.0;

    private final MapView carte;
    private final List<Marqueur> marqueurs = new ArrayList<>();
    private Consumer<PointGeo> onClic = point -> {};
    private DeplacementMarqueur onDeplace = (point, lat, lon) -> {};
    private boolean editable;

    CouchePoints(MapView carte) {
        this.carte = Objects.requireNonNull(carte, "carte");
    }

    void setOnClic(Consumer<PointGeo> onClic) {
        this.onClic = Objects.requireNonNull(onClic, "onClic");
    }

    void setOnDeplace(DeplacementMarqueur onDeplace) {
        this.onDeplace = Objects.requireNonNull(onDeplace, "onDeplace");
    }

    /// Active/désactive l'édition (déplacement des marqueurs au glisser/clavier). Met à jour le curseur.
    void setEditable(boolean editable) {
        this.editable = editable;
        for (Marqueur marqueur : marqueurs) {
            marqueur.noeud.setCursor(editable ? Cursor.OPEN_HAND : Cursor.HAND);
        }
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
            if (marqueur.positionEcranModifiee) {
                continue; // marqueur déjà déplacé : on garde sa position écran courante
            }
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

    /// Convertit une position **écran** (coordonnées de scène) en WGS84, ou `null` hors de la carte.
    private MapPoint versGeo(double sceneX, double sceneY) {
        Point2D local = carte.sceneToLocal(sceneX, sceneY);
        return local == null ? null : carte.getMapPosition(local.getX(), local.getY());
    }

    /// Un marqueur : pastille + libellé, dans un [Group] positionné par [#layoutLayer()].
    private final class Marqueur {
        private final PointGeo point;
        private final Group noeud;

        /// Vrai dès que la **position écran** du marqueur a été modifiée (glisser ou clavier) : la mise en
        /// page ne le repositionne plus depuis l'ancien GPS, jusqu'au prochain `definirPoints`.
        private boolean positionEcranModifiee;

        /// Vrai pendant un **glisser en cours** seulement (entre press et release) : sépare un vrai
        /// glisser-déposer d'un simple clic, pour ne pas « valider » un déplacement non demandé (#330).
        private boolean glisserEnCours;

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
            groupe.setCursor(editable ? Cursor.OPEN_HAND : Cursor.HAND);
            if (point.infobulle() != null) {
                // Mini-stats au survol (#152) ; aussi en accessibleHelp pour les lecteurs d'écran (#163).
                Tooltip.install(groupe, new Tooltip(point.infobulle()));
                groupe.setAccessibleHelp(point.infobulle());
            }
            groupe.setOnMouseClicked(evenement -> {
                if (!editable) {
                    onClic.accept(point);
                }
            });
            groupe.setOnMousePressed(evenement -> glisserEnCours = false); // début de geste : pas encore glissé
            groupe.setOnMouseDragged(evenement -> {
                if (editable) {
                    glisserEnCours = true;
                    deplacerVers(evenement.getSceneX(), evenement.getSceneY(), false);
                    evenement.consume();
                }
            });
            groupe.setOnMouseReleased(evenement -> {
                // On ne valide un déplacement QUE si un vrai glisser a eu lieu pendant CE geste (#330) :
                // un simple clic, même après un déplacement précédent, ne déplace plus le point.
                if (editable && glisserEnCours) {
                    deplacerVers(evenement.getSceneX(), evenement.getSceneY(), true);
                    evenement.consume();
                }
                glisserEnCours = false;
            });
            groupe.setOnKeyPressed(evenement -> {
                if (editable) {
                    Point2D delta = deltaClavier(evenement.getCode());
                    if (delta != null) {
                        nudge(delta);
                        evenement.consume();
                    }
                } else if (evenement.getCode() == KeyCode.ENTER || evenement.getCode() == KeyCode.SPACE) {
                    onClic.accept(point); // en édition, le marqueur n'est plus un déclencheur de clic (#330)
                }
            });
            this.noeud = groupe;
        }

        /// Déplace le marqueur sous la position écran donnée ; si `valider`, remonte le nouveau GPS.
        private void deplacerVers(double sceneX, double sceneY, boolean valider) {
            Point2D local = carte.sceneToLocal(sceneX, sceneY);
            MapPoint geo = versGeo(sceneX, sceneY);
            if (local == null || geo == null) {
                return;
            }
            positionEcranModifiee = true;
            noeud.setTranslateX(local.getX());
            noeud.setTranslateY(local.getY());
            if (valider) {
                onDeplace.deplace(point, geo.getLatitude(), geo.getLongitude());
            }
        }

        /// Déplacement clavier : décale la position écran de quelques pixels, reconvertit en GPS et valide.
        private void nudge(Point2D deltaPx) {
            double x = noeud.getTranslateX() + deltaPx.getX();
            double y = noeud.getTranslateY() + deltaPx.getY();
            MapPoint geo = carte.getMapPosition(x, y);
            if (geo == null) {
                return;
            }
            positionEcranModifiee = true;
            noeud.setTranslateX(x);
            noeud.setTranslateY(y);
            onDeplace.deplace(point, geo.getLatitude(), geo.getLongitude());
        }

        private Point2D deltaClavier(KeyCode code) {
            return switch (code) {
                case LEFT -> new Point2D(-PAS_CLAVIER_PX, 0);
                case RIGHT -> new Point2D(PAS_CLAVIER_PX, 0);
                case UP -> new Point2D(0, -PAS_CLAVIER_PX);
                case DOWN -> new Point2D(0, PAS_CLAVIER_PX);
                default -> null;
            };
        }
    }
}

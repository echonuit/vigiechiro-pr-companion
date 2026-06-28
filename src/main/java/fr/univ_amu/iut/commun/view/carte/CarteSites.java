package fr.univ_amu.iut.commun.view.carte;

import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.layout.Region;

/// **Composant carte réutilisable** : affiche des **carrés** (emprises 2 km) et des **points d'écoute**
/// (marqueurs colorés par statut) sur un fond de carte OpenStreetMap (Gluon Maps). Destiné à irriguer
/// plusieurs écrans (vue multi-sites, fiche site, rattachement d'import…) : il rend une [DonneesCarte]
/// **pure** sans rien savoir du domaine ; l'appelant décide des couleurs et fournit les emprises (cf.
/// [FournisseurEmpriseCarre]).
///
/// Le fond de tuiles est **best-effort** (réseau) : en environnement headless / hors-ligne, il reste
/// vide, mais les carrés et points (nos couches) s'affichent et se positionnent normalement.
///
/// Accessibilité (#163) : les marqueurs sont focusables au clavier et porteurs d'un libellé accessible
/// (cf. [CouchePoints]) ; le tracé de carré porte un `accessibleText` (cf. [CoucheCarres]).
public class CarteSites extends Region {

    /// Centre par défaut : France métropolitaine, en attendant des données.
    private static final MapPoint CENTRE_FRANCE = new MapPoint(46.6, 2.5);

    private static final int ZOOM_FRANCE = 6;
    private static final int ZOOM_MAX = 15;
    private static final int ZOOM_MIN = 4;

    /// Zoom de mise au point sur un carré 2 km (un seul site/point) : assez serré pour le voir en entier.
    private static final int ZOOM_CARRE = 14;

    private final MapView carte = new MapView();
    private final CoucheCarres coucheCarres = new CoucheCarres();
    private final CouchePoints couchePoints = new CouchePoints(carte);

    /// Numéro du carré actuellement en surbrillance (sélection liée au tableau), ou `null`. Mémorisé pour
    /// **réappliquer** la surbrillance après un `setDonnees` qui recrée les tracés (refresh de la carte).
    private String carreSurbrillance;

    public CarteSites() {
        getStyleClass().add("carte-sites");
        // Les carrés d'abord (dessous), les points ensuite (au-dessus).
        carte.addLayer(coucheCarres);
        carte.addLayer(couchePoints);
        carte.setZoom(ZOOM_FRANCE);
        carte.setCenter(CENTRE_FRANCE);
        getChildren().add(carte);
    }

    /// Définit le handler de clic sur un point (souris ou Entrée/Espace au clavier).
    public void setOnPointClic(Consumer<PointGeo> onPointClic) {
        couchePoints.setOnClic(Objects.requireNonNull(onPointClic, "onPointClic"));
    }

    /// Définit le handler de clic sur un carré (souris ou Entrée/Espace au clavier).
    public void setOnCarreClic(Consumer<CarreGeo> onCarreClic) {
        coucheCarres.setOnClic(Objects.requireNonNull(onCarreClic, "onCarreClic"));
    }

    /// Active/désactive le **mode édition** : un marqueur de point se déplace alors au glisser ou aux
    /// flèches du clavier (la position est remontée par [#setOnPointDeplace]). Désactivé par défaut.
    public void setEditionActive(boolean active) {
        couchePoints.setEditable(active);
    }

    /// Définit le rappel appelé quand un marqueur est **déplacé** (glisser/clavier) en mode édition.
    public void setOnPointDeplace(DeplacementMarqueur onPointDeplace) {
        couchePoints.setOnDeplace(Objects.requireNonNull(onPointDeplace, "onPointDeplace"));
    }

    /// Met en **surbrillance** le carré de numéro `numeroCarre` (les autres reviennent à la normale).
    /// `null` n'en surligne aucun. Sert à refléter sur la carte la sélection faite dans le tableau.
    public void surbrillanceCarre(String numeroCarre) {
        carreSurbrillance = numeroCarre;
        coucheCarres.surbrillance(numeroCarre);
    }

    /// Centre la carte sur `(lat, lon)` au `zoom` donné (borné `[ZOOM_MIN, ZOOM_MAX]`). Sert à **focaliser**
    /// la carte sur un élément précis (« voir sur la carte » d'un site/point/passage).
    public void centrerSur(double latitude, double longitude, int zoom) {
        carte.setCenter(new MapPoint(latitude, longitude));
        carte.setZoom(Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom)));
    }

    /// Focalise sur un **carré** (centre de son emprise, zoom adapté à une maille 2 km).
    public void centrerSurCarre(EmpriseCarre emprise) {
        Objects.requireNonNull(emprise, "emprise");
        centrerSur(emprise.latCentre(), emprise.lonCentre(), ZOOM_CARRE);
    }

    /// Affiche les carrés et points de `donnees`, puis recadre la vue sur leur emprise. La surbrillance
    /// active (sélection du tableau) est **réappliquée** après recréation des tracés, pour qu'elle survive
    /// à un refresh de la carte.
    public void setDonnees(DonneesCarte donnees) {
        setDonnees(donnees, true);
    }

    /// Variante avec contrôle du **recadrage**. Avec `recadrer=false`, la vue (centre/zoom) **ne bouge
    /// pas** : utile quand on rafraîchit fréquemment les tracés sans vouloir suivre la donnée (p. ex. une
    /// carte-outil de saisie GPS qui reste calée sur le carré pendant qu'on déplace le marqueur).
    public void setDonnees(DonneesCarte donnees, boolean recadrer) {
        Objects.requireNonNull(donnees, "donnees");
        coucheCarres.definirCarres(donnees.carres());
        coucheCarres.surbrillance(carreSurbrillance);
        couchePoints.definirPoints(donnees.points());
        if (recadrer) {
            recadrerSur(donnees);
        }
    }

    /// Recadre la carte sur l'ensemble des coordonnées (points géolocalisés + centres des carrés) :
    /// centre = barycentre, zoom déduit de l'étendue. Sans aucune coordonnée, reste centré sur la France.
    public void recadrerSur(DonneesCarte donnees) {
        List<double[]> coords = new ArrayList<>();
        for (PointGeo point : donnees.points()) {
            if (Double.isFinite(point.latitude()) && Double.isFinite(point.longitude())) {
                coords.add(new double[] {point.latitude(), point.longitude()});
            }
        }
        for (CarreGeo carre : donnees.carres()) {
            coords.add(
                    new double[] {carre.emprise().latCentre(), carre.emprise().lonCentre()});
        }
        if (coords.isEmpty()) {
            carte.setCenter(CENTRE_FRANCE);
            carte.setZoom(ZOOM_FRANCE);
            return;
        }
        double latMin = coords.stream().mapToDouble(c -> c[0]).min().orElseThrow();
        double latMax = coords.stream().mapToDouble(c -> c[0]).max().orElseThrow();
        double lonMin = coords.stream().mapToDouble(c -> c[1]).min().orElseThrow();
        double lonMax = coords.stream().mapToDouble(c -> c[1]).max().orElseThrow();
        carte.setCenter(new MapPoint((latMin + latMax) / 2.0, (lonMin + lonMax) / 2.0));
        carte.setZoom(zoomPourEtendue(Math.max(latMax - latMin, lonMax - lonMin)));
    }

    /// Zoom OSM grossièrement adapté à une étendue en degrés : plus l'étendue est grande, plus le zoom est
    /// faible. Borné à `[ZOOM_MIN, ZOOM_MAX]`. Une étendue nulle (un seul point) donne le zoom max.
    private static int zoomPourEtendue(double etendueDegres) {
        if (etendueDegres <= 0) {
            return ZOOM_MAX;
        }
        int zoom = (int) Math.round(8 - Math.log(etendueDegres) / Math.log(2));
        return Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom));
    }

    @Override
    protected void layoutChildren() {
        carte.resizeRelocate(0, 0, getWidth(), getHeight());
    }

    /// Accès au fond de carte Gluon — **réservé aux tests** du même paquet (centre/zoom courants).
    MapView vueCarte() {
        return carte;
    }
}

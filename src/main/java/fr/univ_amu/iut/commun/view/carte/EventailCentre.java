package fr.univ_amu.iut.commun.view.carte;

import java.util.ArrayList;
import java.util.List;

/// Répartit `nombre` éléments **en éventail autour du centre** d'une [EmpriseCarre] : utilisé pour
/// désempiler les points **sans GPS** posés au centre de leur carré (#153/#154). Un seul élément reste
/// **pile au centre** ; plusieurs sont placés sur un **petit cercle** ([#RAYON] du demi-côté), donc à des
/// positions distinctes et toujours **à l'intérieur de la maille** — sinon ils se masqueraient
/// mutuellement (un point sélectionné pourrait disparaître sous un autre).
///
/// Logique partagée entre la vue multi-sites ([fr.univ_amu.iut.multisite.view.ConstructeurDonneesCarte])
/// et la carte de confirmation d'import ([fr.univ_amu.iut.importation.view.CarteRattachement]).
public final class EventailCentre {

    /// Fraction du **demi-côté** du carré servant de rayon à l'éventail : assez petit pour rester bien à
    /// l'intérieur de la maille tout en désempilant les marqueurs.
    private static final double RAYON = 0.30;

    private EventailCentre() {}

    /// Positions `{latitude, longitude}` des `nombre` éléments (le i-ᵉ pour le i-ᵉ élément). `nombre <= 0`
    /// renvoie une liste vide ; `1` renvoie le centre ; au-delà, un cercle régulier autour du centre.
    public static List<double[]> positions(EmpriseCarre emprise, int nombre) {
        List<double[]> positions = new ArrayList<>();
        double latCentre = emprise.latCentre();
        double lonCentre = emprise.lonCentre();
        double rayonLat = RAYON * (emprise.latMax() - emprise.latMin()) / 2.0;
        double rayonLon = RAYON * (emprise.lonMax() - emprise.lonMin()) / 2.0;
        for (int i = 0; i < nombre; i++) {
            double latitude = latCentre;
            double longitude = lonCentre;
            if (nombre > 1) {
                double angle = 2.0 * Math.PI * i / nombre;
                latitude += rayonLat * Math.cos(angle);
                longitude += rayonLon * Math.sin(angle);
            }
            positions.add(new double[] {latitude, longitude});
        }
        return positions;
    }
}

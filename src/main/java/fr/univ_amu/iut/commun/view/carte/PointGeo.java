package fr.univ_amu.iut.commun.view.carte;

import java.util.Objects;
import javafx.scene.paint.Color;

/// Point à afficher sur la [CarteSites] : un libellé, des coordonnées WGS84 et une couleur (décidée par
/// l'appelant, p. ex. selon le statut workflow des passages du point). Donnée de **présentation**
/// (couche `view`), volontairement agnostique du domaine pour que le composant carte reste réutilisable.
///
/// L'accessibilité (#163) ne repose jamais sur la seule couleur : la couche affiche aussi le [#libelle]
/// (et l'expose en `accessibleText`).
///
/// Un point peut être **approximatif** ([#approximatif]) : ses coordonnées ne sont pas un GPS mesuré mais
/// une position de **substitution** (p. ex. le centre de son carré, faute de GPS saisi). La couche le rend
/// alors distinctement (anneau pointillé) pour ne pas le confondre avec une position réelle.
///
/// @param libelle texte identifiant le point (jamais nul ; sert aussi de libellé accessible)
/// @param latitude latitude WGS84
/// @param longitude longitude WGS84
/// @param couleur couleur du marqueur (jamais nulle)
/// @param infobulle mini-stats affichées au survol (et exposées en `accessibleHelp`, #163) ; `null` = aucune
/// @param approximatif `true` si la position est approchée (centre du carré) et non un GPS mesuré
public record PointGeo(
        String libelle, double latitude, double longitude, Color couleur, String infobulle, boolean approximatif) {

    public PointGeo {
        Objects.requireNonNull(libelle, "libelle");
        Objects.requireNonNull(couleur, "couleur");
    }

    /// Marqueur **géolocalisé** (position réelle) avec info-bulle.
    public PointGeo(String libelle, double latitude, double longitude, Color couleur, String infobulle) {
        this(libelle, latitude, longitude, couleur, infobulle, false);
    }

    /// Marqueur géolocalisé sans info-bulle (forme rétro-compatible).
    public PointGeo(String libelle, double latitude, double longitude, Color couleur) {
        this(libelle, latitude, longitude, couleur, null, false);
    }
}

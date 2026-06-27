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
/// @param libelle texte identifiant le point (jamais nul ; sert aussi de libellé accessible)
/// @param latitude latitude WGS84
/// @param longitude longitude WGS84
/// @param couleur couleur du marqueur (jamais nulle)
/// @param infobulle mini-stats affichées au survol (et exposées en `accessibleHelp`, #163) ; `null` = aucune
public record PointGeo(String libelle, double latitude, double longitude, Color couleur, String infobulle) {

    public PointGeo {
        Objects.requireNonNull(libelle, "libelle");
        Objects.requireNonNull(couleur, "couleur");
    }

    /// Marqueur sans info-bulle (forme rétro-compatible).
    public PointGeo(String libelle, double latitude, double longitude, Color couleur) {
        this(libelle, latitude, longitude, couleur, null);
    }
}

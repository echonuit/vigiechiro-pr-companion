package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.sites.model.PointDEcoute;
import java.util.Optional;

/// Données de présentation d'une carte de point d'écoute sur l'écran M-Site-detail.
///
/// Enveloppe le [PointDEcoute] d'origine (conservé pour l'édition/suppression) et des valeurs utiles à
/// l'affichage : la présence de coordonnées GPS (badge `✓ GPS` ou `⚠ GPS manquant`), le nombre de passages
/// rattachés (qui bloque la suppression du point quand il est non nul) et la **distance au point le plus
/// proche** du même site (#154), pour repérer des points anormalement rapprochés.
///
/// @param point point d'écoute d'origine (code, GPS, descriptif)
/// @param nombrePassages nombre de passages rattachés à ce point
/// @param distanceProcheMetres distance (m) au point géolocalisé le plus proche du même site, ou `null` si
///     ce point n'a pas de GPS ou qu'aucun autre point géolocalisé n'existe
public record CartePoint(PointDEcoute point, int nombrePassages, Double distanceProcheMetres) {

    /// Seuil (m) en dessous duquel deux points sont signalés **« trop proches »** (#154, garde-fou de
    /// protocole). Valeur par défaut prudente, à ajuster selon le protocole : c'est l'unique endroit à
    /// changer pour rendre le seuil configurable.
    public static final double SEUIL_PROXIMITE_METRES = 200.0;

    /// `true` si les deux coordonnées GPS sont renseignées.
    public boolean gpsPresent() {
        return point.latitude() != null && point.longitude() != null;
    }

    /// `true` si au moins un passage est rattaché : la suppression du point est alors bloquée.
    public boolean aDesPassages() {
        return nombrePassages > 0;
    }

    /// Distance (m) au point géolocalisé le plus proche du même site, si elle est connue.
    public Optional<Double> distanceProche() {
        return Optional.ofNullable(distanceProcheMetres);
    }

    /// `true` si un point géolocalisé plus proche que [#SEUIL_PROXIMITE_METRES] existe : à vérifier
    /// (saisie GPS erronée ou points trop rapprochés pour le protocole).
    public boolean tropProche() {
        return distanceProcheMetres != null && distanceProcheMetres < SEUIL_PROXIMITE_METRES;
    }
}

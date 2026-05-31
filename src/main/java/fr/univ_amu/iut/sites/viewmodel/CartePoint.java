package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.sites.model.PointDEcoute;

/// Données de présentation d'une carte de point d'écoute sur l'écran M-Site-detail.
///
/// Enveloppe le [PointDEcoute] d'origine (conservé pour l'édition/suppression) et deux valeurs
/// utiles à l'affichage : la présence de coordonnées GPS (badge `✓ GPS` ou `⚠ GPS manquant`) et
/// le nombre de passages rattachés (qui bloque la suppression du point quand il est non nul).
///
/// @param point point d'écoute d'origine (code, GPS, descriptif)
/// @param nombrePassages nombre de passages rattachés à ce point
public record CartePoint(PointDEcoute point, int nombrePassages) {

  /// `true` si les deux coordonnées GPS sont renseignées.
  public boolean gpsPresent() {
    return point.latitude() != null && point.longitude() != null;
  }

  /// `true` si au moins un passage est rattaché : la suppression du point est alors bloquée.
  public boolean aDesPassages() {
    return nombrePassages > 0;
  }
}

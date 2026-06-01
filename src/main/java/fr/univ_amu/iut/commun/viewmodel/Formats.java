package fr.univ_amu.iut.commun.viewmodel;

/// Formatages d'affichage partagés par les ViewModel des features (libellés dérivés de valeurs
/// numériques). Regroupés dans le socle pour éviter la duplication d'une feature à l'autre.
public final class Formats {

  private Formats() {}

  /// Durée lisible : `X h Y min` au-delà d'une heure, sinon `X min Y s` (arrondi à la seconde).
  ///
  /// @param secondes durée en secondes
  /// @return libellé d'affichage
  public static String dureeLisible(double secondes) {
    long total = Math.round(secondes);
    long heures = total / 3600;
    long minutes = (total % 3600) / 60;
    return heures > 0 ? heures + " h " + minutes + " min" : minutes + " min " + (total % 60) + " s";
  }
}

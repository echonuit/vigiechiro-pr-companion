package fr.univ_amu.iut.commun.viewmodel;

import java.util.Locale;

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

    /// Volume lisible : `Go` (1 décimale) au-delà d'un gigaoctet, sinon `Mo` (entier) au-delà d'un
    /// mégaoctet, sinon `Ko`. Les valeurs négatives sont ramenées à zéro.
    ///
    /// @param octets volume en octets
    /// @return libellé d'affichage (locale FR)
    public static String octetsLisibles(long octets) {
        long valeur = Math.max(0, octets);
        if (valeur >= 1_073_741_824L) {
            return String.format(Locale.FRANCE, "%.1f Go", valeur / 1_073_741_824.0);
        }
        if (valeur >= 1_048_576L) {
            return String.format(Locale.FRANCE, "%.0f Mo", valeur / 1_048_576.0);
        }
        return String.format(Locale.FRANCE, "%d Ko", valeur / 1024);
    }
}

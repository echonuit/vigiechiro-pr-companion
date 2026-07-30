package fr.univ_amu.iut.commun.viewmodel;

import java.util.Locale;

/// Formatages d'affichage partagés par les ViewModel des features (libellés dérivés de valeurs
/// numériques). Regroupés dans le socle pour éviter la duplication d'une feature à l'autre.
public final class Formats {

    /// Le glyphe d'une **valeur absente** dans un tableau : pas de mesure, pas de choix posé, pas de
    /// donnée. C'est un usage **typographique**, celui qu'attend un lecteur de tableau, et non une
    /// ponctuation de phrase.
    ///
    /// La convention d'écriture du dépôt interdit le tiret cadratin (#2365). Elle vise la **ponctuation
    /// de prose**, jamais ce glyphe : le remplacer par un tiret simple dégraderait l'affichage et ferait
    /// rougir les tests qui l'assertent, pour se conformer à une règle qui ne le visait pas.
    ///
    /// D'où cette constante, et le fait qu'elle soit **la seule** occurrence du caractère dans les
    /// sources Java. Le cliquet de l'ADR 2843 peut ainsi exempter une déclaration nommée plutôt qu'un
    /// motif dispersé, et toute réapparition ailleurs redevient un défaut.
    public static final String VALEUR_ABSENTE = "—";

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

    /// Température lisible : `8,5 °C` (1 décimale, virgule décimale FR), ou [#VALEUR_ABSENTE] si non
    /// renseignée (`null`, #106).
    ///
    /// @param celsius température en °C, ou `null`
    /// @return libellé d'affichage
    public static String temperatureLisible(Double celsius) {
        return celsius == null ? VALEUR_ABSENTE : String.format(Locale.FRANCE, "%.1f °C", celsius);
    }

    /// Durée d'une **séquence** : `%.1f s` en locale FR (virgule décimale), ou [#VALEUR_ABSENTE] si non
    /// renseignée (`null`, corrige le NPE latent du formateur privé qu'elle remplace). Distincte de
    /// [#dureeLisible] (durées **cumulées**, « X min Y s » / « X h Y min ») et du formateur de la feature
    /// audio `FormatLigneAudio` (durées de **cris** sub-secondes, format ms/s adaptatif) : trois besoins
    /// d'affichage **assumés**, deux partagés ici et un spécialisé côté audio (non fusionné à dessein).
    ///
    /// @param secondes durée en secondes, ou `null`
    /// @return libellé d'affichage
    public static String dureeSecondes(Double secondes) {
        return secondes == null ? VALEUR_ABSENTE : String.format(Locale.FRANCE, "%.1f s", secondes);
    }
}

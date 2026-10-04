package fr.univ_amu.iut.sites.model;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Lit une position **collée depuis une carte** (#4575), sans réseau.
///
/// Deux formes se lisent, les degrés décimaux et le degré-minute-seconde, toutes deux dans l'ordre
/// **latitude puis longitude** : celui que produisent le « Copier les coordonnées » de Google Maps et
/// le clic droit d'OpenStreetMap.
///
/// **L'ordre ne se devine pas.** Une latitude tient dans plus ou moins 90 et une longitude dans plus ou
/// moins 180 : l'heuristique de plage ne tranche que si l'un des deux nombres dépasse 90. En France
/// métropolitaine la latitude vaut environ 41 à 51 et la longitude environ -5 à 10, donc elle ne tranche
/// jamais là où on en aurait besoin. Deviner par l'emprise du pays réordonnerait en silence une position
/// réellement erronée, et un carré plausible et faux est précisément le défaut qu'on cherche à éviter.
///
/// Ce qui n'est ni l'une ni l'autre se refuse **avec son motif**, jamais en devinant.
public final class PositionCollee {

    /// Ce qui sépare la latitude de la longitude quand chacune porte son cardinal : une virgule facultative.
    private static final String ENTRE_LES_DEUX = "\\s*,?\\s*";

    private static final Pattern DECIMAL =
            Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");

    /// Ce qui trahit un lien plutôt qu'une position : un schéma d'URL, ou un nom d'hôte de carte collé
    /// sans son schéma. La reconnaissance n'a pas à être exhaustive - ce qui lui échappe retombe sur
    /// [LecturePosition.Illisible], dont le motif dit déjà quoi coller.
    private static final Pattern RESSEMBLE_A_UN_LIEN =
            Pattern.compile("(?i)^\\s*(https?://|www\\.|maps\\.|geo:|[a-z0-9.-]+\\.(?:com|org|fr)/)");

    /// Un couple degré-minute-seconde avec ses points cardinaux, tel que le rend le « Copier les
    /// coordonnées » d'une carte : `43°17'47.3"N 5°22'11.2"E`. Les secondes et le séparateur sont
    /// facultatifs, les symboles tolérants : ce qui compte est l'ordre degré, minute, seconde, cardinal.
    ///
    /// Le séparateur ne peut pas être un point : `43°24.06'N` porte des minutes décimales, et le lire
    /// comme 43° 24' 06" le décalerait de 75 m (#5688).
    private static final Pattern DMS =
            Pattern.compile("(?i)^\\s*(\\d+)[^\\d.]+(\\d+)[^\\d.]+(\\d+(?:\\.\\d+)?)\\D*([NS])"
                    + ENTRE_LES_DEUX
                    + "(\\d+)[^\\d.]+(\\d+)[^\\d.]+(\\d+(?:\\.\\d+)?)\\D*([EWO])\\s*$");

    /// Degrés, puis **minutes décimales**, avec le cardinal : `43°24.06'N 5°26.85'E`, la forme des
    /// récepteurs GPS de terrain (#5688).
    private static final Pattern DM = Pattern.compile("(?i)^\\s*(\\d+)[^\\d.]+(\\d+\\.\\d+)\\D*([NS])"
            + ENTRE_LES_DEUX
            + "(\\d+)[^\\d.]+(\\d+\\.\\d+)\\D*([EWO])\\s*$");

    /// Un **décimal suivi de son cardinal** : `43.401 N, 1.574 W` (#5688). Le cardinal porte le signe.
    private static final Pattern DECIMAL_CARDINAL = Pattern.compile("(?i)^\\s*(\\d+(?:\\.\\d+)?)\\s*°?\\s*([NS])"
            + ENTRE_LES_DEUX + "(\\d+(?:\\.\\d+)?)\\s*°?\\s*([EWO])\\s*$");

    /// Deux nombres à **virgule décimale** française : `43,401, 5,447`. Reconnus pour être refusés avec
    /// leur propre motif, la virgule y étant aussi le séparateur de la paire (#5688).
    private static final Pattern VIRGULE_DECIMALE = Pattern.compile("^\\s*-?\\d+,\\d+\\s*[,;]?\\s*-?\\d+,\\d+\\s*$");

    private PositionCollee() {}

    /// Ce que ce texte porte comme position.
    public static LecturePosition lire(String texte) {
        Matcher decimal = DECIMAL.matcher(texte);
        if (decimal.matches()) {
            return new LecturePosition.Lue(Double.parseDouble(decimal.group(1)), Double.parseDouble(decimal.group(2)));
        }
        Matcher cardinal = DECIMAL_CARDINAL.matcher(texte);
        if (cardinal.matches()) {
            return new LecturePosition.Lue(
                    signe(Double.parseDouble(cardinal.group(1)), cardinal.group(2)),
                    signe(Double.parseDouble(cardinal.group(3)), cardinal.group(4)));
        }
        Matcher dm = DM.matcher(texte);
        if (dm.matches()) {
            return new LecturePosition.Lue(
                    enDegres(dm.group(1), dm.group(2), "0", dm.group(3)),
                    enDegres(dm.group(4), dm.group(5), "0", dm.group(6)));
        }
        Matcher dms = DMS.matcher(texte);
        if (dms.matches()) {
            return new LecturePosition.Lue(
                    enDegres(dms.group(1), dms.group(2), dms.group(3), dms.group(4)),
                    enDegres(dms.group(5), dms.group(6), dms.group(7), dms.group(8)));
        }
        if (RESSEMBLE_A_UN_LIEN.matcher(texte).find()) {
            return new LecturePosition.UrlDeCarte();
        }
        if (VIRGULE_DECIMALE.matcher(texte).matches()) {
            return new LecturePosition.VirguleDecimale();
        }
        return new LecturePosition.Illisible();
    }

    /// Un degré-minute-seconde en degrés décimaux. Le cardinal porte le signe : sud et ouest comptent
    /// négativement. `O` est accepté à côté de `W` : une carte en français écrit « Ouest ».
    private static double enDegres(String degres, String minutes, String secondes, String cardinal) {
        return signe(
                Double.parseDouble(degres) + Double.parseDouble(minutes) / 60 + Double.parseDouble(secondes) / 3600,
                cardinal);
    }

    /// Le cardinal porte le signe : sud et ouest comptent négativement, `O` comme `W`.
    private static double signe(double valeur, String cardinal) {
        String vers = cardinal.toUpperCase(Locale.ROOT);
        return "S".equals(vers) || "W".equals(vers) || "O".equals(vers) ? -valeur : valeur;
    }
}

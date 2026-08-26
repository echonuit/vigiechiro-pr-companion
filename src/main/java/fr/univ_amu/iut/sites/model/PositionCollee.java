package fr.univ_amu.iut.sites.model;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Lit une position **collée depuis une carte** (#4575), sans réseau.
public final class PositionCollee {

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
    private static final Pattern DMS = Pattern.compile("(?i)^\\s*(\\d+)\\D+(\\d+)\\D+(\\d+(?:\\.\\d+)?)\\D*([NS])"
            + "\\s*,?\\s*"
            + "(\\d+)\\D+(\\d+)\\D+(\\d+(?:\\.\\d+)?)\\D*([EWO])\\s*$");

    private PositionCollee() {}

    /// Ce que ce texte porte comme position.
    public static LecturePosition lire(String texte) {
        Matcher decimal = DECIMAL.matcher(texte);
        if (decimal.matches()) {
            return new LecturePosition.Lue(Double.parseDouble(decimal.group(1)), Double.parseDouble(decimal.group(2)));
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
        return new LecturePosition.Illisible();
    }

    /// Un degré-minute-seconde en degrés décimaux. Le cardinal porte le signe : sud et ouest comptent
    /// négativement. `O` est accepté à côté de `W` : une carte en français écrit « Ouest ».
    private static double enDegres(String degres, String minutes, String secondes, String cardinal) {
        double valeur =
                Double.parseDouble(degres) + Double.parseDouble(minutes) / 60 + Double.parseDouble(secondes) / 3600;
        String vers = cardinal.toUpperCase(Locale.ROOT);
        return "S".equals(vers) || "W".equals(vers) || "O".equals(vers) ? -valeur : valeur;
    }
}

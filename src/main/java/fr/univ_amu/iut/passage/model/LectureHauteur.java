package fr.univ_amu.iut.passage.model;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Lecture d'une hauteur de micro saisie en texte libre sur la plateforme (#6211).
///
/// Le formulaire web demande des mètres, mais la saisie est libre : la base porte la virgule décimale
/// (« 2,5 »), une unité (« 2,5m », « 250cm », « 2m50 ») et des centimètres sans unité (« 250 »). Mesuré
/// le 14 octobre 2026 sur les 52 741 hauteurs Point Fixe, cette lecture n'en laisse que 186 sans valeur.
final class LectureHauteur {

    /// À partir de cette valeur, une hauteur sans unité est en centimètres. Décidé par le porteur le
    /// 14 octobre 2026 : un micro à plus de 30 m n'est pas plausible, et la base porte 1 778 hauteurs
    /// entre 30 et 1000 que la lecture en mètres rendait absurdes. La tranche de 10 à 30, ambiguë, reste
    /// lue en mètres.
    static final double SEUIL_CENTIMETRES = 30;

    /// Un nombre, une virgule ou un point décimal, puis une unité facultative.
    private static final Pattern NOMBRE_ET_UNITE = Pattern.compile(
            "^(\\d+(?:[.,]\\d+)?)\\.?\\s*(cm|m|mètres?|metres?)?\\.?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /// Des mètres puis leur fraction après la lettre : « 2m50 », « 1m5 ».
    private static final Pattern METRES_ET_FRACTION =
            Pattern.compile("^(\\d+)\\s*m\\s*(\\d{1,2})$", Pattern.CASE_INSENSITIVE);

    private LectureHauteur() {}

    /// La hauteur en mètres, ou vide si `texte` est absent, blanc, négatif ou d'une forme que cette lecture
    /// ne prend pas (« 2-3 », « 2? »).
    static Optional<Double> enMetres(String texte) {
        if (texte == null || texte.isBlank()) {
            return Optional.empty();
        }
        String saisie = texte.strip();
        Matcher fraction = METRES_ET_FRACTION.matcher(saisie);
        if (fraction.matches()) {
            return Optional.of(Double.parseDouble(fraction.group(1) + "." + fraction.group(2)));
        }
        Matcher nombre = NOMBRE_ET_UNITE.matcher(saisie);
        if (!nombre.matches()) {
            return Optional.empty();
        }
        double valeur = Double.parseDouble(nombre.group(1).replace(',', '.'));
        String unite = nombre.group(2) == null ? "" : nombre.group(2).toLowerCase(Locale.ROOT);
        if ("cm".equals(unite) || (unite.isEmpty() && valeur >= SEUIL_CENTIMETRES)) {
            return Optional.of(valeur / 100);
        }
        return Optional.of(valeur);
    }
}

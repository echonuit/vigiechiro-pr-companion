package fr.univ_amu.iut.commun.model.validation;

import java.util.regex.Pattern;

/// Validation d'un code de point d'écoute (R2).
///
/// Un code de point vaut **une lettre majuscule suivie d'un ou plusieurs chiffres** (ex. `A1`, `Z4`,
/// `Z41`). Le numéro peut comporter **plusieurs chiffres**. La validation se fait à la saisie.
public final class ValidateurCodePoint {

    private static final Pattern LETTRE_CHIFFRES = Pattern.compile("[A-Z][0-9]+");

    private ValidateurCodePoint() {}

    /// `true` si `code` est une lettre majuscule suivie d'un ou plusieurs chiffres.
    public static boolean estValide(String code) {
        return code != null && LETTRE_CHIFFRES.matcher(code).matches();
    }

    /// Vérifie `code` et le renvoie inchangé, ou lève une [IllegalArgumentException] si la
    /// règle R2 n'est pas respectée.
    public static String exigerValide(String code) {
        if (!estValide(code)) {
            throw new IllegalArgumentException(
                    "Code de point invalide (une lettre majuscule + un ou plusieurs chiffres, ex. A1, Z41) : " + code);
        }
        return code;
    }
}

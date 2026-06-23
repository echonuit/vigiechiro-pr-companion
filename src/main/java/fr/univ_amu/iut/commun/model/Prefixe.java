package fr.univ_amu.iut.commun.model;

import java.util.Locale;
import java.util.regex.Pattern;

/// Construit le préfixe de nommage des fichiers et dossiers d'une session (R6/R7/R8).
///
/// Forme : `Car<carré>-<année>-Pass<n>-<point>`. Les tirets sont impérativement des
/// **tirets du 6** (`'-'`, U+002D HYPHEN-MINUS), jamais des cadratins/demi-cadratins (R6).
///
/// - [#nomDossierSession()] : nom du sous-dossier de session (R22), sans tiret final.
/// - [#prefixeFichier()] : préfixe ajouté devant le nom de fichier (R6), avec tiret final.
/// - [#nommerOriginal(String)] : préfixe + suffixe de l'enregistreur conservé tel quel
/// (R7).
/// - [#nommerSequence(String, int)] : insère le suffixe `_000`, `_001`… (R8).
///
/// @param carre numéro de carré (6 chiffres)
/// @param annee année à 4 chiffres
/// @param numeroPassage numéro de passage
/// @param codePoint code du point d'écoute (lettre + chiffre)
public record Prefixe(String carre, int annee, int numeroPassage, String codePoint) {

    /// Tiret du 6 imposé par R6 (U+002D HYPHEN-MINUS).
    public static final char TIRET = '-';

    /// Grammaire d'un nom **déjà préfixé** R6 : `Car<carré>-<année>-Pass<n°>-<point>-<suffixe>` (tirets
    /// U+002D). On reconnaît la **grammaire complète**, pas le simple marqueur `Car` : un fichier comme
    /// `Carto_20260422.wav` ou `Car_old.wav` n'est donc **pas** considéré comme préfixé.
    private static final Pattern MOTIF_PREFIXE_R6 = Pattern.compile("Car\\d+-\\d{4}-Pass\\d+-[^-]+-.+");

    /// `true` si `nomFichier` porte déjà un préfixe R6 valide (cf. [#MOTIF_PREFIXE_R6]). **Source unique
    /// de vérité** partagée par l'inspection (état de nommage), le renommage (idempotence : ne jamais
    /// re-préfixer, donc pas de double préfixe `Car…-Car…`, #111) et l'aperçu (nom conservé si déjà
    /// préfixé).
    public static boolean estNomPrefixe(String nomFichier) {
        return MOTIF_PREFIXE_R6.matcher(nomFichier).matches();
    }

    /// Nom du dossier de session (R22), sans tiret final : `Car040962-2026-Pass1-A1`.
    public String nomDossierSession() {
        return "Car" + carre + TIRET + annee + TIRET + "Pass" + numeroPassage + TIRET + codePoint;
    }

    /// Préfixe de fichier (R6), avec tiret final : `Car040962-2026-Pass1-A1-`.
    public String prefixeFichier() {
        return nomDossierSession() + TIRET;
    }

    /// Nom d'un enregistrement original (R7) : préfixe + suffixe de l'enregistreur conservé tel quel.
    ///
    /// @param suffixeEnregistreur ex. `PaRecPR1925492_20260615_223015.wav`
    public String nommerOriginal(String suffixeEnregistreur) {
        return prefixeFichier() + suffixeEnregistreur;
    }

    /// Nom d'une séquence d'écoute (R8) : insère `_NNN` (3 chiffres) entre la base du nom et
    /// l'extension de l'original.
    ///
    /// @param nomOriginal nom de l'enregistrement original source (issu de [#nommerOriginal(String)])
    /// @param index index de la séquence dans l'original (≥ 0)
    public String nommerSequence(String nomOriginal, int index) {
        String suffixe = String.format(Locale.ROOT, "_%03d", index);
        int point = nomOriginal.lastIndexOf('.');
        if (point < 0) {
            return nomOriginal + suffixe;
        }
        return nomOriginal.substring(0, point) + suffixe + nomOriginal.substring(point);
    }
}

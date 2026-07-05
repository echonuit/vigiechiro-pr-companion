package fr.univ_amu.iut.commun.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
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

    /// Horodatage `_AAAAMMJJ_HHMMSS` d'un nom d'enregistreur (ex. `..._20260422_225849.wav`).
    private static final Pattern MOTIF_HORODATAGE = Pattern.compile("_(\\d{8})_(\\d{6})");

    /// Format de l'horodatage des noms d'enregistreur (date puis heure, séparées par `_`).
    private static final DateTimeFormatter FORMAT_HORODATAGE =
            DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmmss", Locale.ROOT);

    /// Nom d'une séquence d'écoute **horodatée** (R8, convention Vigie-Chiro/Tadarida) : chaque tranche de
    /// 5 s réelles porte l'**heure réelle de son début** (horodatage de l'original **décalé** de
    /// `decalageSecondes`) et un suffixe `_000` systématique — et non un index `_000`, `_001`… C'est
    /// indispensable pour que les lignes de l'`observations.csv` (qui référencent ces noms décalés) se
    /// raccrochent à la bonne tranche audio. Exemple : `..._20260422_225849.wav`, tranche d'index 2
    /// (`decalageSecondes = 10`) → `..._20260422_225859_000.wav`.
    ///
    /// Le décalage traverse correctement minutes/heures/jours (arithmétique [LocalDateTime]). Si le nom ne
    /// porte pas d'horodatage reconnu (cas de test ou nom non standard), on **retombe** sur le suffixe
    /// indexé historique [#nommerSequence(String, int)].
    ///
    /// @param nomOriginal nom de l'enregistrement original (préfixé R6, avec horodatage `_AAAAMMJJ_HHMMSS`)
    /// @param index index de la tranche (≥ 0), utilisé seulement pour le repli sans horodatage
    /// @param decalageSecondes décalage à appliquer à l'horodatage (= `index × durée de séquence`)
    public String nommerSequence(String nomOriginal, int index, int decalageSecondes) {
        int point = nomOriginal.lastIndexOf('.');
        String base = point < 0 ? nomOriginal : nomOriginal.substring(0, point);
        String extension = point < 0 ? "" : nomOriginal.substring(point);
        String baseDecalee = decalerHorodatage(base, decalageSecondes);
        if (baseDecalee == null) {
            return nommerSequence(nomOriginal, index); // repli : pas d'horodatage → suffixe indexé
        }
        return baseDecalee + "_000" + extension;
    }

    /// Extrait l'**horodatage de capture** `_AAAAMMJJ_HHMMSS` porté par un nom de fichier de séquence (R7/R8)
    /// : le nom d'une tranche de 5 s porte l'heure réelle de son début. On prend le **dernier** horodatage du
    /// nom (comme [#decalerHorodatage], pour éviter toute ambiguïté avec des chiffres du préfixe R6).
    /// `Optional.empty()` si le nom est `null` ou ne porte pas d'horodatage reconnu (nom de test / non
    /// standard). Parseur canonique partagé (extraction à l'import, backfill).
    public static Optional<LocalDateTime> horodatageDe(String nomFichier) {
        if (nomFichier == null) {
            return Optional.empty();
        }
        Matcher m = MOTIF_HORODATAGE.matcher(nomFichier);
        String horodatage = null;
        while (m.find()) {
            horodatage = m.group(1) + "_" + m.group(2);
        }
        if (horodatage == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(horodatage, FORMAT_HORODATAGE));
        } catch (RuntimeException invalide) {
            return Optional.empty();
        }
    }

    /// Décale le **dernier** horodatage `_AAAAMMJJ_HHMMSS` de `base` de `decalageSecondes`, ou `null` si
    /// aucun horodatage n'est reconnu. Le « dernier » évite toute ambiguïté avec des chiffres du préfixe.
    private static String decalerHorodatage(String base, int decalageSecondes) {
        Matcher m = MOTIF_HORODATAGE.matcher(base);
        int debut = -1;
        int fin = -1;
        String horodatage = null;
        while (m.find()) {
            debut = m.start();
            fin = m.end();
            horodatage = m.group(1) + "_" + m.group(2);
        }
        if (horodatage == null) {
            return null;
        }
        String decale = LocalDateTime.parse(horodatage, FORMAT_HORODATAGE)
                .plusSeconds(decalageSecondes)
                .format(FORMAT_HORODATAGE);
        return base.substring(0, debut) + "_" + decale + base.substring(fin);
    }
}

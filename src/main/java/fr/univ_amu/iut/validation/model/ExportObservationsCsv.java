package fr.univ_amu.iut.validation.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/// Export **CSV** des observations audio (#149), pour l'exploitation hors application (analyse, interop
/// tableur). Distinct du CSV `_Vu` réinjectable (dépôt Tadarida) : ici on **aplatit les colonnes affichées**
/// (carré, point, site, date, espèce, statut, fréquence, commentaire…) d'un jeu de lignes — typiquement le
/// **sous-ensemble filtré** de la vue « Sons & validation ». Formateur **pur** (aucune dépendance IHM),
/// réutilisable par le CLI (#618).
///
/// **Format ouvrable directement par un tableur français** : UTF-8 **avec BOM** (Excel reconnaît l'encodage),
/// séparateur **`;`** (le séparateur de liste par défaut d'Excel FR) et décimales à la **virgule**. Les champs
/// contenant `;`, un guillemet ou un saut de ligne sont entourés de guillemets (doublés à l'intérieur).
public final class ExportObservationsCsv {

    private static final String BOM = "\uFEFF";
    private static final char SEPARATEUR = ';';
    private static final String FIN_LIGNE = "\r\n";

    private static final List<String> ENTETES = List.of(
            "Carré",
            "Point",
            "Site",
            "Passage",
            "Date",
            "Fichier",
            "Taxon Tadarida",
            "Proba Tadarida",
            "Votre taxon",
            "Nom espèce",
            "Groupe",
            "Statut",
            "Référence",
            "Fréquence médiane (kHz)",
            "Début (s)",
            "Fin (s)",
            "Commentaire");

    private ExportObservationsCsv() {}

    /// Écrit le CSV des `lignes` dans `destination` (UTF-8 + BOM). Renvoie le fichier écrit.
    public static Path ecrire(List<LigneObservationAudio> lignes, Path destination) throws IOException {
        Files.writeString(destination, contenu(lignes), StandardCharsets.UTF_8);
        return destination;
    }

    /// Contenu CSV complet (BOM + en-têtes + une ligne par observation).
    public static String contenu(List<LigneObservationAudio> lignes) {
        StringBuilder csv = new StringBuilder(BOM);
        ajouterLigne(csv, ENTETES);
        for (LigneObservationAudio ligne : lignes) {
            ajouterLigne(csv, champs(ligne));
        }
        return csv.toString();
    }

    private static List<String> champs(LigneObservationAudio l) {
        return List.of(
                texte(l.numeroCarre()),
                texte(l.codePoint()),
                texte(l.nomSite()),
                Integer.toString(l.numeroPassage()),
                texte(l.dateEnregistrement()),
                texte(l.nomFichier()),
                texte(l.taxonTadarida()),
                proba(l.probTadarida()),
                texte(l.taxonObservateur()),
                texte(l.nomEspece()),
                texte(l.groupe()),
                statut(l.statut()),
                l.reference() ? "oui" : "non",
                l.frequenceKHz() == null ? "" : Integer.toString(l.frequenceKHz()),
                secondes(l.debutS()),
                secondes(l.finS()),
                texte(l.commentaire()));
    }

    private static void ajouterLigne(StringBuilder csv, List<String> valeurs) {
        for (int i = 0; i < valeurs.size(); i++) {
            if (i > 0) {
                csv.append(SEPARATEUR);
            }
            csv.append(echapper(valeurs.get(i)));
        }
        csv.append(FIN_LIGNE);
    }

    /// Échappe une valeur : entourée de guillemets (doublés à l'intérieur) si elle contient le séparateur,
    /// un guillemet ou un saut de ligne.
    private static String echapper(String valeur) {
        if (valeur.indexOf(SEPARATEUR) < 0
                && valeur.indexOf('"') < 0
                && valeur.indexOf('\n') < 0
                && valeur.indexOf('\r') < 0) {
            return valeur;
        }
        return '"' + valeur.replace("\"", "\"\"") + '"';
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur;
    }

    private static String proba(Double probabilite) {
        return probabilite == null ? "" : String.format(Locale.FRENCH, "%.2f", probabilite);
    }

    private static String secondes(Double valeur) {
        return valeur == null ? "" : String.format(Locale.FRENCH, "%.2f", valeur);
    }

    private static String statut(StatutObservation statut) {
        return switch (statut) {
            case NON_TOUCHEE -> "À revoir";
            case VALIDEE -> "Validée";
            case CORRIGEE -> "Corrigée";
        };
    }
}

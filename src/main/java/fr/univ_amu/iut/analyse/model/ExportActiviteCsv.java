package fr.univ_amu.iut.analyse.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/// Export **CSV** de la courbe d'activité horaire (#2352, lot 2 du chantier #2348), pour l'exploitation
/// hors application (analyse, interopérabilité tableur). Facette **données** de l'export, en pendant de
/// l'export **image** de l'IHM : la même agrégation ([AgregationActivite]) sort ici en tableau plutôt qu'en
/// graphe. Utilisé par le CLI (`exporter-activite`), formateur **pur** (aucune dépendance IHM).
///
/// **Une ligne par (carré, point, nuit, espèce, tranche)**, en format long : robuste au nombre variable
/// d'espèces et directement filtrable dans un tableur. Chaque ligne **porte son contexte entier** — d'où
/// elle vient, quand, et à quelle largeur de tranche —, de sorte qu'un export couvrant plusieurs nuits ou
/// plusieurs points reste recoupable une fois détaché de l'application (#2613). La **nuit** est
/// recalculée par la **bascule à midi** ([Nuit#de]), la même règle que la courbe : une tranche à 02 h
/// appartient à la nuit du soir précédent, pas à la date du jour.
///
/// **Format ouvrable directement par un tableur français** : UTF-8 **avec BOM**, séparateur **`;`**, fins de
/// ligne `\r\n`, comme [fr.univ_amu.iut.validation.model.ExportObservationsCsv].
public final class ExportActiviteCsv {

    private static final String BOM = "\uFEFF";
    private static final char SEPARATEUR = ';';
    private static final String FIN_LIGNE = "\r\n";

    private static final List<String> ENTETES = List.of(
            "Carré",
            "Point",
            "Nuit",
            "Code espèce",
            "Nom espèce",
            "Groupe",
            "Début tranche",
            "Tranche (min)",
            "Contacts");

    private ExportActiviteCsv() {}

    /// Écrit le CSV de l'activité (BOM + en-têtes + une ligne par point de chaque courbe) dans `destination`.
    /// Renvoie le fichier écrit.
    public static Path ecrire(LargeurTranche tranche, List<LigneActivite> lignes, Path destination) throws IOException {
        Files.writeString(destination, contenu(tranche, lignes), StandardCharsets.UTF_8);
        return destination;
    }

    /// Contenu CSV complet. Un passage sans contact produit les en-têtes seuls (résultat valide, comme
    /// `exporter-observations`).
    public static String contenu(LargeurTranche tranche, List<LigneActivite> lignes) {
        StringBuilder csv = new StringBuilder(BOM);
        ajouterLigne(csv, ENTETES);
        for (LigneActivite ligne : lignes) {
            ajouterLigne(csv, champs(tranche, ligne));
        }
        return csv.toString();
    }

    private static List<String> champs(LargeurTranche tranche, LigneActivite ligne) {
        return List.of(
                texte(ligne.numeroCarre()),
                texte(ligne.codePoint()),
                ligne.nuit().toString(),
                texte(ligne.taxon()),
                texte(ligne.nomEspece()),
                texte(ligne.groupe()),
                ligne.debutTranche().toString(),
                Integer.toString(tranche.minutes()),
                Integer.toString(ligne.nombre()));
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

    /// Échappe une valeur : entourée de guillemets (doublés à l'intérieur) si elle contient le séparateur, un
    /// guillemet ou un saut de ligne.
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
}

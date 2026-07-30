package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.Certitude;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/// Export **CSV** des observations audio (#149), pour l'exploitation hors application (analyse, interop
/// tableur). Distinct du CSV `_Vu` réinjectable (dépôt Tadarida) : ici on **aplatit les colonnes affichées**
/// (carré, point, site, date, espèce, statut, fréquence, commentaire…) d'un jeu de lignes : typiquement le
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
            // Commune du point (#2791) : le nom de lieu opposable, dérivé du GPS - celui
            // qu'un expert cherche pour situer une observation sans connaître les sites.
            "Commune",
            "Passage",
            "Date",
            "Fichier",
            "Taxon Tadarida",
            "Proba Tadarida",
            "Votre taxon",
            // Les TROIS avis, au complet (#1417). L'écran les montre côte à côte ; un export qui n'en
            // porterait que deux ferait perdre le verdict de l'expert à qui ouvre le fichier dans un
            // tableur - et lui laisserait croire que sa propre correction est le dernier mot.
            "Votre certitude",
            "Avis du validateur",
            "Certitude du validateur",
            // Le fil ne s'exporte pas (il se lit dans l'application), mais son EXISTENCE doit se voir :
            // sans ce compteur, une discussion ouverte par un validateur serait invisible hors de l'app.
            "Messages",
            "Nom espèce",
            "Groupe",
            "Statut",
            "Référence",
            "Douteux",
            // Espèce à enjeu (#2353) : le repère de l'écran doit survivre à la sortie du fichier. Sans
            // cette colonne, qui ouvre l'export dans un tableur perd l'information qu'un naturaliste
            // cherche en premier, et devrait la reconstituer à la main depuis une liste externe.
            "Espèce à enjeu",
            "Fréquence médiane (kHz)",
            "Début (s)",
            "Fin (s)",
            "Commentaire");

    private ExportObservationsCsv() {}

    /// Écrit le CSV des `lignes` dans `destination` (UTF-8 + BOM). Renvoie le fichier écrit.
    public static Path ecrire(List<LigneObservationAudio> lignes, Path destination) throws IOException {
        return ecrire(lignes, destination, taxon -> false);
    }

    /// Écrit le CSV en renseignant la colonne « Espèce à enjeu » d'après le référentiel (#2353).
    public static Path ecrire(List<LigneObservationAudio> lignes, Path destination, Predicate<String> aEnjeu)
            throws IOException {
        Files.writeString(destination, contenu(lignes, aEnjeu), StandardCharsets.UTF_8);
        return destination;
    }

    /// Contenu CSV complet (BOM + en-têtes + une ligne par observation).
    public static String contenu(List<LigneObservationAudio> lignes) {
        return contenu(lignes, taxon -> false);
    }

    /// Contenu CSV complet, avec le **référentiel de conservation** pour renseigner la colonne « Espèce à
    /// enjeu » (#2353). Le prédicat reçoit le **code du taxon retenu** de chaque ligne.
    public static String contenu(List<LigneObservationAudio> lignes, Predicate<String> aEnjeu) {
        StringBuilder csv = new StringBuilder(BOM);
        ajouterLigne(csv, ENTETES);
        for (LigneObservationAudio ligne : lignes) {
            ajouterLigne(csv, champs(ligne, aEnjeu));
        }
        return csv.toString();
    }

    private static List<String> champs(LigneObservationAudio l, Predicate<String> aEnjeu) {
        return List.of(
                texte(l.numeroCarre()),
                texte(l.codePoint()),
                texte(l.nomSite()),
                texte(l.commune()),
                Integer.toString(l.numeroPassage()),
                texte(l.dateEnregistrement()),
                texte(l.nomFichier()),
                texte(l.taxonTadarida()),
                proba(l.probTadarida()),
                texte(l.taxonObservateur()),
                certitude(l.certitude()),
                texte(l.taxonValidateur()),
                certitude(l.certitudeValidateur()),
                Integer.toString(l.nbMessages()),
                texte(l.nomEspece()),
                texte(l.groupe()),
                statut(l.statut()),
                ouiNon(l.reference()),
                ouiNon(l.douteux()),
                ouiNon(aEnjeu.test(l.taxonRetenu())),
                l.frequenceKHz() == null ? "" : Integer.toString(l.frequenceKHz()),
                secondes(l.debutS()),
                secondes(l.finS()),
                texte(l.commentaire()));
    }

    /// Les drapeaux booléens du CSV, en toutes lettres : un tableur lit « oui »/« non » là où `true` /
    /// `false` demanderait de connaître la convention du fichier.
    private static String ouiNon(boolean vrai) {
        return vrai ? "oui" : "non";
    }

    /// Certitude déclarée (`Sûr` / `Probable` / `Possible`), ou vide si non renseignée : pour l'observateur
    /// comme pour le validateur : côté serveur, c'est la même énumération (contrat #1203).
    private static String certitude(Certitude certitude) {
        return certitude == null ? "" : certitude.libelle();
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

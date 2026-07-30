package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.commun.model.ContexteActivite;
import fr.univ_amu.iut.commun.model.ReferentielActivite;
import fr.univ_amu.iut.commun.model.SeuilsActivite;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/// Écrit la **synthèse d'une nuit** en CSV (#2351).
///
/// ## Ce que ce fichier emporte avec lui
///
/// Un CSV quitte l'application pour vivre dans un tableur, loin de l'écran qui portait la mise en garde.
/// Trois choses l'accompagnent donc, en tête de fichier :
///
/// 1. **le contexte** : à quoi les nombres ont été comparés (déclinaison, saison, périmètre) ;
/// 2. **l'avertissement** : ce qu'une classe d'activité ne dit pas ;
/// 3. **la citation** : la source est libre d'usage *avec citation obligatoire*.
///
/// C'est la règle que la maquette M-Synthese formule le mieux : **si l'avertissement ne voyage pas avec
/// la donnée, il ne sert à rien**. Un tableur ouvert trois mois plus tard, par quelqu'un qui n'a jamais
/// vu l'écran, doit pouvoir savoir d'où sortent ces classes et ce qu'elles valent.
///
/// Les lignes de contexte sont **préfixées par `#`** : les tableurs les affichent comme du texte, et un
/// script qui lit le fichier peut les sauter d'une règle triviale, sans les perdre pour autant.
public final class ExportSyntheseCsv {

    private static final String SEPARATEUR = ";";
    // Échappé plutôt qu'écrit tel quel : un BOM littéral est invisible dans l'éditeur, donc indétectable
    // à la relecture et fragile au moindre copier-coller.
    private static final String BOM = "\uFEFF";
    private static final String COMMENTAIRE = "# ";

    private static final List<String> ENTETES = List.of(
            "Code espèce",
            "Nom espèce",
            "Groupe",
            "Contacts",
            "Fichiers",
            "Activité",
            "Q25",
            "Q75",
            "Q98",
            "Déclinaison retenue",
            "Saison retenue",
            "Occurrences du référentiel",
            "Fiabilité");

    private ExportSyntheseCsv() {}

    /// Écrit le CSV dans `destination` et renvoie le fichier écrit.
    public static Path ecrire(List<LigneSynthese> lignes, ContexteActivite contexte, Path destination)
            throws IOException {
        Files.writeString(destination, contenu(lignes, contexte), StandardCharsets.UTF_8);
        return destination;
    }

    /// Contenu complet : bloc de contexte, avertissement, citation, puis en-têtes et lignes.
    ///
    /// Une nuit sans espèce produit le bloc de tête et les en-têtes seuls : résultat valide, et bien plus
    /// parlant qu'un fichier vide.
    public static String contenu(List<LigneSynthese> lignes, ContexteActivite contexte) {
        StringBuilder csv = new StringBuilder(BOM);
        csv.append(COMMENTAIRE)
                .append("Synthèse d'une nuit - VigieChiro Companion")
                .append(System.lineSeparator());
        csv.append(COMMENTAIRE)
                .append("Comparé au référentiel : ")
                .append(contexte.libelle())
                .append(System.lineSeparator());
        csv.append(COMMENTAIRE).append(ReferentielActivite.AVERTISSEMENT).append(System.lineSeparator());
        csv.append(COMMENTAIRE)
                .append("Source : ")
                .append(ReferentielActivite.CITATION)
                .append(System.lineSeparator());
        csv.append(COMMENTAIRE).append(System.lineSeparator());
        ajouterLigne(csv, ENTETES);
        for (LigneSynthese ligne : lignes) {
            ajouterLigne(csv, champs(ligne));
        }
        return csv.toString();
    }

    /// Une ligne du tableau. Les colonnes de seuils restent **vides** quand il n'y a pas de classe : dans
    /// un CSV, une cellule vide se lit comme une absence de donnée, ce qui est exactement le cas, et la
    /// colonne « Activité », elle, **dit pourquoi** ([LigneSynthese#libelleClasse()]).
    private static List<String> champs(LigneSynthese ligne) {
        return List.of(
                texte(ligne.codeTaxon()),
                texte(ligne.nomEspece()),
                texte(ligne.groupe()),
                Integer.toString(ligne.contacts()),
                Integer.toString(ligne.fichiers()),
                ligne.libelleClasse(),
                quantile(ligne, SeuilsActivite::q25),
                quantile(ligne, SeuilsActivite::q75),
                quantile(ligne, SeuilsActivite::q98),
                ligne.seuils().map(SeuilsActivite::declinaison).orElse(""),
                ligne.seuils().map(SeuilsActivite::saison).orElse(""),
                ligne.seuils()
                        .map(seuils -> Integer.toString(seuils.occurrences()))
                        .orElse(""),
                ligne.seuils().map(seuils -> seuils.confiance().name()).orElse(""));
    }

    private static String quantile(LigneSynthese ligne, java.util.function.ToIntFunction<SeuilsActivite> lecture) {
        return ligne.seuils()
                .map(seuils -> Integer.toString(lecture.applyAsInt(seuils)))
                .orElse("");
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur;
    }

    private static void ajouterLigne(StringBuilder csv, List<String> valeurs) {
        for (int i = 0; i < valeurs.size(); i++) {
            if (i > 0) {
                csv.append(SEPARATEUR);
            }
            csv.append(echapper(valeurs.get(i)));
        }
        csv.append(System.lineSeparator());
    }

    /// Échappement CSV : une valeur contenant le séparateur, un guillemet ou un saut de ligne est mise
    /// entre guillemets, les guillemets internes étant doublés.
    private static String echapper(String valeur) {
        if (valeur.contains(SEPARATEUR) || valeur.contains("\"") || valeur.contains("\n")) {
            return "\"" + valeur.replace("\"", "\"\"") + "\"";
        }
        return valeur;
    }
}

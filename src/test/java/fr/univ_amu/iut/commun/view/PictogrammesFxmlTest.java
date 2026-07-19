package fr.univ_amu.iut.commun.view;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/// Garde-fou « un pictogramme d'IHM se pose en [org.kordamp.ikonli.javafx.FontIcon], pas en
/// caractère », par analyse statique des `.fxml` (#1933, règle #700).
///
/// Un caractère écrit dans un libellé dépend des **polices installées** sur la machine : selon le
/// système il tombe en rectangle vide, en noir et blanc, ou en emoji couleur pleine taille qui
/// déséquilibre la ligne - et il ne se **teinte** pas avec le texte, donc il ne peut pas suivre un
/// état. Le chantier #1933 a converti 35 glyphes sur 17 vues ; sans cliquet, l'usage littéral
/// reviendrait comme il était déjà revenu après #700.
///
/// Le défaut ne se voit ni à la compilation ni à l'exécution sur la machine qui l'écrit : c'est
/// justement pourquoi il a besoin d'un test. La preuve tient en une capture - le `🔍` du champ de
/// recherche s'affichait chez le développeur et **manquait** sur les aperçus régénérés en CI, sans
/// que rien ne rougisse.
///
/// ## Où passe la frontière
///
/// Le chantier a tranché la question restée ouverte à son ouverture : ce qui **désigne une action ou
/// un objet** est une icône ; ce qui **vit dans une phrase** reste un caractère. Mécaniquement :
///
///  - les **flèches** (U+2190-U+21FF) et les **opérateurs mathématiques** (U+2200-U+22FF) sont de la
///    typographie : `A → B`, `≥ 1 mois`, une longitude négative. Ils sont tolérés **à condition de ne
///    pas constituer à eux seuls le libellé** - une flèche seule sur un bouton est une icône qui
///    s'ignore ;
///  - tout le reste (`☰`, `✕`, `🔍`, `📤`, `♻`, `☁`, `✏`, `🗑`…) est un pictogramme, et se pose en
///    `FontIcon`.
///
/// La frontière est volontairement tracée par **bloc Unicode** et non par liste de caractères : une
/// liste ne dirait rien du caractère suivant, alors que la question « est-ce de la typographie ? » se
/// repose à chaque ajout.
///
/// Ce garde-fou ne couvre que les **FXML**. Les libellés bâtis en Java restent à traiter (#1564), et
/// la **CLI** est hors sujet : une console ne rend pas de `FontIcon`, `⚠` y est le seul moyen d'écrire
/// un avertissement.
class PictogrammesFxmlTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    private static final Pattern COMMENTAIRE = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern VALEUR_ATTRIBUT = Pattern.compile("\\b[A-Za-z_][\\w.:-]*\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern CONTENU_ELEMENT = Pattern.compile(">([^<>]+)<");

    static Stream<Path> fichiersFxml() throws Exception {
        try (Stream<Path> chemins = Files.walk(SOURCE)) {
            return chemins.filter(p -> p.toString().endsWith(".fxml")).sorted().toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fichiersFxml")
    @DisplayName("Aucun pictogramme littéral : les icônes se posent en FontIcon")
    void les_pictogrammes_se_posent_en_fonticone(Path fxml) throws Exception {
        String contenu = COMMENTAIRE.matcher(Files.readString(fxml)).replaceAll("");
        String nom = SOURCE.relativize(fxml).toString();

        SoftAssertions verifs = new SoftAssertions();
        for (String texte : textes(contenu)) {
            List<String> fautifs = pictogrammes(texte);
            verifs.assertThat(fautifs)
                    .as(
                            "%s : le libellé « %s » porte le(s) pictogramme(s) %s en dur. Un caractère dépend des "
                                    + "polices de la machine (il manque sur les aperçus régénérés en CI) et ne se teinte "
                                    + "pas avec le texte : pose une <FontIcon iconLiteral=\"fas-…\"/> dans le <graphic> du "
                                    + "nœud, et laisse le texte au texte (#1933). Si c'est de la typographie dans une "
                                    + "phrase (une flèche « A → B », un signe moins), utilise le caractère Unicode de la "
                                    + "bonne famille : flèches U+2190-U+21FF, opérateurs U+2200-U+22FF.",
                            nom, texte, fautifs)
                    .isEmpty();
        }
        verifs.assertAll();
    }

    /// Tout ce qu'un FXML peut afficher : valeurs d'attributs et contenus d'éléments. On ne se limite
    /// pas à `text=` - `promptText` a précisément été l'angle mort du chantier.
    private static List<String> textes(String contenu) {
        List<String> trouves = new ArrayList<>();
        Matcher attribut = VALEUR_ATTRIBUT.matcher(contenu);
        while (attribut.find()) {
            trouves.add(attribut.group(1));
        }
        Matcher element = CONTENU_ELEMENT.matcher(contenu);
        while (element.find()) {
            trouves.add(element.group(1));
        }
        return trouves;
    }

    private static List<String> pictogrammes(String texte) {
        List<String> fautifs = new ArrayList<>();
        texte.codePoints().forEach(point -> {
            if (estPictogramme(point, texte)) {
                fautifs.add(Character.toString(point));
            }
        });
        return fautifs;
    }

    private static boolean estPictogramme(int point, String texte) {
        if (point < 0x2000) {
            return false; // ASCII, lettres accentuées, degré, exposants…
        }
        if (point <= 0x206F) {
            return false; // ponctuation générale : guillemets, tirets, points de suspension
        }
        if (estTypographie(point)) {
            // Toléré tant que le signe vit dans une phrase : seul, c'est une icône qui s'ignore.
            return Character.toString(point).equals(texte.trim());
        }
        return true;
    }

    private static boolean estTypographie(int point) {
        return (point >= 0x2190 && point <= 0x21FF) || (point >= 0x2200 && point <= 0x22FF);
    }
}

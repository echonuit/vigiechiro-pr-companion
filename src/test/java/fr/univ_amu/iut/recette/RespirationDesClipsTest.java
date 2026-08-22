package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Un cas filmé qui ne s'arrête jamais est un clip que **personne ne pourra lire** (#4149).
///
/// ## Le constat qui a produit ce garde
///
/// Le banc tourne un clip par test citant un cas. Les scénarios perceptifs marquaient leurs temps
/// d'arrêt ; les cas **assertés** n'en avaient aucun. Ils posent leur état, lisent leur propriété et
/// rendent la main : à dix images par seconde, il n'y a rien à voir parce qu'il n'y a le temps de rien
/// voir.
///
/// Mesuré au moment d'écrire ce garde : **34 clips sur 55**. Le poids le disait sans qu'on ait à les
/// ouvrir - 17 à 40 Ko pour ceux qui ne respirent pas, 110 Ko à 1,3 Mo pour les autres.
///
/// La revue l'a dit dans les mêmes termes : « sur aucun on arrive à comprendre ce qu'il se passe ni ce
/// que l'on est censé voir ».
///
/// ## Ce que ce garde ne dit pas
///
/// ⚠️ Il vérifie qu'un cas **s'arrête**, pas qu'il montre quelque chose. Un arrêt sur un écran immobile
/// reste un clip qu'on ne comprend pas - c'est ce qu'était `S1-02` avant qu'on lui donne son contraste,
/// et `S1-03` avant qu'une frappe suive son raccourci. La question reste **ce que le clip doit faire
/// voir** ; ce garde ne tient que sa condition la plus grossière, celle qu'une machine peut lire.
///
/// C'est délibéré : un garde qui prétendrait juger la lisibilité mentirait sur ce qu'il vérifie, et ce
/// chantier est né de dispositifs qui faisaient exactement cela.
class RespirationDesClipsTest {

    private static final Path SOURCES = Path.of("src", "test", "java");

    /// Une annotation de cas **réelle** : en tête de ligne, hors commentaire et hors littéral.
    ///
    /// ⚠️ On ne cherche PAS la méthode par une expression rationnelle qui traverserait les annotations
    /// intermédiaires. La première version le faisait, et un `@DisplayName` contenant une parenthèse
    /// suffisait à la faire échouer **en silence** : le garde inspectait 50 cas sur 54 et se déclarait
    /// vert. Un garde qui en regarde moins qu'il n'annonce est exactement ce que ce chantier combat.
    private static final String ANNOTATION = "@CasDeRecette(";

    /// Le nom d'une méthode : le dernier identifiant avant la parenthèse ouvrante.
    ///
    /// ⚠️ On n'exige PAS l'accolade sur la même ligne. Une signature longue est repliée par le
    /// formateur - `void le_bandeau_dit_l_identite_du_carre(FxRobot robot) throws TimeoutException {`
    /// tient sur une ligne, d'autres non - et l'exiger faisait échouer la lecture sur des cas
    /// parfaitement ordinaires.
    private static final Pattern DECLARATION = Pattern.compile("([a-zA-Z_0-9]+)\\s*\\(");

    /// Les lignes qui séparent une annotation de sa méthode : autres annotations, suite d'une
    /// annotation repliée, commentaires.
    private static final Pattern INTERCALAIRE = Pattern.compile("^(?:[@\"). ]|//|\\+).*|^$");

    /// N'importe quelle méthode du fichier, pour suivre un temps d'arrêt posé dans un utilitaire.
    ///
    /// ⚠️ Le nom accepte les **majuscules**. La première version ne prenait que `[a-z_0-9]`, ce qui
    /// suffisait pour les méthodes de test - elles sont en `serpent_minuscule` - et manquait TOUS les
    /// utilitaires, qui sont en `casseChameau`. Le garde déclarait alors muets neuf cas qui posaient
    /// leur arrêt dans `verifierLeCarre` ou `jouerLaConnexion`.
    private static final Pattern METHODE =
            Pattern.compile("(?:^|\\n)\\s*(?:@\\w+\\s+)*(?:private|protected|public|static|final|\\s)*"
                    + "(?:void|[A-Za-z<>\\[\\]]+)\\s+([a-zA-Z_0-9]+)\\s*\\([^)]*\\)[^{;]*\\{");

    @Test
    @DisplayName("#4149 : tout cas filmé s'arrête au moins une fois, sinon son clip est illisible")
    void tout_cas_filme_s_arrete_au_moins_une_fois() {
        List<String> muets = new ArrayList<>();
        int inspectes = 0;
        for (Path source : sourcesDeTest()) {
            String code = lire(source);
            if (!code.contains("@CasDeRecette(") || code.contains("@FixtureDeRecette")) {
                continue;
            }
            Map<String, String> corps = corpsDesMethodes(code);
            Set<String> quiRespirent = methodesQuiRespirent(corps);

            for (String methode : casDuFichier(source, code)) {
                inspectes++;
                if (!quiRespirent.contains(methode)) {
                    muets.add(source.getFileName() + "#" + methode);
                }
            }
        }

        // ⚠️ Le garde dit combien de cas il a regardés, et refuse d'en perdre en route. Sans ce
        // recoupement, une annotation qu'il ne sait pas lire le rend VERT sur un cas qu'il n'a pas vu.
        assertThat(inspectes)
                .as("le garde n'a inspecté aucun cas : il ne garde plus rien")
                .isPositive();
        System.out.printf("%nRespiration des clips : %d cas filmés inspectés.%n", inspectes);

        assertThat(muets).as("""
                        Ces tests citent un cas de recette - donc le banc en tourne un clip - et ne \
                        s'arrêtent JAMAIS. À dix images par seconde, leur clip défile trop vite pour \
                        qu'on y voie quoi que ce soit : la page annonce un lecteur devant lequel il n'y \
                        a rien à comprendre.

                        Remède : poser un `Respiration.…` aux moments qui comptent - l'écran au repos \
                        avant le geste, ce qui a changé après, le moment que le cas existe pour montrer. \
                        Ces arrêts ne coûtent QU'À une séance filmée : hors tournage, `Seance.filmee()` \
                        est faux et rien ne dort.

                        Et si ce test n'affiche aucune scène, ce n'est pas d'un arrêt qu'il a besoin : \
                        c'est le `@CasDeRecette` qui n'a rien à faire là. Le cas se couvre alors dans un \
                        test qui MONTRE le geste, comme `S1-13` l'a fait en quittant \
                        `ValidationFormulaireTest` pour la modale de site.""").isEmpty();
    }

    /// Les méthodes qui portent un cas, résolues **ligne à ligne**.
    ///
    /// Une annotation qui n'aboutit à aucune déclaration fait échouer le garde en la nommant : c'est
    /// une lecture ratée, et une lecture ratée ne doit jamais passer pour une absence de défaut.
    private static List<String> casDuFichier(Path source, String code) {
        List<String> methodes = new ArrayList<>();
        String[] lignes = code.split("\n", -1);
        for (int i = 0; i < lignes.length; i++) {
            if (!lignes[i].strip().startsWith(ANNOTATION)) {
                continue;
            }
            // ⚠️ On saute l'annotation en suivant ses PARENTHÈSES, pas ses lignes. Une annotation
            // repliée sur cinq lignes - `@CasDeRecette(value = …, portee = …, reserve = …)` - a des
            // lignes de continuation qui ressemblent à tout sauf à une annotation, et un filtre
            // ligne à ligne prenait la première pour une déclaration de méthode (#4158).
            int j = i;
            int ouvertes = 0;
            do {
                for (char c : lignes[j].toCharArray()) {
                    if (c == '(') {
                        ouvertes++;
                    } else if (c == ')') {
                        ouvertes--;
                    }
                }
                j++;
            } while (j < lignes.length && ouvertes > 0);

            String nom = null;
            for (; j < lignes.length && nom == null; j++) {
                String ligne = lignes[j].strip();
                if (INTERCALAIRE.matcher(ligne).matches()) {
                    continue;
                }
                Matcher declaration = DECLARATION.matcher(ligne);
                nom = declaration.find() ? declaration.group(1) : null;
                break;
            }
            assertThat(nom)
                    .as(
                            "%s ligne %d : cette annotation de cas ne mène à aucune déclaration de méthode"
                                    + " que le garde sache lire. Il ne peut donc rien en dire, et se taire ici"
                                    + " reviendrait à déclarer sain un cas qu'il n'a pas regardé.",
                            source.getFileName(), i + 1)
                    .isNotNull();
            methodes.add(nom);
        }
        return methodes;
    }

    /// Le corps de chaque méthode du fichier, par accolades appariées.
    private static Map<String, String> corpsDesMethodes(String code) {
        Map<String, String> corps = new LinkedHashMap<>();
        Matcher m = METHODE.matcher(code);
        while (m.find()) {
            int ouverture = code.indexOf('{', m.end() - 1);
            if (ouverture < 0) {
                continue;
            }
            int profondeur = 0;
            int i = ouverture;
            for (; i < code.length(); i++) {
                char c = code.charAt(i);
                if (c == '{') {
                    profondeur++;
                } else if (c == '}') {
                    profondeur--;
                    if (profondeur == 0) {
                        break;
                    }
                }
            }
            corps.put(m.group(1), code.substring(ouverture, Math.min(i + 1, code.length())));
        }
        return corps;
    }

    /// Les méthodes qui s'arrêtent, **directement ou par un utilitaire du même fichier**.
    ///
    /// ⚠️ L'indirection compte : six cas de `ModaleSiteVerifierCarreViewTest` posent leur arrêt dans
    /// `verifierLeCarre`, et un garde qui ne regarderait que le corps du test les déclarerait muets.
    private static Set<String> methodesQuiRespirent(Map<String, String> corps) {
        Set<String> directes = new LinkedHashSet<>();
        corps.forEach((nom, texte) -> {
            if (texte.contains("Respiration.")) {
                directes.add(nom);
            }
        });
        Set<String> toutes = new LinkedHashSet<>(directes);
        corps.forEach((nom, texte) -> {
            for (String utilitaire : directes) {
                if (texte.contains(utilitaire + "(")) {
                    toutes.add(nom);
                }
            }
        });
        return toutes;
    }

    private static List<Path> sourcesDeTest() {
        try (Stream<Path> chemins = Files.walk(SOURCES)) {
            List<Path> trouvees =
                    chemins.filter(p -> p.toString().endsWith(".java")).toList();
            assertThat(trouvees)
                    .as("Le garde ne balaie aucun fichier : lancé hors de la racine du dépôt ?")
                    .isNotEmpty();
            return trouvees;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

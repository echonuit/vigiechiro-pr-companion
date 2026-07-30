package fr.univ_amu.iut.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le **doublon au niveau des sources** de la règle ArchUnit « une feature ne dépend pas du view ni du
/// viewmodel d'une autre feature ».
///
/// **Pourquoi deux fois la même règle.** ArchUnit lit le **bytecode**. Une dépendance qui se réduit à une
/// constante compile-time n'y laisse aucune trace : le compilateur inline la valeur sur le site d'appel
/// (JLS 13.1, *constant variables*), et le `.class` ne référence jamais la classe qui la déclarait. La
/// règle est alors **verte à tort**, sans rien signaler, c'est le cas réel qui a ouvert #2181 :
/// `cli.commande.Importer` citait `importation.viewmodel.PreferenceConservation.CLE_PUBLIQUE`, et
/// `javap` ne montrait aucune référence.
///
/// Ce n'est pas un faux négatif isolé mais une **catégorie** : toute dépendance réduite à une `String`,
/// un `int` ou un `boolean` constant traverse n'importe laquelle de nos règles ArchUnit sans être vue.
///
/// La déclaration `import`, elle, est dans le **source** quoi qu'en fasse le compilateur. Ce test la lit
/// donc directement. Il est volontairement plus simple qu'ArchUnit : il ne voit que les imports
/// explicites, pas les noms pleinement qualifiés écrits en ligne, et c'est suffisant : le style du
/// dépôt (Google Java Format, Spotless) impose l'import.
///
/// Le cas d'`Importer` a été corrigé en descendant la clé dans `importation.model` (#2181). Ce test
/// existe pour que sa disparition ne laisse pas la catégorie sans détecteur : **on ne retire pas le seul
/// témoin d'un défaut sans installer ce qui le verra revenir.**
class IsolationFeatureSourcesTest {

    private static final Path RACINE = Path.of("src/main/java/fr/univ_amu/iut");

    /// `import [static] fr.univ_amu.iut.<feature>.<reste>;`
    private static final Pattern IMPORT =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?fr\\.univ_amu\\.iut\\.([^;]+);", Pattern.MULTILINE);

    @Test
    @DisplayName("Aucune source ne cite le view/viewmodel d'une autre feature, constantes comprises")
    void aucun_import_vers_la_vue_dune_autre_feature() throws IOException {
        List<String> violations = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(RACINE)) {
            for (Path source :
                    sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String featureOrigine = featureDuChemin(source);
                String texte = Files.readString(source, StandardCharsets.UTF_8);
                Matcher m = IMPORT.matcher(texte);
                while (m.find()) {
                    String cible = m.group(1);
                    String featureCible = premierSegment(cible);
                    if (estVueOuViewModel(cible)
                            && !featureCible.equals(featureOrigine)
                            && !featureCible.equals("commun")) {
                        violations.add(RACINE.relativize(source) + " → fr.univ_amu.iut." + cible);
                    }
                }
            }
        }

        assertThat(violations)
                .as("Une source cite le view/viewmodel d'une AUTRE feature. Si la règle ArchUnit"
                        + " équivalente est verte, c'est que la dépendance se réduit à une constante"
                        + " compile-time, invisible dans le bytecode (#2181). Descendre le membre"
                        + " partagé dans le `model` de sa feature, ou dans `commun`.")
                .isEmpty();
    }

    /// Nom de la feature d'un fichier : le premier segment sous `fr/univ_amu/iut`.
    private static String featureDuChemin(Path source) {
        return RACINE.relativize(source).getName(0).toString();
    }

    /// Premier segment d'un nom de classe relatif à `fr.univ_amu.iut.` (ex. `importation`).
    private static String premierSegment(String nomRelatif) {
        int point = nomRelatif.indexOf('.');
        return point < 0 ? nomRelatif : nomRelatif.substring(0, point);
    }

    /// Vrai si un segment du nom relatif est `view` ou `viewmodel` : même critère qu'`ArchitectureTest`.
    private static boolean estVueOuViewModel(String nomRelatif) {
        for (String segment : nomRelatif.split("\\.")) {
            if (segment.equals("view") || segment.equals("viewmodel")) {
                return true;
            }
        }
        return false;
    }
}

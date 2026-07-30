package fr.univ_amu.iut.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Garde : **un nombre à virgule ne se formate pas avec la locale de la machine** (#2896).
///
/// ## Le défaut qu'elle refuse
///
/// `String.format("%.2f", x)` suit `Locale.getDefault()`. Le même code rend donc « 0,92 » sur un poste
/// français et « 0.92 » ailleurs - y compris sur le runner de la CI, qui est en anglais **et qui génère
/// les aperçus de la galerie**. Une application francophone finit par montrer un séparateur décimal à ses
/// utilisateurs et un autre à sa propre documentation visuelle.
///
/// ## Pourquoi une garde et pas seulement un correctif
///
/// Le défaut a été trouvé deux fois de suite, à deux endroits sans rapport : dans le repli textuel du port
/// de compte rendu (#2757), où la CI l'a fait rougir, puis dans `FormatAnalyse` (#2896), où seul un audit
/// de la **classe** de défaut l'a révélé - la galerie le publiait depuis longtemps sans que rien ne
/// bronche. Sans garde, la troisième occurrence passera comme la deuxième.
///
/// ## Sa portée
///
/// Uniquement les conversions **flottantes** (`%f`, `%e`, `%g`) et le groupement des milliers (`%,d`) :
/// eux seuls dépendent de la locale. `%s` et `%d` simples n'ont pas de séparateur, et les interdire
/// aurait produit vingt faux positifs pour rien.
///
/// Le remède est toujours le même : passer la locale en premier argument, comme
/// `PanneauCompteRendu` le fait pour la légende de ses segments.
class FormatFlottantLocaliseTest {

    private static final Path RACINE = Path.of("src/main/java/fr/univ_amu/iut");

    /// `String.format(` suivi, avant la parenthèse fermante, d'un format flottant ou groupé - et dont le
    /// premier argument n'est **pas** une locale.
    ///
    /// La négation en tête est ce qui distingue l'appel fautif du correct : les deux portent le même
    /// format, seul le premier argument les sépare. Elle accepte la forme **pleinement qualifiée**
    /// (`java.util.Locale.FRENCH`), employée à trois endroits du code - sans quoi la garde les dénonçait
    /// alors qu'ils font précisément ce qu'elle demande.
    private static final Pattern FORMAT_SANS_LOCALE = Pattern.compile(
            "String\\.format\\(\\s*(?!\\s*(?:java\\.util\\.)?Locale\\.)[^;]*?%[-+ 0#,(]*[0-9]*\\.?[0-9]*[fFeEgG]");

    private static final Pattern GROUPEMENT_SANS_LOCALE =
            Pattern.compile("String\\.format\\(\\s*(?!\\s*(?:java\\.util\\.)?Locale\\.)[^;]*?%,[0-9]*d");

    @Test
    @DisplayName("#2896 : aucun flottant n'est formaté avec la locale de la machine")
    void aucun_flottant_ne_suit_la_locale_de_la_machine() throws IOException {
        List<String> fautifs = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(RACINE)) {
            for (Path source : sources.filter(chemin -> chemin.toString().endsWith(".java"))
                    .toList()) {
                releverDans(source, fautifs);
            }
        }

        assertThat(fautifs)
                .as("passez la locale en premier argument : `String.format(Locale.FRANCE, \"%%.2f\", x)`."
                        + " Sans elle, ce nombre s'écrit avec une virgule ici et un point sur la CI")
                .isEmpty();
    }

    private static void releverDans(Path source, List<String> fautifs) throws IOException {
        List<String> lignes = Files.readAllLines(source, StandardCharsets.UTF_8);
        for (int rang = 0; rang < lignes.size(); rang++) {
            String ligne = lignes.get(rang);
            if (ligne.stripLeading().startsWith("//") || ligne.stripLeading().startsWith("///")) {
                continue;
            }
            if (FORMAT_SANS_LOCALE.matcher(ligne).find()
                    || GROUPEMENT_SANS_LOCALE.matcher(ligne).find()) {
                fautifs.add(RACINE.relativize(source) + ":" + (rang + 1) + " → " + ligne.strip());
            }
        }
    }
}

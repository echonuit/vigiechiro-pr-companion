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
import java.util.stream.Collectors;
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
/// En revanche **les trois portes** sont couvertes - `String.format`, `printf`, `String.formatted` -, car
/// elles suivent toutes `Locale.getDefault()`. Seule la première avait un défaut réel ; les deux autres
/// sont là pour que contourner la garde demande un effort délibéré plutôt qu'un synonyme.
///
/// Le remède est toujours le même : passer la locale en premier argument, comme
/// `PanneauCompteRendu` le fait pour la légende de ses segments.
class FormatFlottantLocaliseTest {

    private static final Path RACINE = Path.of("src/main/java/fr/univ_amu/iut");

    /// Les portes qui prennent leur format **en argument** : `String.format` et `printf`. Toutes deux ont
    /// une surcharge qui accepte une locale en tête, d'où l'anticipation négative.
    ///
    /// `String.formatted` est traitée à part ([#FORMATTED_FLOTTANT]) : son format **précède** l'appel, et
    /// aucune de ses surcharges ne prend de locale.
    private static final String PORTES = "(?:String\\.format|printf)";

    /// Un format flottant dont le séparateur décimal **s'affiche réellement**.
    ///
    /// La précision `.0` en est exclue : `%4.0f` n'imprime aucune décimale, donc aucun séparateur, et il
    /// est sûr - c'est la falsification qui l'a montré, en dénonçant le `printf` de l'outil de mesure. En
    /// revanche `%f` **nu** reste fautif : sans précision, il vaut six décimales.
    private static final String FLOTTANT = "%[-+ 0#,(]*[0-9]*(?:\\.(?!0[fFeEgG])[0-9]+)?[fFeEgG]";

    /// Un format **flottant** dont le premier argument n'est pas une locale.
    ///
    /// La négation en tête est ce qui distingue l'appel fautif du correct : les deux portent le même
    /// format, seul le premier argument les sépare. Elle accepte la forme **pleinement qualifiée**
    /// (`java.util.Locale.FRENCH`), employée à trois endroits du code - sans quoi la garde les dénonçait
    /// alors qu'ils font précisément ce qu'elle demande.
    private static final Pattern FORMAT_SANS_LOCALE =
            Pattern.compile(PORTES + "\\(\\s*(?!\\s*(?:java\\.util\\.)?Locale\\.)[^;]*?" + FLOTTANT);

    /// Le **groupement des milliers**, qui dépend aussi de la locale (« 1 234 » contre « 1,234 »).
    private static final Pattern GROUPEMENT_SANS_LOCALE =
            Pattern.compile(PORTES + "\\(\\s*(?!\\s*(?:java\\.util\\.)?Locale\\.)[^;]*?%,[0-9]*d");

    /// `"…%.2f…".formatted(x)` : le format précède l'appel, donc les motifs ci-dessus ne le voient pas.
    ///
    /// C'est la falsification qui l'a montré - la garde élargie restait **verte** sur cette forme, alors
    /// qu'elle prétendait la couvrir. Aucune anticipation négative ici : `String.formatted` n'a aucune
    /// surcharge prenant une locale, donc tout flottant qui passe par elle est fautif.
    private static final Pattern FORMATTED_FLOTTANT =
            Pattern.compile("\"[^\"]*" + FLOTTANT + "[^\"]*\"\\s*\\.formatted\\(");

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

    /// Balaie le **fichier entier**, et non ligne par ligne.
    ///
    /// C'est la falsification qui l'a imposé : la première version scannait les lignes une à une, si bien
    /// qu'un `printf(` dont le format vit sur la ligne suivante lui échappait. Dix appels du code sont dans
    /// ce cas, et l'un d'eux cachait un vrai défaut - le refus de contrôle de durée de
    /// `VerificationIdentiteAudio`, dont les deux durées s'écrivaient avec un point hors locale française.
    ///
    /// Les motifs bornent leur portée par `[^;]`, donc à une **instruction** : ils traversent les retours à
    /// la ligne sans déborder sur l'appel suivant.
    private static void releverDans(Path source, List<String> fautifs) throws IOException {
        String texte = sansCommentaires(Files.readString(source, StandardCharsets.UTF_8));
        for (Pattern motif : List.of(FORMAT_SANS_LOCALE, GROUPEMENT_SANS_LOCALE, FORMATTED_FLOTTANT)) {
            Matcher trouve = motif.matcher(texte);
            while (trouve.find()) {
                long ligne = texte.substring(0, trouve.start()).lines().count();
                fautifs.add(RACINE.relativize(source) + ":" + ligne + " → "
                        + trouve.group().lines().map(String::strip).collect(Collectors.joining(" ")));
            }
        }
    }

    /// Vide les lignes de commentaire **sans en supprimer aucune** : les numéros de ligne restent justes,
    /// et un exemple fautif cité dans une Javadoc ne fait pas rougir la garde.
    private static String sansCommentaires(String texte) {
        return texte.lines()
                .map(ligne -> ligne.stripLeading().startsWith("//") ? "" : ligne)
                .collect(Collectors.joining("\n"));
    }
}

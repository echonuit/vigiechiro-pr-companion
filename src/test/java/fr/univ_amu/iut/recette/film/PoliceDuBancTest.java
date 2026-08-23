package fr.univ_amu.iut.recette.film;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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

/// Le banc dessine par-dessus l'image qu'il rend : carton-titre, halo, flèche, badge. Ce dessin passe
/// par AWT, qui ne sait rien des polices que `commun.view.Typographie` charge du côté JavaFX.
///
/// ## Ce que ce garde empêche de revenir
///
/// Une police **logique** (`Font.SANS_SERIF` et ses voisines) n'est pas une police : c'est une demande
/// que la JVM résout depuis la configuration de la **machine**. Sur le runner de la CI, `SANS_SERIF`
/// tombait sur une serif, et chaque clip s'ouvrait donc sur un carton-titre en serif quand le produit
/// est en Noto Sans (#4241).
///
/// Le défaut a vécu depuis le premier jour du banc sans que rien ne rougisse, et `CartonDeTitreTest`
/// mesurait ses largeurs avec **la même police logique** que le code qu'il vérifiait : d'accord avec lui
/// quelle que soit la police servie, il ne pouvait pas le contredire.
///
/// ## La propriété qu'on ne perd pas au passage
///
/// La police logique avait été choisie pour une bonne raison, écrite dans le code : « toujours résolue,
/// sur tout poste et sans fontconfig ». Charger le TTF **embarqué dans le jar** garde cette propriété et
/// donne en plus la typographie du produit. C'est strictement mieux, pas un compromis.
class PoliceDuBancTest {

    private static final Path FILM = Path.of("src", "test", "java", "fr", "univ_amu", "iut", "recette", "film");

    /// Les façons de demander une police au poste plutôt qu'au jar. Construites morceau par morceau,
    /// pour que le garde ne se dénonce pas lui-même en se lisant.
    private static final List<String> LOGIQUES =
            List.of("Font." + "SANS_SERIF", "Font." + "SERIF", "Font." + "MONOSPACED", "Font." + "DIALOG");

    private static final Pattern COMMENTAIRE = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    @Test
    @DisplayName("#4241 : la police du banc est celle du produit, pas celle de la machine")
    void la_police_du_banc_est_celle_du_produit() {
        assertThat(PoliceDuBanc.grasse(22).getFamily())
                .as("le banc doit dessiner dans la typographie embarquée, sinon deux runners ne rendent "
                        + "pas le même clip")
                .isEqualTo("Noto Sans");
        assertThat(PoliceDuBanc.normale(18).getFamily()).isEqualTo("Noto Sans");
    }

    @Test
    @DisplayName("#4241 : le corps demandé est le corps servi")
    void le_corps_demande_est_le_corps_servi() {
        assertThat(PoliceDuBanc.grasse(22).getSize()).isEqualTo(22);
        assertThat(PoliceDuBanc.normale(13).getSize()).isEqualTo(13);
    }

    @Test
    @DisplayName("#4241 : le badge dessine dans la police du produit")
    void le_badge_dessine_dans_la_police_du_produit() {
        BufferedImage toile = new BufferedImage(1280, 900, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = toile.createGraphics();

        CalqueDesGestes.badge(g, 1280, 900, "Ctrl + F");

        assertThat(g.getFont().getFamily())
                .as("le badge se rendait en serif sur le runner : la police logique était résolue par la "
                        + "machine (#4241)")
                .isEqualTo("Noto Sans");
        g.dispose();
    }

    @Test
    @DisplayName("#4241 : aucune police logique ne subsiste dans le paquet film")
    void aucune_police_logique_dans_le_paquet_film() throws IOException {
        List<String> fautifs = releverLesPolicesLogiques(FILM);

        assertThat(fautifs).as("""
                        Ces appels demandent une police à la MACHINE, pas au jar.

                        Une police logique est une demande que la JVM résout depuis la configuration du \
                        poste : sur le runner de la CI, `SANS_SERIF` tombe sur une SERIF, et le clip \
                        s'ouvre alors sur un carton-titre qui n'est pas dans la typographie du produit. \
                        Rien ne le signale, puisque le rendu réussit.

                        Remède : `PoliceDuBanc.grasse(corps)` ou `PoliceDuBanc.normale(corps)`, qui \
                        dérivent de la Noto Sans embarquée (#4241).""").isEmpty();
    }

    @Test
    @DisplayName("#4241 : le garde attrape bien un fichier fabriqué qui demande une police logique")
    void le_garde_attrape_un_fichier_fabrique() throws IOException {
        Path bac = Files.createTempDirectory("garde-police");
        Files.writeString(
                bac.resolve("Fautif.java"),
                "class Fautif { void dessiner() { g.setFont(new Font(Font." + "SANS_SERIF, 0, 12)); } }",
                StandardCharsets.UTF_8);

        assertThat(releverLesPolicesLogiques(bac))
                .as("un garde qui ne sait pas rougir ne dit rien de ce qu'il vérifie")
                .hasSize(1);
    }

    @Test
    @DisplayName("#4241 : le garde a bien regardé quelque chose")
    void le_garde_a_regarde_quelque_chose() throws IOException {
        try (Stream<Path> sources = Files.walk(FILM)) {
            long inspectees = sources.filter(chemin -> chemin.toString().endsWith(".java"))
                    .count();
            assertThat(inspectees)
                    .as("un garde vert qui n'a rien lu est le faux vert le plus difficile à voir")
                    .isGreaterThan(10);
        }
    }

    private static List<String> releverLesPolicesLogiques(Path racine) throws IOException {
        List<String> fautifs = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(racine)) {
            for (Path source : sources.filter(chemin -> chemin.toString().endsWith(".java"))
                    .filter(chemin -> !chemin.getFileName().toString().equals("PoliceDuBancTest.java"))
                    .sorted()
                    .toList()) {
                String code = sansCommentaires(Files.readString(source, StandardCharsets.UTF_8));
                for (String logique : LOGIQUES) {
                    Matcher trouve =
                            Pattern.compile(Pattern.quote(logique) + "\\b").matcher(code);
                    while (trouve.find()) {
                        fautifs.add("%s : %s".formatted(source.getFileName(), logique));
                    }
                }
            }
        }
        return fautifs;
    }

    private static String sansCommentaires(String code) {
        return COMMENTAIRE.matcher(code).replaceAll("");
    }
}

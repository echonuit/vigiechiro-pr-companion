package fr.univ_amu.iut.cliquet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// **Le patron du cliquet** : une dette est épinglée dans une liste explicite, et cette liste ne peut que
/// rétrécir (#2863).
///
/// ## Pourquoi ce patron existe
///
/// Une migration opportuniste (on bascule un fichier quand on le retouche) est une migration qu'on
/// oublie, sauf si quelque chose la compte. Le dépôt en a la démonstration chiffrée : à l'ouverture du
/// chantier #1771, le **seul** axe équipé d'un cliquet était le seul à avoir reculé depuis l'audit
/// initial. Les trois autres avaient grossi.
///
/// > Ce qui n'est pas compté grandit.
///
/// ## Les deux sens de variation, et pourquoi les deux sont rouges
///
/// **La liste s'allonge** : quelqu'un vient d'ajouter une copie de plus. Le message doit renvoyer vers la
/// brique canonique. C'est le cas qui compte le plus : sans lui, la dette repousse aussi vite qu'on la
/// coupe.
///
/// **La liste raccourcit** : soit une migration a réussi - bravo, retirer le nom est le geste qui rend le
/// progrès **visible** ; soit le **détecteur** a changé, et alors ce n'est pas un progrès mais une
/// correction de la mesure. Les deux se ressemblent et ne valent pas la même chose : le message doit les
/// distinguer, sous peine de faire passer l'un pour l'autre.
///
/// ## Les deux pièges du patron
///
/// Tous deux rencontrés pour de vrai sur le premier cliquet (#2714), et tous deux inhérents :
///
///  - **le court-circuit.** Dès qu'un détecteur cesse de regarder un objet parce qu'il le croit « déjà
///    traité », il devient aveugle exactement sur ce qui est **en cours** de migration, c'est-à-dire là
///    où il devrait parler. Son silence se lit alors comme un accord ;
///  - **la confusion usage / mention.** Citer une brique ne prouve rien. Le premier cliquet comptait une
///    **lecture** comme une écriture, et maintenait ainsi onze fichiers dans une liste où ils n'avaient
///    plus leur place. Un cliquet qui surcompte se décrédibilise aussi sûrement qu'un qui sous-compte.
///
/// ## La valeur par défaut
///
/// Un objet est **hors** du dispositif tant qu'on ne l'y met pas, jamais l'inverse (#2833) : la bonne
/// valeur par défaut est celle qui ne ment pas quand on l'oublie.
public final class Cliquet {

    /// Racine des sources de test, d'où part la plupart des balayages.
    public static final Path TESTS = Path.of("src", "test", "java");

    /// Racine des sources principales : une dette peut vivre dans le produit, pas seulement dans ses
    /// tests (#2669, les outils qui assemblent leur injecteur à la main).
    public static final Path SOURCES = Path.of("src", "main", "java");

    private Cliquet() {}

    /// Les fichiers de **test** qui satisfont `critere`, en chemins relatifs à [#TESTS], triés.
    public static List<String> fichiersOu(Predicate<Fichier> critere) {
        return fichiersOu(TESTS, critere);
    }

    /// Les fichiers sous `racine` qui satisfont `critere`, en chemins relatifs à `racine`, triés.
    ///
    /// Le prédicat reçoit le **contenu** du fichier et son chemin : les deux sont nécessaires, le second
    /// pour exclure un paquet entier ; une exclusion écrite vaut mieux qu'un effet de bord.
    public static List<String> fichiersOu(Path racine, Predicate<Fichier> critere) {
        try (Stream<Path> fichiers = Files.walk(racine)) {
            return fichiers.filter(chemin -> chemin.toString().endsWith(".java"))
                    .map(chemin -> new Fichier(chemin, lire(chemin)))
                    .filter(critere)
                    .map(fichier ->
                            racine.relativize(fichier.chemin()).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException echec) {
            throw new UncheckedIOException("parcours de " + racine, echec);
        }
    }

    /// Le source **sans ses doc-commentaires** (`///`) ni ses blocs `/* … */`.
    ///
    /// Un détecteur qui lit les commentaires confond l'**usage** et la **mention** : `RacineInjecteur`
    /// se serait exclu tout seul du cliquet des injecteurs parce que sa Javadoc cite
    /// `RacineInjecteur.modules()` en exemple, alors que son code ne l'appelle pas. C'est le piège que
    /// l'ADR 2867 nomme, sous sa forme la plus discrète : ici, il **innocentait** le seul fichier qui
    /// devait l'être pour une tout autre raison.
    public static String sansCommentaires(String source) {
        return BLOC_DE_COMMENTAIRE
                .matcher(source)
                .replaceAll(" ")
                .lines()
                .filter(ligne -> !ligne.strip().startsWith("//"))
                .collect(Collectors.joining("\n"));
    }

    private static final Pattern BLOC_DE_COMMENTAIRE = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    /// Un fichier de test, avec son contenu déjà lu : le détecteur n'a pas à s'occuper d'entrées-sorties.
    public record Fichier(Path chemin, String source) {

        /// Ce fichier vit-il dans ce paquet (chemins en style `fr/univ_amu/iut/…`) ?
        public boolean dansLePaquet(String paquet) {
            return chemin.toString().replace('\\', '/').contains("/" + paquet + "/");
        }
    }

    /// Compare la mesure à la liste épinglée, et explique les **deux** sens de variation.
    ///
    /// @param quoi ce que la liste compte, au pluriel (« les tests qui écrivent un en-tête WAV à la main »)
    /// @param versQuoi la brique canonique vers laquelle migrer, citée dans le message
    /// @param ouEstLaListe où retirer un nom quand une migration aboutit
    public static void verifier(
            List<String> reels, List<String> epingles, String quoi, String versQuoi, String ouEstLaListe) {
        assertThat(reels).as("""
                        La liste de %s a changé.

                        • Elle s'ALLONGE ? Une copie de plus vient d'apparaître (il y en a déjà assez).
                          Utilisez %s.

                        • Elle RACCOURCIT ? Deux causes possibles, et elles ne se valent pas. Soit une
                          migration a abouti - bravo, retirez son nom dans %s : c'est le geste qui rend le
                          progrès visible. Soit vous avez touché au DÉTECTEUR, et la liste raccourcit sans
                          qu'une ligne de test ait bougé - alors ce n'est pas un progrès, c'est une
                          correction de la mesure, et elle se dit comme telle.

                        Pourquoi ce cliquet : une migration opportuniste sans garde-fou est une migration
                        qu'on oublie. Une dette qu'aucun test ne compte n'est pas une dette, c'est un vœu.
                        """, quoi, versQuoi, ouEstLaListe).containsExactlyInAnyOrderElementsOf(epingles);
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier);
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + fichier, echec);
        }
    }
}

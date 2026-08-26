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

/// **Le patron du cliquet** : une dette est épinglée dans une liste explicite, et cette liste ne peut
/// que rétrécir (#2863).
///
/// Une migration opportuniste est une migration qu'on oublie, sauf si quelque chose la compte. À
/// l'ouverture du chantier #1771, le seul axe équipé d'un cliquet était le seul à avoir reculé ; les
/// trois autres avaient grossi.
///
/// > Ce qui n'est pas compté grandit.
///
/// **Les deux sens de variation sont rouges.** La liste qui s'allonge dit qu'une copie de plus vient
/// d'être ajoutée. La liste qui raccourcit dit soit qu'une migration a réussi, soit que le **détecteur**
/// a changé : les deux se ressemblent, et le message les distingue.
///
/// **Deux pièges, rencontrés sur le premier cliquet (#2714) et inhérents au patron.** Le
/// **court-circuit** : un détecteur qui cesse de regarder un objet parce qu'il le croit déjà traité
/// devient aveugle là où il devrait parler. La **confusion usage / mention** : compter une lecture pour
/// une écriture a maintenu onze fichiers dans une liste où ils n'avaient plus leur place.
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

package fr.univ_amu.iut.cliquet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Le patron du cliquet se tient lui-même** (ADR 2867) : tout test nommé `Cliquet*Test` vit dans ce
/// paquet et passe par [Cliquet].
///
/// ## Pourquoi ce garde-fou
///
/// Un cliquet écrit à la main perd la moitié de sa valeur : les deux pièges du premier du dépôt, le
/// court-circuit et la confusion usage / mention, ont dû être retrouvés une seconde fois (ADR 2867).
///
/// Le patron ne prétend pas mutualiser les détecteurs : ils inspectent des choses trop différentes, et
/// « qu'est-ce qui compte ? » doit rester lisible dans chaque fichier. Ce qui est mutualisé, c'est le
/// balayage et le **message**, celui qui distingue les deux sens de variation.
///
/// ## La limite, écrite
///
/// **Le nom est le contrat.** Un garde-fou qui fait le travail d'un cliquet sans en porter le nom
/// échappe à ce test : `DocumentationAJourTest` et les cliquets d'ADR (ADR 2465) sont dans ce cas,
/// délibérément. Le motif est leur langage et non leur sujet : ce sont des scripts Python, dont la
/// forme commune vit dans `scripts/adr/_commun.py` (ADR 4586).
///
/// Ce test ne prouve donc pas « tous les cliquets du dépôt sont conformes ». Il prouve que **prendre le
/// nom oblige à prendre la forme**, ce qui est le seul invariant qui tienne sans énumération.
class PatronDuCliquetTest {

    /// Le paquet où vivent les cliquets, en style chemin.
    private static final String MAISON = "fr/univ_amu/iut/cliquet/";

    @Test
    @DisplayName("Tout test nommé Cliquet*Test vit dans le paquet cliquet et passe par Cliquet.verifier")
    void tout_cliquet_passe_par_le_patron() {
        List<String> tous = cliquets();

        // Non-vacuité : un détecteur qui ne trouve plus rien passe au vert et ne dit pas pourquoi. Le
        // jour où le paquet est renommé ou le motif cassé, c'est CETTE ligne qui parle, pas le silence
        // d'une liste vide comparée à une liste vide.
        assertThat(tous)
                .as("le détecteur ne voit plus AUCUN cliquet : c'est lui qui est cassé, pas le dépôt qui"
                        + " est devenu propre")
                .isNotEmpty();

        List<String> horsDuPatron = tous.stream()
                .filter(chemin -> !chemin.startsWith(MAISON) || !contientLAppel(chemin))
                .toList();

        assertThat(horsDuPatron).as("""
                        Un test nommé « Cliquet…Test » ne suit pas le patron (ADR 2867).

                        Deux exigences, et une seule raison : un cliquet écrit à la main perd le message
                        qui distingue ses DEUX sens de variation, et se retrouve à devoir réapprendre
                        seul les pièges du patron.

                        • Il vit dans le paquet `%s` - avec les autres, pour qu'une règle nouvelle
                          se pose une fois.
                        • Il appelle `Cliquet.verifier(...)` - qui porte le message.

                        Si ce que vous écrivez n'est pas un cliquet, ne l'appelez pas ainsi : le nom est
                        le contrat.
                        """.formatted(MAISON)).isEmpty();
    }

    /// Les fichiers de test dont le **nom** annonce un cliquet, où qu'ils soient.
    ///
    /// Le balayage part de la racine des tests et non du paquet : un cliquet égaré ailleurs est
    /// précisément ce qu'on cherche, et le chercher là où il devrait être ne le trouverait jamais.
    private static List<String> cliquets() {
        try (Stream<Path> fichiers = Files.walk(Cliquet.TESTS)) {
            return fichiers.map(chemin ->
                            Cliquet.TESTS.relativize(chemin).toString().replace('\\', '/'))
                    .filter(chemin -> chemin.matches(".*/Cliquet\\w*Test\\.java"))
                    .sorted()
                    .toList();
        } catch (IOException echec) {
            throw new UncheckedIOException("parcours de " + Cliquet.TESTS, echec);
        }
    }

    /// L'appel au patron, **espacements tolérés**.
    ///
    /// La recherche d'une chaîne littérale aurait suffi aujourd'hui, et c'est exactement le piège que
    /// l'ADR 2867 décrit : le détecteur des captures cherchait `new ByteArrayOutputStream` et ne voyait
    /// pas `new java.io.ByteArrayOutputStream(...)`. Un formateur qui coupe la ligne autrement suffirait
    /// à rendre ce test aveugle, et son silence se lirait comme un accord.
    private static final Pattern APPEL_AU_PATRON = Pattern.compile("Cliquet\\s*\\.\\s*verifier\\s*\\(");

    private static boolean contientLAppel(String chemin) {
        try {
            return APPEL_AU_PATRON
                    .matcher(Files.readString(Cliquet.TESTS.resolve(chemin)))
                    .find();
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + chemin, echec);
        }
    }
}

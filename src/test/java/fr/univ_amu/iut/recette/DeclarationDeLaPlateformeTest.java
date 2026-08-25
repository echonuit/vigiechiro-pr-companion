package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Un scénario connecté déclare vers QUI son banc parle (#4447).
///
/// ## Le défaut que ce cas ferme
///
/// `@Tag("recette-connectee")` décide de la **sélection** : le tournage connecté ne joue que les
/// classes qui le portent. Il ne décide de rien d'autre. Le **câblage**, lui, se déclare au banc, par
/// [BancDeRecette#parleALaPlateforme()] ou [BancDeRecette#connecteALaPlateforme()] ; sans quoi le banc
/// lie le client hors ligne de #4332, sur `http://localhost:1`.
///
/// Deux interrupteurs pour une seule intention, et `ScenarioConnecteConnexionTest` n'en a levé qu'un.
/// Il avait un jeton réel, une URL réelle dans son environnement, et parlait au port 1. Trois
/// tournages ont filmé cet écran hors ligne sans que rien ne le dise : la connexion paraissait
/// simplement brève, et le badge d'identité restait gris. Le défaut n'est apparu qu'en imprimant le
/// bandeau de statut, sur le tir 32894626486 :
///
/// ```
/// synchro de la connexion : Vigie-Chiro est injoignable (ConnectException)
/// ```
///
/// ## Pourquoi un relevé de sources, et pas un cas d'exécution
///
/// [BancDeRecetteSansDepotTest] éprouve que la déclaration **produit** le bon câblage. Il ne peut rien
/// dire de la classe qui ne la fait pas : elle monte un banc parfaitement valide, simplement hors
/// ligne. Ce qui manque est une propriété sur l'ENSEMBLE des scénarios connectés, et elle se lit dans
/// les sources.
///
/// La déclaration se cherche comme un APPEL, en tête de ligne, et non comme un texte présent
/// quelque part dans le fichier. `ScenarioConnecteConnexionTest` porte en commentaire la phrase « Ni
/// `connecte(...)` ni `connecteALaPlateforme()` » : un relevé qui se contenterait de chercher le nom
/// aurait été vert sur le fichier même qui portait le défaut.
class DeclarationDeLaPlateformeTest {

    private static final Path SOURCES = Path.of("src", "test", "java");

    /// Ce qui met une classe dans le tournage connecté.
    private static final String TAG = "@Tag(\"recette-connectee\")";

    /// Ce qui distingue un scénario d'un outil : `CorrespondanceRecetteTest` porte le tag pour être
    /// joué avec eux, mais il dérive le corpus au lieu de monter un écran.
    private static final String MONTE_UN_BANC = "BancDeRecette.surLeChrome()";

    private static final List<String> DECLARATIONS = List.of(".parleALaPlateforme()", ".connecteALaPlateforme()");

    @Test
    @DisplayName("#4447 : tout scénario connecté qui monte un banc déclare parler à la plateforme")
    void un_scenario_connecte_declare_sa_plateforme() throws IOException {
        List<Path> scenarios = sourcesPortantLAnnotation().stream()
                .filter(fichier -> contient(fichier, MONTE_UN_BANC))
                .toList();

        // Un relevé qui ne trouve aucun scénario serait vert pour la pire des raisons.
        assertThat(scenarios)
                .as(
                        "Aucun scénario connecté trouvé : le relevé cherche `%s` puis `%s` sous `%s`. Zéro"
                                + " fichier veut dire que le motif ne correspond plus, pas que la propriété"
                                + " tient.",
                        TAG, MONTE_UN_BANC, SOURCES)
                .isNotEmpty();

        List<Path> sansDeclaration = scenarios.stream()
                .filter(fichier -> !appelleUneDeclaration(fichier))
                .toList();

        assertThat(sansDeclaration).as("""
                        Ces scénarios sont joués par le tournage connecté et n'ont pas dit au banc vers
                        qui parler. Leur client repart donc sur `http://localhost:1`, le hors-ligne que
                        #4332 lie à tout banc n'ayant déclaré aucun serveur.

                        `@Tag("recette-connectee")` décide de la SÉLECTION, pas du câblage. Ajouter
                        `.parleALaPlateforme()` (le scénario colle le jeton lui-même) ou
                        `.connecteALaPlateforme()` (le banc le dépose, la modale revérifie seule).

                        Sans cela le clip n'est pas faux, il est MUET SUR SON PROPRE OBJET (ADR 4142) :
                        un jeton réel, une connexion instantanée, un badge d'identité qui reste gris, et
                        rien qui dise pourquoi.""").isEmpty();
    }

    /// L'appel, et non la mention : la ligne dépouillée doit COMMENCER par la déclaration. Un
    /// commentaire qui cite le nom l'a en milieu de ligne, derrière ses `//`.
    private static boolean appelleUneDeclaration(Path fichier) {
        return lignes(fichier).anyMatch(ligne -> {
            String nue = ligne.strip();
            return DECLARATIONS.stream().anyMatch(nue::startsWith);
        });
    }

    /// Le tag comme ANNOTATION, en tete de ligne : ce fichier-ci porte le motif dans ses constantes,
    /// et un releve qui chercherait le texte n importe ou se designerait lui-meme comme scenario
    /// connecte sans plateforme. C est la figure du cliquet qui parcourt son propre fichier, et elle a
    /// rougi ici avant d etre ecrite.
    private static List<Path> sourcesPortantLAnnotation() throws IOException {
        // `Files.walk` enveloppe l'échec de parcours dans une `UncheckedIOException` (#3632) : elle se
        // rattrape ici plutôt que de traverser le cas sous forme d'échec illisible.
        try (Stream<Path> arbre = Files.walk(SOURCES)) {
            return arbre.filter(Files::isRegularFile)
                    .filter(fichier -> fichier.toString().endsWith(".java"))
                    .filter(DeclarationDeLaPlateformeTest::porteLAnnotation)
                    .sorted()
                    .toList();
        } catch (UncheckedIOException parcoursInterrompu) {
            throw parcoursInterrompu.getCause();
        }
    }

    private static boolean porteLAnnotation(Path fichier) {
        return lignes(fichier).anyMatch(ligne -> ligne.strip().startsWith(TAG));
    }

    private static boolean contient(Path fichier, String motif) {
        return lignes(fichier).anyMatch(ligne -> ligne.contains(motif));
    }

    private static Stream<String> lignes(Path fichier) {
        try {
            return Files.readAllLines(fichier).stream();
        } catch (IOException illisible) {
            throw new UncheckedIOException(illisible);
        }
    }
}

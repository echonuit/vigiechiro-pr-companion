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
/// `@Tag("recette-connectee")` décide de la **sélection** ; le **câblage**, lui, se déclare au banc, par
/// [BancDeRecette#parleALaPlateforme()] ou [BancDeRecette#connecteALaPlateforme()]. Deux interrupteurs
/// pour une seule intention, et `ScenarioConnecteConnexionTest` n'en avait levé qu'un : trois tournages
/// ont filmé un écran hors ligne sans que rien ne le dise. La mesure est chez [BancDeRecetteSansDepotTest].
///
/// Ce voisin éprouve que la déclaration **produit** le bon câblage. Il ne peut rien dire de la classe qui
/// ne la fait pas : elle monte un banc valide, simplement hors ligne. Cette propriété-là porte sur
/// l'ENSEMBLE des scénarios connectés, et se lit dans les sources.
///
/// La déclaration s'y cherche comme un APPEL, en tête de ligne. `ScenarioConnecteConnexionTest` porte en
/// commentaire la phrase « Ni `connecte(...)` ni `connecteALaPlateforme()` » : un relevé qui aurait
/// cherché le nom aurait été vert sur le fichier même qui portait le défaut.
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

    /// Ce qui fait monter la VRAIE plateforme de test, donc demande Docker (#5665) : la déclaration
    /// publique, qui reçoit une clé d'utilisateur LITTÉRALE. La surcharge de paquet, qui reçoit un accès
    /// fabriqué, ne monte rien, et `BancDeRecettePlateformeDeTestTest` l'emploie sans Docker.
    private static final String DECLARE_LA_PLATEFORME_DE_TEST = ".surLaPlateformeDeTest(\"";

    /// Ce qui sort une classe du build par défaut et la met dans le job `plateforme-de-test`.
    private static final String TAG_PLATEFORME_DE_TEST = "@Tag(\"plateforme-de-test\")";

    @Test
    @DisplayName("#5665 : tout scénario qui déclare la plateforme de test porte le tag qui l'écarte du build")
    void un_scenario_sur_la_plateforme_de_test_porte_son_tag() throws IOException {
        List<Path> declarants;
        try (Stream<Path> arbre = Files.walk(SOURCES)) {
            declarants = arbre.filter(Files::isRegularFile)
                    .filter(fichier -> fichier.toString().endsWith(".java"))
                    .filter(fichier -> commenceUneLigne(fichier, DECLARE_LA_PLATEFORME_DE_TEST))
                    .sorted()
                    .toList();
        } catch (UncheckedIOException parcoursInterrompu) {
            throw parcoursInterrompu.getCause();
        }

        // Le sens inverse du cas précédent, et le même piège : zéro déclarant voudrait dire que le
        // motif ne correspond plus, pas que la propriété tient.
        assertThat(declarants)
                .as(
                        "Aucun scénario ne déclare la plateforme de test : le relevé cherche `%s` en tête de"
                                + " ligne sous `%s`.",
                        DECLARE_LA_PLATEFORME_DE_TEST, SOURCES)
                .isNotEmpty();

        List<Path> sansTag = declarants.stream()
                .filter(fichier -> !commenceUneLigne(fichier, TAG_PLATEFORME_DE_TEST))
                .toList();

        assertThat(sansTag).as("""
                        Ces classes montent la plateforme de test, donc Docker, sans porter
                        `@Tag("plateforme-de-test")`. Elles tournent alors sous `./mvnw test`, que jouent
                        aussi les runners Windows et macOS, qui n'ont pas de Docker utilisable : le build
                        par défaut y rougirait sur une cause étrangère au code.

                        Ajouter `@Tag("plateforme-de-test")` : la classe passe dans le job du même nom.""").isEmpty();
    }

    /// La ligne dépouillée COMMENCE par le motif : un appel ou une annotation, jamais une mention en
    /// commentaire, et jamais les constantes de ce fichier-ci, qui portent le motif entre guillemets.
    private static boolean commenceUneLigne(Path fichier, String motif) {
        return lignes(fichier).anyMatch(ligne -> ligne.strip().startsWith(motif));
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

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
/// [BancDeRecette#parleALaPlateforme()], [BancDeRecette#connecteALaPlateforme()] ou, pour un scénario
/// propre à la plateforme de test, [BancDeRecette#surLaPlateformeDeTest(String)]. Deux interrupteurs
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

    /// Ce qui fait monter la VRAIE plateforme de test, donc demande Docker (#5665) : la déclaration
    /// publique, qui reçoit une clé d'utilisateur LITTÉRALE. La surcharge de paquet, qui reçoit un accès
    /// fabriqué, ne monte rien, et `BancDeRecettePlateformeDeTestTest` l'emploie sans Docker.
    private static final String DECLARE_LA_PLATEFORME_DE_TEST = ".surLaPlateformeDeTest(\"";

    /// Les trois manières de dire au banc vers qui parler. La troisième (#5795) vise la plateforme de
    /// test SANS lire la cible déclarée : c'est celle d'un scénario qui écrit, et qui ne doit pouvoir
    /// écrire nulle part ailleurs même sélectionné par erreur dans un tournage national.
    private static final List<String> DECLARATIONS =
            List.of(".parleALaPlateforme()", ".connecteALaPlateforme()", DECLARE_LA_PLATEFORME_DE_TEST);

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
                        `.connecteALaPlateforme()` (le banc le dépose, la modale revérifie seule), ou
                        `.surLaPlateformeDeTest("observatrice")` si le scénario n'a de sens que sur la plateforme
                        de test.

                        Sans cela le clip n'est pas faux, il est MUET SUR SON PROPRE OBJET (ADR 4142) :
                        un jeton réel, une connexion instantanée, un badge d'identité qui reste gris, et
                        rien qui dise pourquoi.""").isEmpty();
    }

    /// L'autre manière de la monter, celle d'un test d'API (clôture de #5642, ADR 5663) : l'extension
    /// elle-même. `DepotSurLaPlateformeDeTestTest` est écrit ainsi, et le relevé ne cherchait que la
    /// déclaration du banc : un test d'API sans tag lui échappait.
    private static final String MONTE_LA_PLATEFORME_DE_TEST = "@ExtendWith(PlateformeDeTest.class)";

    /// La troisième manière (clôture de #5643) : une sonde du contrat live qui DÉCLARE sa cible. Elle ne
    /// monte la plateforme que sous le profil de test ; ailleurs elle vise la nationale. Sans le tag, le
    /// job `plateforme-de-test` ne la jouerait jamais, et rien ne le dirait.
    private static final String DECLARE_SA_CIBLE = "CibleLive.declaree()";

    /// Ce qui sort une classe du build par défaut et la met dans le job `plateforme-de-test`.
    private static final String TAG_PLATEFORME_DE_TEST = "@Tag(\"plateforme-de-test\")";

    @Test
    @DisplayName("#5665 : toute classe qui monte la plateforme de test porte le tag qui l'écarte du build")
    void une_classe_qui_monte_la_plateforme_de_test_porte_son_tag() throws IOException {
        List<Path> scenarios = classesOuUneLigneCommencePar(DECLARE_LA_PLATEFORME_DE_TEST);
        List<Path> testsDApi = classesOuUneLigneCommencePar(MONTE_LA_PLATEFORME_DE_TEST);

        // Le sens inverse du cas précédent, et le même piège, une fois PAR MOTIF : un motif qui ne
        // correspond plus serait masqué par l'autre si l'on ne comptait que leur union.
        assertThat(scenarios)
                .as(
                        "Aucun scénario ne déclare la plateforme de test : le relevé cherche `%s` en tête de"
                                + " ligne sous `%s`.",
                        DECLARE_LA_PLATEFORME_DE_TEST, SOURCES)
                .isNotEmpty();
        assertThat(testsDApi)
                .as(
                        "Aucun test d'API ne monte la plateforme de test : le relevé cherche `%s` en tête de"
                                + " ligne sous `%s`.",
                        MONTE_LA_PLATEFORME_DE_TEST, SOURCES)
                .isNotEmpty();

        List<Path> declarants =
                Stream.concat(scenarios.stream(), testsDApi.stream()).distinct().toList();

        List<Path> sansTag = declarants.stream()
                .filter(fichier -> !commenceUneLigne(fichier, TAG_PLATEFORME_DE_TEST))
                .toList();

        assertThat(sansTag).as("""
                        Ces classes montent la plateforme de test, donc Docker, sans porter
                        `@Tag("plateforme-de-test")`. Elles tournent alors sous `./mvnw test`, que jouent
                        aussi les runners Windows et macOS, qui n'ont pas de Docker utilisable : le build
                        par défaut y rougirait sur une cause étrangère au code.

                        Ajouter `@Tag("plateforme-de-test")` : la classe passe dans le job du même nom.""").isEmpty();

        // Un APPEL, cherché dans la ligne : `cible = CibleLive.declaree();` ne commence pas par lui. Ce
        // fichier-ci porte le motif dans sa constante, d'où son exclusion par nom.
        List<Path> sondesLive = javaSous(SOURCES)
                .filter(fichier -> !fichier.endsWith("DeclarationDeLaPlateformeTest.java"))
                .filter(fichier -> contient(fichier, DECLARE_SA_CIBLE))
                .toList();
        assertThat(sondesLive)
                .as("Aucune sonde ne déclare sa cible : le relevé cherche `%s` sous `%s`.", DECLARE_SA_CIBLE, SOURCES)
                .isNotEmpty();
        assertThat(sondesLive.stream()
                        .filter(fichier -> !commenceUneLigne(fichier, TAG_PLATEFORME_DE_TEST))
                        .toList())
                .as("""
                        Ces classes déclarent leur cible par `CibleLive` sans porter
                        `@Tag("plateforme-de-test")`. Le job du même nom ne les joue donc jamais, et leur
                        cible de test reste lettre morte sans que rien ne le dise (#5643).""")
                .isEmpty();
    }

    private static Stream<Path> javaSous(Path racine) throws IOException {
        try (Stream<Path> arbre = Files.walk(racine)) {
            return arbre
                    .filter(Files::isRegularFile)
                    .filter(fichier -> fichier.toString().endsWith(".java"))
                    .sorted()
                    .toList()
                    .stream();
        } catch (UncheckedIOException parcoursInterrompu) {
            throw parcoursInterrompu.getCause();
        }
    }

    /// L'autre moitié du même contrat (#5642, passe 6 de sa clôture) : porter le tag ne sert que si le build
    /// par défaut l'exclut. Retirer `plateforme-de-test` de `surefire.excludedGroups` ne faisait rougir
    /// aucune demande, le job `plateforme-de-test` jouant ce tag de toute façon : seuls les runners Windows
    /// et macOS de `suite-sous-windows-et-macos.yml`, le mardi, auraient cassé, faute de Docker.
    ///
    /// La propriété se lit à sa PREMIÈRE occurrence, qui est la valeur par défaut : les profils, plus bas,
    /// la redéfinissent pour leur seul usage.
    @Test
    @DisplayName("#5642 : le build par défaut exclut le tag plateforme-de-test")
    void le_build_par_defaut_exclut_la_plateforme_de_test() {
        String defaut = lignes(Path.of("pom.xml"))
                .map(String::strip)
                .filter(ligne -> ligne.startsWith("<surefire.excludedGroups>"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("`pom.xml` ne déclare plus `surefire.excludedGroups`"));

        assertThat(defaut).as("""
                        `./mvnw test` doit exclure `plateforme-de-test` : ce tag monte Docker, que les runners
                        Windows et macOS n'ont pas. Valeur lue : %s""", defaut).contains("plateforme-de-test");
    }

    private static List<Path> classesOuUneLigneCommencePar(String motif) throws IOException {
        try (Stream<Path> arbre = Files.walk(SOURCES)) {
            return arbre.filter(Files::isRegularFile)
                    .filter(fichier -> fichier.toString().endsWith(".java"))
                    .filter(fichier -> commenceUneLigne(fichier, motif))
                    .sorted()
                    .toList();
        } catch (UncheckedIOException parcoursInterrompu) {
            throw parcoursInterrompu.getCause();
        }
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

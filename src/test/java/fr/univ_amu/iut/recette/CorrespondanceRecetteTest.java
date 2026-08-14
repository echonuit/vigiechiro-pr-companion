package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le garde de correspondance entre les sessions de recette et le code qui les couvre (#3728).
///
/// ## Pourquoi il existe
///
/// Une session est un document en prose ; le harnais est du code. Ces deux-là divergent, et le
/// jour où ils divergent, le code atteste ce que la session ne dit plus. Ce n'est pas une crainte
/// théorique : `s1-premier-contact.md` désignait les cas 24 et 25 là où il parlait des 26 et 27.
///
/// ## Ses TROIS devoirs, et le deuxième est celui qu'on oublie
///
/// 1. tout identifiant cité par un test **existe** dans une session ;
/// 2. les cas que **rien** ne couvre sont **listés** ;
/// 3. le script et le code disent la **même chose** du juge (#3764).
///
/// ⚠️ Sans le deuxième, un garde qui ne trouve rien à redire est **indiscernable** d'un garde qui ne
/// lit pas le document. Le premier devoir seul serait vert sur un dépôt où aucun test ne cite quoi
/// que ce soit.
///
/// « Aucun test » reste parfaitement recevable : c'est même le cas normal pour les cas perceptifs,
/// que la passe 6 envoie en recette **parce qu'**ils ne sont pas automatisables. Ce garde ne
/// réclame donc pas la couverture, il refuse qu'elle soit **tacite**.
///
/// ## Ce que le troisième devoir a changé
///
/// Les cas perceptifs étaient jusqu'ici indiscernables des cas non couverts : deux bacs pour trois
/// situations. Le script les marque désormais `*perceptif*`, ce qui leur donne le leur, et surtout
/// permet de **confronter** les deux sources - voir [RepartitionDesCas], où le tri vit pour pouvoir
/// être éprouvé sur des situations que ce dépôt ne contient pas.
class CorrespondanceRecetteTest {

    private static final Path SESSIONS = Path.of("dev-docs", "recette", "sessions");

    /// Un cas se déclare `- **S1-04** · texte`, et `- **S1-26** · *perceptif* · texte` s'il ne se
    /// tranche qu'à l'oeil. Le second groupe capture cette marque.
    private static final Pattern CAS =
            Pattern.compile("^- \\*\\*(S\\d+-\\d+)\\*\\* ·( \\*perceptif\\* ·)?", Pattern.MULTILINE);

    /// Les cas déclarés par les sessions, par identifiant, associés au fichier qui les porte.
    private static Map<String, String> declares;

    /// Ceux d'entre eux que le script marque `*perceptif*`.
    private static Set<String> perceptifs;

    /// Les cas cités par le code, et par quel test.
    private static Map<String, Set<String>> cites;

    /// Ce que les tests qui les citent prétendent prouver.
    private static Map<String, Set<Jugement>> jugements;

    /// Le tri qui en découle, calculé une fois.
    private static RepartitionDesCas tri;

    @BeforeAll
    static void lire() {
        declares = new LinkedHashMap<>();
        perceptifs = new LinkedHashSet<>();
        lireLesScripts();

        cites = new LinkedHashMap<>();
        jugements = new LinkedHashMap<>();
        lireLeCode();

        tri = RepartitionDesCas.repartir(declares.keySet(), perceptifs, jugements);
    }

    @Test
    @DisplayName("Tout cas cité par un test existe dans une session")
    void aucun_cas_cite_n_est_inconnu() {
        SoftAssertions verifs = new SoftAssertions();
        cites.forEach((cas, tests) -> verifs.assertThat(declares)
                .as(
                        "%s est cité par %s, mais aucune session ne le déclare. Un identifiant"
                                + " inventé ou renuméroté ne couvre rien : corrigez la citation, ou"
                                + " la session.",
                        cas, String.join(", ", tests))
                .containsKey(cas));
        verifs.assertAll();
    }

    @Test
    @DisplayName("Les cas que rien ne couvre sont listés, jamais tacites")
    void les_cas_non_couverts_sont_nommes() {
        // Ce test ne réclame PAS la couverture : il l'affiche. Il n'échoue que si le garde lui-même
        // ne lit plus rien - sans quoi il rendrait vert sur un dépôt sans aucune session.
        assertThat(declares)
                .as("Aucun cas de recette n'a été lu sous %s : le garde ne garde plus rien.", SESSIONS)
                .isNotEmpty();

        System.out.printf(
                "%nCorrespondance recette : %d cas déclarés, %d assertés, %d perceptifs, %d non couverts.%n",
                declares.size(),
                tri.assertes().size(),
                tri.perceptifs().size(),
                tri.nonCouverts().size());

        tri.perceptifs()
                .forEach(cas -> System.out.printf(
                        "  perceptif   · %s · %s · %s%n",
                        cas,
                        declares.get(cas),
                        cites.containsKey(cas) ? "joué par " + joindre(cites.get(cas)) : "à jouer"));
        tri.nonCouverts().forEach(cas -> System.out.printf("  non couvert · %s · %s%n", cas, declares.get(cas)));
    }

    @Test
    @DisplayName("Le script et le code disent la même chose du juge")
    void le_script_et_le_code_s_accordent_sur_le_juge() {
        // Le devoir neuf de #3764, et le seul des trois qui puisse rougir sur le contenu du dépôt.
        // Les deux sources se recoupent : c'est ce recoupement qui les tient l'une par l'autre.
        String explications = tri.desaccords().entrySet().stream()
                .map(desaccord -> explication(desaccord.getKey(), desaccord.getValue()))
                .collect(Collectors.joining(System.lineSeparator() + "  "));

        assertThat(tri.desaccords())
                .as("Le script et le code ne disent pas la même chose du juge :%n  %s", explications)
                .isEmpty();
    }

    // ----------------------------------------------------------------------------------------

    private static String explication(String cas, Jugement ceQueDitLeCode) {
        String tests = joindre(cites.getOrDefault(cas, Set.of()));
        // ⚠️ Les parenthèses ne sont pas décoratives : sans elles, `.formatted` ne s'applique qu'au
        // DERNIER fragment de la concaténation, et le message part avec ses `%s` intacts. Le témoin
        // du chantier l'a montré avant qu'un lecteur ait à le subir.
        if (ceQueDitLeCode == Jugement.AUTOMATIQUE) {
            return ("%s est marqué *perceptif* dans %s, mais %s prétend l'asserter. Ou bien le script"
                            + " a tort et le cas est automatisable - retirez la marque -, ou bien le"
                            + " test surestime ce qu'il prouve.")
                    .formatted(cas, declares.get(cas), tests);
        }
        return ("%s se déclare jugé par un humain dans %s, mais %s ne le marque pas *perceptif*. Ou"
                        + " bien le cas relève de l'oeil - marquez-le -, ou bien ce scénario doit"
                        + " asserter.")
                .formatted(cas, tests, declares.get(cas));
    }

    private static String joindre(Set<String> noms) {
        return String.join(", ", noms);
    }

    private static void lireLesScripts() {
        if (!Files.isDirectory(SESSIONS)) {
            return;
        }
        try (Stream<Path> fichiers = Files.list(SESSIONS)) {
            fichiers.filter(f -> f.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .forEach(f -> {
                        Matcher m = CAS.matcher(lire(f));
                        while (m.find()) {
                            declares.putIfAbsent(m.group(1), f.getFileName().toString());
                            if (m.group(2) != null) {
                                perceptifs.add(m.group(1));
                            }
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void lireLeCode() {
        JavaClasses classes = new ClassFileImporter().importPackages("fr.univ_amu.iut");
        classes.forEach(classe -> {
            // Les exemples qui éprouvent l'outillage citent de vrais identifiants sans rien couvrir :
            // les compter gonflerait l'index de tests qui n'exercent pas le produit. Voir
            // FixtureDeRecette, où l'exclusion est expliquée et rendue portante.
            if (classe.isAnnotatedWith(FixtureDeRecette.class)) {
                return;
            }
            classe.getMethods()
                    .forEach(methode -> methode.tryGetAnnotationOfType(CasDeRecette.class)
                            .ifPresent(annotation -> {
                                String nom = classe.getSimpleName() + "." + methode.getName();
                                for (String id : List.of(annotation.value())) {
                                    cites.computeIfAbsent(id, k -> new LinkedHashSet<>())
                                            .add(nom);
                                    jugements
                                            .computeIfAbsent(id, k -> EnumSet.noneOf(Jugement.class))
                                            .add(annotation.jugement());
                                }
                            }));
        });
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

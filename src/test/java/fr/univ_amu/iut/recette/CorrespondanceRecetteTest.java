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
import java.util.TreeSet;
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

    /// Les sessions dont ce garde ne lit aucun cas, et dont le silence est assumé (#3884).
    ///
    /// ⚠️ Cette liste est une **dette chiffrée**, pas une dispense. Chaque ligne dit pourquoi la
    /// session échappe à la regex, et disparaîtra le jour où elle adoptera le format `- **Sxx-NN** ·`.
    /// Le garde refuse qu'on l'oublie dans les deux sens : une session muette absente d'ici rougit,
    /// une ligne d'ici dont la session s'est mise à parler rougit aussi.
    ///
    /// Le travail de conversion est le point 2 de #3884, et il se fait session par session.
    ///
    /// ⚠️ `Map.ofEntries` et non `Map.of` : ce dernier plafonne à **dix** paires, et la liste en
    /// compte exactement dix. La onzième session muette - celle que ce garde existe pour attraper -
    /// aurait cassé la **compilation** au lieu de rougir avec son message. Un garde qui échoue par
    /// erreur de build ne dit pas ce qu'il a trouvé, et c'est ce qui est arrivé en écrivant ceci.
    private static final Map<String, String> MUETTES_ADMISES = Map.ofEntries(
            Map.entry("passe-ciblee-constats-en-attente.md", "cas préfixés PC- et cochés, hors numérotation"),
            Map.entry(
                    "passe-verification-stabilisation.md",
                    "ce n'est pas une session : un tableau des capacités et de leur session propriétaire"));

    /// Les classes à filmer pour une **planche de contact** (#3835), déposées là où le script de
    /// séance ira les chercher.
    ///
    /// ⚠️ Cette liste se DÉRIVE, elle ne se tient pas à la main. Un `grep` sur `@CasDeRecette`
    /// ramène deux faux positifs sur dix-huit - l'annotation elle-même, qui contient un exemple
    /// dans sa documentation, et les fixtures de [ReperesDeSeanceTest]. Le balayage du classpath,
    /// lui, voit les annotations compilées et honore [FixtureDeRecette].
    ///
    /// Une liste tenue à la main dériverait exactement comme la prose dérivait avant #3728.
    private static final Path CLASSES_A_FILMER = Path.of("target", "recette", "classes-citantes.txt");

    /// Le motif vit dans [MotifDeCas] : trois lecteurs de ces fichiers coexistent, et deux ont
    /// découvert séparément que certaines sessions cochent leurs puces.
    private static final Pattern CAS = MotifDeCas.CAS;

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

    /// Pour chaque session balayée, le nombre de cas que [#CAS] y a lus - zéro compris.
    private static Map<String, Integer> casParFichier;

    /// Sur quoi le décompte porte, et sur quoi il ne porte pas.
    private static PerimetreDesSessions perimetre;

    @BeforeAll
    static void lire() {
        declares = new LinkedHashMap<>();
        perceptifs = new LinkedHashSet<>();
        casParFichier = new LinkedHashMap<>();
        lireLesScripts();
        perimetre = PerimetreDesSessions.analyser(casParFichier, MUETTES_ADMISES.keySet());

        cites = new LinkedHashMap<>();
        jugements = new LinkedHashMap<>();
        lireLeCode();

        tri = RepartitionDesCas.repartir(declares.keySet(), perceptifs, jugements);

        // ⚠️ Retirée AVANT d'être réécrite, en deux gestes distincts. Le fichier survit d'un
        // lancement à l'autre : sans ce retrait, une dérivation qui cesserait de tourner
        // laisserait la liste d'hier en place, et son garde resterait vert dessus. Retirer
        // l'appel qui suit fait maintenant rougir, ce qu'on a vérifié.
        retirerLesClassesAFilmer();
        deposerLesClassesAFilmer();
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
                "%nCorrespondance recette (%s) : %d cas déclarés, %d assertés, %d perceptifs, %d non couverts.%n",
                perimetre.assiette(),
                declares.size(),
                tri.assertes().size(),
                tri.perceptifs().size(),
                tri.nonCouverts().size());

        // ⚠️ Les muettes s'affichent AVANT les cas. Le lecteur qui s'arrête à la première ligne doit
        // déjà savoir ce que le décompte ne couvre pas ; les mettre en pied de sortie reviendrait à
        // les réserver à qui lit tout, c'est-à-dire à personne.
        perimetre
                .muettes()
                .forEach(session -> System.out.printf(
                        "  hors périmètre · %s · %s%n",
                        session, MUETTES_ADMISES.getOrDefault(session, "aucun cas au format lu, et rien ne l'admet")));

        tri.perceptifs()
                .forEach(cas -> System.out.printf(
                        "  perceptif   · %s · %s · %s%n",
                        cas,
                        declares.get(cas),
                        cites.containsKey(cas) ? "joué par " + joindre(cites.get(cas)) : "à jouer"));
        tri.nonCouverts().forEach(cas -> System.out.printf("  non couvert · %s · %s%n", cas, declares.get(cas)));
    }

    @Test
    @DisplayName("Le garde nomme les sessions qu'il ne lit pas")
    void le_perimetre_ne_se_tait_sur_aucune_session() {
        SoftAssertions verifs = new SoftAssertions();

        verifs.assertThat(perimetre.silencesNonDeclares())
                .as(
                        "Ces sessions existent sous %s et ce garde n'en lit aucun cas, sans que rien"
                                + " ne le dise. Un décompte qui tait son assiette se lit plus large"
                                + " qu'il ne porte. Ajoutez-les à MUETTES_ADMISES avec leur raison,"
                                + " ou donnez-leur le format `- **Sxx-NN** ·`.",
                        SESSIONS)
                .isEmpty();

        verifs.assertThat(perimetre.admissionsPerimees())
                .as("Ces sessions sont admises muettes et ne le sont plus - elles rendent des cas,"
                        + " ou elles ont disparu. Retirez-les de MUETTES_ADMISES : une liste"
                        + " d'exceptions qu'on n'élague pas redevient la prose qui dérive.")
                .isEmpty();

        verifs.assertAll();
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

    @Test
    @DisplayName("La liste des classes à filmer est déposée, et elle exclut les exemples")
    void la_liste_des_classes_a_filmer_est_deposee() throws IOException {
        // La planche de contact (#3835) filme ces classes-là et pas d'autres. Une liste vide ou
        // absente ferait tourner une séance qui ne montre rien, sans rien dire.
        assertThat(CLASSES_A_FILMER)
                .as("la liste n a pas ete deposee par CETTE seance : la planche de contact"
                        + " filmerait le vide, ou pire, la liste d une seance precedente")
                .exists();

        List<String> classes = Files.readAllLines(CLASSES_A_FILMER);
        assertThat(classes)
                .as("aucune classe citante : le balayage du classpath ne trouve plus rien")
                .isNotEmpty();
        assertThat(classes)
                .as(
                        "les exemples de %s imitent un test sans rien couvrir : les filmer donnerait des"
                                + " extraits d'un décor, pas du produit",
                        FixtureDeRecette.class.getSimpleName())
                .doesNotContain("ReperesDeSeanceTest");
        assertThat(classes).doesNotHaveDuplicates().isSorted();
        assertThat(classes)
                .as("la liste déposée doit être celle de CETTE séance, pas celle d'une précédente")
                .containsExactlyElementsOf(classesCitantes());
    }

    // ----------------------------------------------------------------------------------------

    /// Les classes qui citent au moins un cas, triées.
    private static Set<String> classesCitantes() {
        Set<String> classes = new TreeSet<>();
        cites.values().forEach(tests -> tests.forEach(test -> classes.add(test.substring(0, test.indexOf('.')))));
        return classes;
    }

    /// Retire la liste de la séance précédente, pour que la suivante ne puisse pas la relire.
    private static void retirerLesClassesAFilmer() {
        try {
            Files.deleteIfExists(CLASSES_A_FILMER);
        } catch (IOException e) {
            throw new UncheckedIOException("Liste des classes à filmer impossible à retirer", e);
        }
    }

    /// Dépose les classes qui citent au moins un cas, une par ligne, triées.
    private static void deposerLesClassesAFilmer() {
        try {
            Files.createDirectories(CLASSES_A_FILMER.getParent());
            Files.write(CLASSES_A_FILMER, classesCitantes());
        } catch (IOException e) {
            throw new UncheckedIOException("Liste des classes à filmer impossible à écrire", e);
        }
    }

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
                        String nom = f.getFileName().toString();
                        // ⚠️ Posé à zéro AVANT de lire, pour que les sessions dont la regex ne tire
                        // rien figurent quand même au périmètre. Ne compter que ce qui parle rendrait
                        // le silence indiscernable de l'absence : le défaut même de #3884.
                        casParFichier.put(nom, 0);
                        Matcher m = CAS.matcher(lire(f));
                        while (m.find()) {
                            declares.putIfAbsent(m.group(1), nom);
                            casParFichier.merge(nom, 1, Integer::sum);
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

package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
/// ## Ses DEUX devoirs, et le second est celui qu'on oublie
///
/// 1. tout identifiant cité par un test **existe** dans une session ;
/// 2. les cas que **rien** ne couvre sont **listés**.
///
/// ⚠️ Sans le second, un garde qui ne trouve rien à redire est **indiscernable** d'un garde qui ne
/// lit pas le document. Le premier devoir seul serait vert sur un dépôt où aucun test ne cite quoi
/// que ce soit.
///
/// « Aucun test » reste parfaitement recevable : c'est même le cas normal pour les cas perceptifs,
/// que la passe 6 envoie en recette **parce qu'**ils ne sont pas automatisables. Ce garde ne
/// réclame donc pas la couverture, il refuse qu'elle soit **tacite**.
class CorrespondanceRecetteTest {

    private static final Path SESSIONS = Path.of("dev-docs", "recette", "sessions");

    /// Un cas se déclare `- **S1-04** · texte` : la convention de S9 et S3, désormais celle de S1.
    private static final Pattern CAS = Pattern.compile("^- \\*\\*(S\\d+-\\d+)\\*\\* ·", Pattern.MULTILINE);

    /// Les cas déclarés par les sessions, par identifiant.
    private static Map<String, String> declares;

    /// Les cas cités par le code, et par quel test.
    private static Map<String, Set<String>> cites;

    @BeforeAll
    static void lire() {
        declares = casDeclares();
        cites = casCites();
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

        Set<String> nonCouverts = new TreeSet<>(declares.keySet());
        nonCouverts.removeAll(cites.keySet());

        System.out.printf(
                "%nCorrespondance recette : %d cas déclarés, %d couverts, %d non couverts.%n",
                declares.size(), declares.size() - nonCouverts.size(), nonCouverts.size());
        nonCouverts.forEach(cas -> System.out.printf("  non couvert · %s · %s%n", cas, declares.get(cas)));
    }

    // ----------------------------------------------------------------------------------------

    private static Map<String, String> casDeclares() {
        Map<String, String> trouves = new LinkedHashMap<>();
        if (!Files.isDirectory(SESSIONS)) {
            return trouves;
        }
        try (Stream<Path> fichiers = Files.list(SESSIONS)) {
            fichiers.filter(f -> f.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .forEach(f -> {
                        Matcher m = CAS.matcher(lire(f));
                        while (m.find()) {
                            trouves.putIfAbsent(m.group(1), f.getFileName().toString());
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return trouves;
    }

    private static Map<String, Set<String>> casCites() {
        JavaClasses classes = new ClassFileImporter().importPackages("fr.univ_amu.iut");
        Map<String, Set<String>> trouves = new LinkedHashMap<>();
        classes.forEach(classe -> classe.getMethods()
                .forEach(methode -> methode.tryGetAnnotationOfType(CasDeRecette.class)
                        .ifPresent(annotation -> {
                            List<String> cas = new ArrayList<>(List.of(annotation.value()));
                            cas.forEach(id -> trouves.computeIfAbsent(id, k -> new LinkedHashSet<>())
                                    .add(classe.getSimpleName() + "." + methode.getName()));
                        })));
        return trouves;
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

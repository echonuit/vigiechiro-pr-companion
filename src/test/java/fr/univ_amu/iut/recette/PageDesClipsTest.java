package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La page qui montre les clips dit-elle la vérité sur ce que le banc joue (#4056) ?
///
/// ## Ce qu'elle peut mentir, et comment
///
/// `dev-docs/recette/clips.md` écrit **en dur** l'adresse de chaque clip, parce que la pré-version
/// qui les porte est roulante et que ses adresses ne changent pas. Une page écrite à la main devant
/// une liste qui bouge se périme de deux façons, et **aucune ne se voit** :
///
/// - un cas perceptif est ajouté, et la page ne le montre pas : le clip existe, personne ne le
///   regarde, et le cas reste non tranché sans que rien ne le dise ;
/// - une méthode de test est renommée, et l'adresse écrite dans la page pointe sur un fichier qui
///   n'est plus produit : le lecteur voit un lecteur vide et conclut ce qu'il veut.
///
/// Les deux cas ci-dessous gardent chacun l'une de ces dérives.
///
/// ⚠️ Ce test ne vérifie **pas** que les fichiers sont en ligne : une pré-version se peuple par un
/// tournage, qui est manuel. Un clip absent dit « le tournage n'a pas eu lieu depuis », pas « le
/// produit est cassé », et la page le dit à son lecteur.
class PageDesClipsTest {

    private static final Path PAGE = Path.of("dev-docs", "recette", "clips.md");

    private static final Path PAGE_ASSERTES = Path.of("dev-docs", "recette", "clips-assertes.md");

    private static final Path SESSIONS = Path.of("dev-docs", "recette", "sessions");

    private static final Path SOURCES_DE_TEST = Path.of("src", "test", "java");

    /// L'adresse d'un clip dans la page : `.../clips-recette/<Classe>.<methode>.mp4`.
    private static final Pattern CLIP =
            Pattern.compile("/releases/download/clips-recette/([A-Za-z0-9]+)\\.([a-z0-9_]+)\\.mp4");

    /// Le titre d'une section de cas : `### S6-27 · ...`.
    private static final Pattern SECTION = Pattern.compile("^### (S\\d+-\\d+) ·", Pattern.MULTILINE);

    /// Une annotation `@CasDeRecette(...)` suivie, quelques lignes plus bas, de sa méthode de test.
    /// Les trois formes employées dans le dépôt sont couvertes : la valeur seule, `value = "…"`, et
    /// la liste `{"…", "…"}`.
    private static final Pattern CITATION = Pattern.compile(
            "@CasDeRecette\\([^)]*\\)(?:\\s*@[A-Za-z]+\\([^)]*\\))*\\s*(?:void|[A-Za-z<>\\[\\]]+)\\s+([a-z_0-9]+)\\s*\\(",
            Pattern.DOTALL);

    @Test
    @DisplayName("#4056 : la page montre exactement les cas perceptifs, ni plus ni moins")
    void la_page_montre_exactement_les_cas_perceptifs() {
        Set<String> surLaPage = new TreeSet<>();
        Matcher m = SECTION.matcher(lire(PAGE));
        while (m.find()) {
            surLaPage.add(m.group(1));
        }

        assertThat(surLaPage)
                .as("un cas perceptif absent de la page est un cas que personne ne regarde, et rien"
                        + " d'autre ne le signalerait")
                .containsExactlyInAnyOrderElementsOf(casPerceptifs());
    }

    @Test
    @DisplayName("#4056 : chaque adresse écrite dans la page désigne une méthode de test qui existe")
    void chaque_adresse_designe_une_methode_qui_existe() {
        Map<String, String> introuvables = new TreeMap<>();
        Matcher m = CLIP.matcher(lire(PAGE));
        while (m.find()) {
            String classe = m.group(1);
            String methode = m.group(2);
            Path source = sourceDe(classe);
            if (source == null || !lire(source).contains("void " + methode + "(")) {
                introuvables.put(classe + "." + methode, source == null ? "classe absente" : "méthode absente");
            }
        }

        assertThat(introuvables)
                .as("une adresse qui ne correspond plus à un test rend un lecteur vide, et un lecteur"
                        + " vide se lit comme un défaut du produit")
                .isEmpty();
    }

    @Test
    @DisplayName("#4056 : tout test qui cite un cas a son clip sur l'une des deux pages")
    void tout_test_qui_cite_un_cas_est_sur_une_page() {
        String pages = lire(PAGE) + lire(PAGE_ASSERTES);
        Set<String> absents = new TreeSet<>();
        for (String joue : testsQuiCitentUnCas()) {
            if (!pages.contains(joue + ".mp4")) {
                absents.add(joue);
            }
        }

        assertThat(absents)
                .as("le tournage produit un clip par test qui cite un cas ; un clip qu'aucune page ne"
                        + " montre est un clip que personne ne verra, et rien d'autre ne le dirait")
                .isEmpty();
    }

    // --------------------------------------------------------------------------------------------

    /// Les `Classe.methode` des tests qui citent un cas de recette, lus dans les sources.
    ///
    /// ⚠️ Lus dans les SOURCES et non par réflexion : c'est le tournage qui produit les clips, et il
    /// se règle sur ces annotations-là. Une annotation présente mais un test renommé donnerait un
    /// clip d'un autre nom, que les pages ne montreraient plus.
    private static Set<String> testsQuiCitentUnCas() {
        Set<String> joues = new TreeSet<>();
        try (Stream<Path> fichiers = Files.walk(SOURCES_DE_TEST)) {
            for (Path source : fichiers.filter(f -> f.toString().endsWith("Test.java"))
                    // ⚠️ Les tests de l'outillage de recette lui-même sont écartés. Leurs annotations
                    // sont des EXEMPLES : elles imitent un test qui cite un cas, sans rien couvrir.
                    // `CorrespondanceRecetteTest` les écarte déjà, et note qu'un balayage des sources
                    // « ramène deux faux positifs » - ce sont exactement ceux que ce cas a trouvés,
                    // `ReperesDeSeanceTest.avec_cas` et `.deux_cas`.
                    //
                    // Une règle sur le paquet plutôt qu'une liste de noms : la liste vieillirait, et
                    // le prochain exemple de l'outillage y manquerait sans que rien ne le dise.
                    .filter(f -> !f.toString().contains("/iut/recette/"))
                    .toList()) {
                String classe = source.getFileName().toString().replace(".java", "");
                String contenu = lire(source);
                Matcher m = CITATION.matcher(contenu);
                while (m.find()) {
                    joues.add(classe + "." + m.group(1));
                }
            }
        } catch (IOException echec) {
            throw new UncheckedIOException("Sources de test illisibles : " + SOURCES_DE_TEST, echec);
        }
        return joues;
    }

    /// Les cas que les sessions marquent `*perceptif*`. Le motif vient de [MotifDeCas] : trois
    /// lecteurs de ces fichiers coexistent, et deux se sont déjà trompés en le réécrivant.
    private static Set<String> casPerceptifs() {
        Set<String> perceptifs = new TreeSet<>();
        for (Path fichier : sessions()) {
            Matcher m = MotifDeCas.CAS.matcher(lire(fichier));
            while (m.find()) {
                if (m.group(2) != null) {
                    perceptifs.add(m.group(1));
                }
            }
        }
        return perceptifs;
    }

    private static List<Path> sessions() {
        try (Stream<Path> fichiers = Files.list(SESSIONS)) {
            return fichiers.filter(f -> f.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException echec) {
            throw new UncheckedIOException("Sessions illisibles : " + SESSIONS, echec);
        }
    }

    /// Le fichier source d'une classe de test, cherché par son nom simple.
    private static Path sourceDe(String classe) {
        try (Stream<Path> fichiers = Files.walk(SOURCES_DE_TEST)) {
            return fichiers.filter(f -> f.getFileName().toString().equals(classe + ".java"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException echec) {
            throw new UncheckedIOException("Sources de test illisibles : " + SOURCES_DE_TEST, echec);
        }
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (IOException echec) {
            throw new UncheckedIOException("Fichier illisible : " + fichier, echec);
        }
    }
}

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

    /// La page des cas perceptifs. Ce n'est PAS `clips.md`, qui est l'entrée de la section et ne
    /// porte aucun lecteur : les deux familles ont chacune la sienne, et la navigation le dit.
    private static final Path PAGE = Path.of("dev-docs", "recette", "clips-perceptifs.md");

    private static final Path PAGE_ASSERTES = Path.of("dev-docs", "recette", "clips-assertes.md");

    /// La page des clips tournés contre la VRAIE plateforme (#4306). Ce n'est pas une troisième
    /// famille de cas : ce sont les MÊMES cas, vus contre une autre frontière, et leurs pièces vivent
    /// sur une autre pré-version parce que des données vivantes ne se comparent pas.
    ///
    /// ⚠️ Elle compte pour ce garde au même titre que les deux autres. Un clip qu'aucune page ne
    /// montre est un clip que personne ne verra, et la source ne change rien à cela.
    private static final Path PAGE_CONNECTES = Path.of("dev-docs", "recette", "clips-connectes.md");

    private static final Path SESSIONS = Path.of("dev-docs", "recette", "sessions");

    private static final Path SOURCES_DE_TEST = Path.of("src", "test", "java");

    /// L'adresse d'un clip dans une page : `.../clips-recette/<Classe>.<methode>.mp4`, ou la même
    /// sous `clips-connectes` pour un clip tourné contre la plateforme (#4306).
    ///
    /// ⚠️ Les deux pré-versions, et pas un motif ouvert sur n'importe quelle destination : une
    /// adresse qui pointerait ailleurs - un tag de version, par exemple - montrerait un clip figé
    /// pendant que la page prétend montrer le tournage courant.
    private static final Pattern CLIP =
            Pattern.compile("/releases/download/(?:clips-recette|clips-connectes)/([A-Za-z0-9]+)\\.([a-z0-9_]+)\\.mp4");

    /// Le titre d'une section de cas : `### S6-27 · ...`.
    private static final Pattern SECTION = Pattern.compile("^### (S\\d+-\\d+) ·", Pattern.MULTILINE);

    /// Une annotation `@CasDeRecette(...)` suivie, quelques lignes plus bas, de sa méthode de test.
    ///
    /// Les deux formes employées dans le dépôt sont couvertes : `value = "…"` et la liste
    /// `value = {"…", "…"}`. La forme abrégée - la valeur seule, sans `value =` - n'existe plus depuis
    /// que [Portee] est obligatoire : une annotation qui porte un second attribut doit nommer le
    /// premier.
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
    @DisplayName("#4056 : chaque adresse écrite dans les pages de clips désigne une méthode qui existe")
    void chaque_adresse_designe_une_methode_qui_existe() {
        Map<String, String> introuvables = new TreeMap<>();
        // ⚠️ Les DEUX pages. Ce garde s'appelait « chaque adresse » et ne lisait que celle des cas
        // perceptifs : la page des cas assertés a nommé pendant un temps `S1-16 · bouton_synchro_visible`,
        // un test scindé en deux et qui n'existait plus, sans que rien ne rougisse. Le voisin
        // `tout_test_qui_cite_un_cas_est_sur_une_page` lit bien les deux ; celui-ci en avait oublié une,
        // et son nom promettait le contraire.
        //
        // Le sens de la vérification n'est pas symétrique : le voisin part des TESTS et vérifie qu'ils
        // sont annoncés ; celui-ci part des ADRESSES et vérifie qu'elles mènent quelque part. Un test
        // renommé casse le second, pas le premier.
        Matcher m = CLIP.matcher(lire(PAGE) + "\n" + lire(PAGE_ASSERTES) + "\n" + lire(PAGE_CONNECTES));
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
    @DisplayName("#4056 : tout test qui cite un cas a son clip sur l'une des pages")
    void tout_test_qui_cite_un_cas_est_sur_une_page() {
        String pages = lire(PAGE) + lire(PAGE_ASSERTES) + lire(PAGE_CONNECTES);
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
                // Le groupe 2 porte désormais TOUTE la suite de marqueurs (#4417), et non le
                // seul `*perceptif*` : un cas qui déclare son geste passerait pour perceptif si
                // on se contentait de le tester non nul. C'est le troisième lecteur des
                // sessions, et MotifDeCas existe pour qu'il ne réinvente pas la lecture.
                if (MotifDeCas.marqueurs(m.group(2)).containsKey(MotifDeCas.PERCEPTIF)) {
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

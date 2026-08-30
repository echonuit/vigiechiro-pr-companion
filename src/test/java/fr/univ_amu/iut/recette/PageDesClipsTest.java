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
/// Ce test ne vérifie **pas** que les fichiers sont en ligne : une pré-version se peuple par un
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
    /// Elle compte pour ce garde au même titre que les deux autres. Un clip qu'aucune page ne
    /// montre est un clip que personne ne verra, et la source ne change rien à cela.
    private static final Path PAGE_CONNECTES = Path.of("dev-docs", "recette", "clips-connectes.md");

    /// La page d'entrée de la section. Son tableau « Deux familles, deux pages » annonce combien
    /// de cas chaque famille porte, et ces nombres se périment comme le chapeau : ils sont écrits à
    /// la main devant une liste qui bouge. Les deux l'étaient au 2026-08-30, à 9 et 46 pour 15 et 55.
    private static final Path PAGE_ENTREE = Path.of("dev-docs", "recette", "clips.md");

    private static final Path SESSIONS = Path.of("dev-docs", "recette", "sessions");

    private static final Path SOURCES_DE_TEST = Path.of("src", "test", "java");

    /// L'adresse d'un clip dans une page : `.../clips-recette/<Classe>.<methode>.mp4`, ou la même
    /// sous `clips-connectes` pour un clip tourné contre la plateforme (#4306).
    ///
    /// Les deux pré-versions, et pas un motif ouvert sur n'importe quelle destination : une
    /// adresse qui pointerait ailleurs - un tag de version, par exemple - montrerait un clip figé
    /// pendant que la page prétend montrer le tournage courant.
    private static final Pattern CLIP =
            Pattern.compile("/releases/download/(?:clips-recette|clips-connectes)/([A-Za-z0-9]+)\\.([a-z0-9_]+)\\.mp4");

    /// Le titre d'une section de cas : `### S6-27 · ...`.
    private static final Pattern SECTION = Pattern.compile("^### (S\\d+-\\d+) ·", Pattern.MULTILINE);

    /// Les deux nombres du chapeau : « Ces **15** cas [...] tiennent en **10** clips ».
    ///
    /// Deux motifs et non un : les nombres comptent des choses différentes - un cas est une section,
    /// un clip est une adresse - et six cas partagent une seule image. Un motif unique ferait passer
    /// l'un pour l'autre, ce qui est exactement l'erreur que la phrase existe pour écarter.
    private static final Pattern CHAPEAU_CAS = Pattern.compile("Ces \\*\\*(\\d+)\\*\\* cas");

    private static final Pattern CHAPEAU_CLIPS = Pattern.compile("tiennent en \\*\\*(\\d+)\\*\\* clips");

    /// Une ligne du tableau des familles : le lien vers la page, puis son compte en dernière colonne.
    ///
    /// Le lien est capturé et non le libellé : c'est lui qui dit QUELLE page compter, et un libellé
    /// retouché ne doit pas faire porter le compte d'une famille sur l'autre.
    private static final Pattern LIGNE_FAMILLE =
            Pattern.compile("\\[Cas [^\\]]+\\]\\((clips-[a-z]+\\.md)\\)[^|\\n]*\\|[^|\\n]*\\|\\s*(\\d+)\\s*\\|");

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

    /// La troisième façon dont cette page se périme, et celle qu'aucun cas ne gardait : son
    /// **chapeau annonce un nombre**, et ajouter un cas ne le change pas.
    ///
    /// Mesuré (#4820) : le palier de #4447 a porté la page de neuf cas à quinze, et le chapeau a
    /// continué d'écrire « ces neuf-là ». Le cas ci-dessus était vert pendant ce temps, et à juste
    /// titre : il garde l'**ensemble** des cas, pas leur compte. Rien d'autre ne lit cette phrase.
    ///
    /// La page se contredit alors elle-même - le chapeau dit neuf, les sections en montrent quinze -
    /// et rien ne dit au lecteur laquelle des deux croire, donc il doute des deux.
    @Test
    @DisplayName("#4820 : le chapeau annonce le nombre de cas et de clips que la page porte")
    void le_chapeau_annonce_les_bons_nombres() {
        String page = lire(PAGE);

        Set<String> sections = sectionsDe(page);
        Set<String> clips = clipsDe(page);

        assertThat(annonce(page, CHAPEAU_CAS))
                .as("le chapeau annonce un nombre de cas que la page ne porte pas")
                .isEqualTo(sections.size());
        assertThat(annonce(page, CHAPEAU_CLIPS))
                .as("le chapeau annonce un nombre de clips DISTINCTS que la page ne porte pas :"
                        + " six cas partagent la même image, et ce partage est ce que la phrase dit")
                .isEqualTo(clips.size());
    }

    /// Le même mensonge, une page plus haut. Le tableau « Deux familles, deux pages » de
    /// [#PAGE_ENTREE] annonce le compte de chaque famille, et personne ne le relit en ajoutant un cas.
    ///
    /// Mesuré le 2026-08-30 : il disait **9** et **46** pour **15** et **55**. Le second était déjà
    /// faux avant le palier de #4447 - donc ce n'est pas un oubli, c'est le régime normal d'un nombre
    /// que rien ne garde.
    ///
    /// Ce cas lit le tableau plutôt que d'écrire les deux chemins : une troisième famille ajoutée à
    /// la table sera comptée sans qu'on y pense, et c'est le contraire qui a produit le défaut.
    ///
    /// Il compte des **clips** et non des cas, ce que la colonne dit désormais. Les deux nombres
    /// divergent depuis le palier de #4447 : un titre de la page des assertés peut couvrir une plage
    /// - `### S2-01 à S2-07 · ...` - et six cas perceptifs partagent une seule image. Compter les
    /// sections rendait 38 là où la page porte 55 clips, et la colonne ne disait pas laquelle des
    /// deux grandeurs elle annonçait.
    @Test
    @DisplayName("#4820 : le tableau des familles annonce le compte que chaque page porte")
    void le_tableau_des_familles_annonce_les_bons_comptes() {
        Map<String, Integer> annonces = new TreeMap<>();
        Matcher m = LIGNE_FAMILLE.matcher(lire(PAGE_ENTREE));
        while (m.find()) {
            annonces.put(m.group(1), Integer.parseInt(m.group(2)));
        }

        assertThat(annonces)
                .as("le tableau des familles est la porte d'entrée de la section : s'il ne liste plus"
                        + " les deux pages, ce cas ne garde plus rien et doit le dire")
                .hasSize(2);

        Map<String, Integer> reels = new TreeMap<>();
        for (String page : annonces.keySet()) {
            reels.put(page, clipsDe(lire(PAGE_ENTREE.resolveSibling(page))).size());
        }
        assertThat(annonces)
                .as("un compte annoncé qui n'est pas celui de la page laisse le lecteur devant deux"
                        + " chiffres contradictoires, sans savoir lequel croire")
                .isEqualTo(reels);
    }

    @Test
    @DisplayName("#4056 : chaque adresse écrite dans les pages de clips désigne une méthode qui existe")
    void chaque_adresse_designe_une_methode_qui_existe() {
        Map<String, String> introuvables = new TreeMap<>();
        // Les DEUX pages. Ce garde s'appelait « chaque adresse » et ne lisait que celle des cas
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

    @Test
    @DisplayName("#4522 : l'exclusion de l'outillage ne dépend pas du séparateur du système")
    void l_exclusion_ne_depend_pas_du_separateur() {
        // Ce cas existe parce que la suite hebdomadaire a rougi sous Windows là où Linux et macOS
        // étaient verts : l'exclusion comparait à « /iut/recette/ », séparateur codé en dur, et les
        // deux annotations d'exemple de `ReperesDeSeanceTest` entraient dans la population.
        assertThat(dansOutillageDeRecette("src/test/java/fr/univ_amu/iut/recette/ReperesDeSeanceTest.java"))
                .as("chemin POSIX")
                .isTrue();
        assertThat(dansOutillageDeRecette("src\\test\\java\\fr\\univ_amu\\iut\\recette\\ReperesDeSeanceTest.java"))
                .as("le même chemin sous Windows : c'est ce cas qui rougissait en intégration")
                .isTrue();
        assertThat(dansOutillageDeRecette("src/test/java/fr/univ_amu/iut/sites/view/ScenarioFicheSiteTest.java"))
                .as("hors de l'outillage, dans les deux écritures")
                .isFalse();
        assertThat(dansOutillageDeRecette(
                        "src\\test\\java\\fr\\univ_amu\\iut\\sites\\view\\ScenarioFicheSiteTest.java"))
                .isFalse();
    }

    // --------------------------------------------------------------------------------------------

    /// Ce chemin est-il dans l'outillage de recette, quel que soit le séparateur du système ?
    ///
    /// `Path.toString()` rend `...\iut\recette\...` sous Windows, où comparer à `/iut/recette/` ne
    /// trouve jamais rien : le filtre ne s'appliquait pas, et les deux annotations d'exemple entraient
    /// dans la population (#4522). Prend une `String` et non un `Path` pour rester **éprouvable
    /// partout**, un `Path.of` construit sous Linux ne portant jamais d'antislash : c'est la couture
    /// que l'ADR 3802 exige, et l'idiome qu'[fr.univ_amu.iut.architecture.AnnonceDesMutationsTest]
    /// porte déjà (#3645).
    static boolean dansOutillageDeRecette(String chemin) {
        return chemin.replace('\\', '/').contains("/iut/recette/");
    }

    /// Les identifiants de cas qu'une page montre, lus dans ses titres de section.
    private static Set<String> sectionsDe(String page) {
        Set<String> cas = new TreeSet<>();
        Matcher m = SECTION.matcher(page);
        while (m.find()) {
            cas.add(m.group(1));
        }
        return cas;
    }

    /// Les clips DISTINCTS qu'une page montre, lus dans les adresses qu'elle écrit.
    private static Set<String> clipsDe(String page) {
        Set<String> adresses = new TreeSet<>();
        Matcher m = CLIP.matcher(page);
        while (m.find()) {
            adresses.add(m.group(1) + "." + m.group(2));
        }
        return adresses;
    }

    /// Le nombre qu'annonce le chapeau, ou `-1` si la phrase a disparu.
    ///
    /// `-1` et non une exception : une phrase réécrite doit rougir **sur l'assertion**, en disant
    /// quel nombre la page porte, plutôt que casser avant elle sur un `NoSuchElement` que personne
    /// ne relie au chapeau.
    private static int annonce(String page, Pattern phrase) {
        Matcher m = phrase.matcher(page);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /// Les `Classe.methode` des tests qui citent un cas de recette, lus dans les sources.
    ///
    /// Lus dans les SOURCES et non par réflexion : c'est le tournage qui produit les clips, et il
    /// se règle sur ces annotations-là. Une annotation présente mais un test renommé donnerait un
    /// clip d'un autre nom, que les pages ne montreraient plus.
    private static Set<String> testsQuiCitentUnCas() {
        Set<String> joues = new TreeSet<>();
        try (Stream<Path> fichiers = Files.walk(SOURCES_DE_TEST)) {
            for (Path source : fichiers.filter(f -> f.toString().endsWith("Test.java"))
                    // Les tests de l'outillage de recette lui-même sont écartés. Leurs annotations
                    // sont des EXEMPLES : elles imitent un test qui cite un cas, sans rien couvrir.
                    // `CorrespondanceRecetteTest` les écarte déjà, et note qu'un balayage des sources
                    // « ramène deux faux positifs » - ce sont exactement ceux que ce cas a trouvés,
                    // `ReperesDeSeanceTest.avec_cas` et `.deux_cas`.
                    //
                    // Une règle sur le paquet plutôt qu'une liste de noms : la liste vieillirait, et
                    // le prochain exemple de l'outillage y manquerait sans que rien ne le dise.
                    .filter(f -> !dansOutillageDeRecette(f.toString()))
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

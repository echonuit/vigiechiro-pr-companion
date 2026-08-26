package fr.univ_amu.iut.recette.film;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;

/// L'index qui se lit par CAS, écrit une fois la session JUnit terminée.
///
/// Il tient le même rôle que celui du script, à un point près : la colonne « comment auditer » ne se
/// déduit plus d'une part d'images claires, mais du fait qu'une fenêtre a paru, mesuré par la caméra. La
/// frontière reste « quelque chose a paru, ou rien », mais elle est **constatée** au lieu d'être inférée.
///
/// **Des fragments, et pas un seul fichier.** Une session JUnit n'est pas une JVM : surefire tourne à
/// `forkCount=1C` et chacune tient son index. Écrire `index.md` directement laissait la dernière JVM
/// effacer le travail des autres, mesuré sur quatre forks et neuf cas dont l'index final en portait
/// cinq. Un index amputé se lit exactement comme un index complet, ce qui en fait la forme la plus
/// dangereuse d'un défaut. Chaque JVM dépose donc son fragment dans `index.d/` puis reconstruit
/// `index.md` à partir de **tous** les fragments présents.
///
/// Lire les fragments puis écrire l'index est **indivisible** : sans le verrou, une JVM qui a lu la
/// liste trop tôt écrirait après les autres un index plus pauvre.
///
/// Limite assumée : un dossier de tournage réutilisé garde les fragments du précédent, et l'index les
/// compterait. C'est le bon prix, le défaut inverse effaçant des lignes ; le nombre de fragments
/// fusionnés est annoncé à chaque écriture, et la CI part d'un `target/` neuf.
public final class IndexDesCas implements ExtensionContext.Store.CloseableResource {

    public record Ligne(String cas, String test, String clip, boolean fenetreVue) {
        String commentAuditer() {
            return fenetreVue ? "en regardant" : "en lisant le test";
        }

        /// Une ligne de fragment. Le séparateur est la tabulation, qu'aucun des quatre champs ne
        /// peut contenir : un cas et un test sont des identifiants Java ou des codes de session, un
        /// nom de clip est dérivé des deux.
        String versFragment() {
            return String.join("\t", cas, test, clip, String.valueOf(fenetreVue));
        }

        static Ligne depuisFragment(String ligne) {
            String[] champs = ligne.split("\t", -1);
            return new Ligne(champs[0], champs[1], champs[2], Boolean.parseBoolean(champs[3]));
        }

        /// Ce qui fait qu'une ligne est la MÊME qu'une autre. Un cas peut être couvert par
        /// plusieurs tests, et un test peut citer plusieurs cas : c'est la paire qui identifie.
        String cle() {
            return cas + "\t" + test;
        }
    }

    private static final String ENTETE = """
            # Cas filmés

            Un clip par **test**, parce que c'est ce que la JVM sait borner ; cet index se lit par
            **cas**, parce que c'est ce qu'on cherche. Un cas couvert par plusieurs tests a donc
            plusieurs lignes.

            ## Comment auditer : en regardant, ou en lisant

            Tous les tests qui citent un cas n'ouvrent pas de fenêtre. Un ViewModel en cite et ne
            montre rien : son clip s'arrête à son carton, et c'est le résultat **juste**. La
            dernière colonne dit, pour chaque ligne, par quel moyen le cas s'audite.

            | Cas | Clip | Ce qu'il joue | Comment l'auditer |
            |---|---|---|---|
            """;

    private static final String DOSSIER_DES_FRAGMENTS = "index.d";
    private static final String SUFFIXE = ".tsv";

    private final Path fichier;
    private final String identite;
    private final List<Ligne> lignes = Collections.synchronizedList(new ArrayList<>());

    public IndexDesCas(Path fichier) {
        this(fichier, identiteParDefaut());
    }

    /// L'identité de CETTE JVM, unique même parmi celles qui ne vivent pas en même temps.
    ///
    /// Le numéro de processus ne suffit pas, et c'est macOS qui l'a dit. La première version
    /// nommait le fragment d'après le seul `pid`. Sous Linux et Windows les forks vivaient
    /// ensemble, donc leurs numéros différaient et rien ne paraissait. Sur un runner macOS, moins
    /// de coeurs : les forks se sont ENCHAÎNÉS, le système a recyclé un numéro libéré, et la JVM de
    /// `ScenarioPerceptifRefusDepotTest` a écrasé le fragment de `ScenarioPerceptifRecuperationCarreTest`.
    /// Neuf clips produits, index à huit lignes, `S1-37` disparu.
    ///
    /// C'est le JUMEAU du défaut que les fragments corrigeaient : une écriture qui en efface une
    /// autre sans un mot. Le remède avait déplacé la collision d'un cran, du fichier unique au
    /// numéro de processus, sans la retirer. Un identifiant qui se réemploie n'identifie pas.
    ///
    /// Le `pid` est gardé en tête parce qu'il se lit, et se rattache à une ligne de journal.
    static String identiteParDefaut() {
        return ProcessHandle.current().pid() + "-" + UUID.randomUUID();
    }

    /// L'identité est un paramètre pour que le défaut d'origine soit REPRODUCTIBLE. Il ne se
    /// produit qu'entre deux JVM, et un test ne peut pas en démarrer une seconde : sans cette
    /// couture, la seule façon de voir l'index amputé serait de lancer un tournage entier, ce qui
    /// revient à ne jamais le voir.
    ///
    /// Elle sert à ORDONNER les fragments dans les cas de garde, jamais à leur donner un nom
    /// stable : deux séances qui se disent la même identité s'effacent, et c'est précisément le
    /// défaut que [#identiteParDefaut()] existe pour empêcher.
    IndexDesCas(Path fichier, String identite) {
        this.fichier = fichier;
        this.identite = identite;
    }

    public void ajouter(Ligne ligne) {
        lignes.add(ligne);
    }

    @Override
    public void close() throws IOException {
        if (lignes.isEmpty()) {
            return;
        }
        Path fragments = fichier.resolveSibling(DOSSIER_DES_FRAGMENTS);
        Files.createDirectories(fragments);
        deposer(fragments);

        // Le verrou porte sur la SUITE lecture-puis-écriture, pas sur l'écriture seule.
        Path verrouillage = fragments.resolve("index.lock");
        try (FileChannel canal = FileChannel.open(verrouillage, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock verrou = canal.lock()) {
            reconstruire(fragments, verrou);
        }
    }

    /// Le fragment de CETTE JVM.
    private void deposer(Path fragments) throws IOException {
        Path mien = fragments.resolve("index-" + identite + SUFFIXE);
        List<String> ecrites = new ArrayList<>();
        synchronized (lignes) {
            for (Ligne ligne : lignes) {
                ecrites.add(ligne.versFragment());
            }
        }
        Files.write(mien, ecrites, StandardCharsets.UTF_8);
    }

    /// Relit tous les fragments et rend l'index entier.
    ///
    /// @param verrou le verrou tenu par l'appelant. Il n'est pas lu : il est là pour qu'aucun
    ///     appelant ne puisse écrire cette méthode sans l'avoir pris.
    private void reconstruire(Path fragments, FileLock verrou) throws IOException {
        assert verrou.isValid() : "la reconstruction se fait verrou tenu";
        Map<String, Ligne> parCle = new LinkedHashMap<>();
        int fusionnes = 0;
        try (Stream<Path> presents = Files.list(fragments)) {
            List<Path> tries = presents.filter(p -> p.getFileName().toString().endsWith(SUFFIXE))
                    .sorted()
                    .toList();
            fusionnes = tries.size();
            for (Path fragment : tries) {
                for (String ligne : Files.readAllLines(fragment, StandardCharsets.UTF_8)) {
                    if (!ligne.isBlank()) {
                        Ligne lue = Ligne.depuisFragment(ligne);
                        parCle.put(lue.cle(), lue);
                    }
                }
            }
        }

        StringBuilder page = new StringBuilder(ENTETE);
        parCle.values().stream()
                .sorted((a, b) -> a.cas().compareTo(b.cas()))
                .forEach(l -> page.append(
                        String.format("| %s | `%s` | %s | %s |%n", l.cas(), l.clip(), l.test(), l.commentAuditer())));
        Files.createDirectories(fichier.getParent());
        Files.writeString(fichier, page.toString(), StandardCharsets.UTF_8);

        long aRegarder = parCle.values().stream().filter(Ligne::fenetreVue).count();
        System.out.printf(
                "  index : %d ligne(s) de cas dont %d à regarder, %d fragment(s) fusionné(s) -> %s%n",
                parCle.size(), aRegarder, fusionnes, fichier);
    }

    void ecrireMaintenant() {
        try {
            close();
        } catch (IOException echec) {
            throw new UncheckedIOException(echec);
        }
    }
}

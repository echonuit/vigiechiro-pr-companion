package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

/// **Cliquet d'unicité de style** (#1974) : une classe CSS a **une seule feuille pour maison**.
///
/// Un même nom dans deux feuilles finit toujours par dire deux choses. Les trois formes rencontrées :
/// la **copie** (`.message-erreur`, `.field-label`, `.menu-actions` recopiés d'une feuille de feature
/// vers l'autre), le **code mort** (`.fil-ariane`, reliquat d'un breadcrumb déplacé, ne ciblant plus
/// rien), et la **collision** (`.carte-chevron`, dont le `-fx-opacity: 0` hérité de l'accueil a rendu
/// invisible le chevron des cartes de sites). Une classe se définit dans la feuille partagée que les
/// vues chargent, ou dans une seule feuille de feature. Jamais deux.
///
/// `.root` est la seule entrée de [#EXCEPTIONS], et elle est structurelle : `palette.css` y pose les
/// jetons, `base.css` la police et le fond. `palette.css` est chargée seule sur les scènes de capture,
/// précisément pour que les jetons se résolvent partout.
///
/// Le même défaut existe **dans une seule feuille** : `.compte-rendu`, défini deux fois dans
/// `design.css` (#2358), a donné en silence une carte blanche à tous les comptes rendus textuels, sans
/// qu'aucun test rougisse. [#chaque_classe_a_une_seule_feuille] couvre la première moitié,
/// [#aucune_classe_n_est_definie_deux_fois_dans_une_feuille] la seconde.
class DoublonsFeuillesDeStyleTest {

    private static final Path RACINE = Path.of("src/main/java/fr/univ_amu/iut");

    /// Sélecteur = **une seule classe simple** en tête de règle (`.foo {`). On ignore les sélecteurs
    /// composés (`.a .b`, `.a.b`, `.a:hover`) : ils qualifient un contexte, pas la définition d'une
    /// classe.
    private static final Pattern DEBUT_DE_REGLE = Pattern.compile("(?m)^\\s*(\\.[\\w-]+)\\s*\\{");

    /// Classes légitimement présentes dans deux feuilles, avec la raison. À garder minimal : chaque
    /// entrée est une dette qu'on choisit d'assumer.
    private static final Set<String> EXCEPTIONS = Set.of(".root");

    @Test
    @DisplayName("Aucune classe CSS n'est définie dans deux feuilles (une seule maison par classe)")
    void chaque_classe_a_une_seule_feuille() {
        Map<String, TreeSet<String>> feuillesParClasse = new TreeMap<>();

        for (Path feuille : feuillesDeStyle()) {
            Matcher regle = DEBUT_DE_REGLE.matcher(lire(feuille));
            while (regle.find()) {
                feuillesParClasse
                        .computeIfAbsent(regle.group(1), ignore -> new TreeSet<>())
                        .add(feuille.getFileName().toString());
            }
        }

        List<String> multiFeuilles = feuillesParClasse.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .filter(e -> !EXCEPTIONS.contains(e.getKey()))
                .map(e -> e.getKey() + " : " + e.getValue())
                .toList();

        assertThat(multiFeuilles)
                .as("""
                        Une classe CSS est définie dans plusieurs feuilles.

                        Que les propriétés soient identiques (copie), différentes (collision) ou
                        vides d'effet (code mort), c'est le même piège : deux maisons pour un nom, qui
                        divergeront. #1974 en a soldé une série ; celle-ci ne doit pas s'y rajouter.

                        Donnez à cette classe UNE feuille : la feuille partagée (design.css / base.css)
                        si le concept est transverse, une seule feuille de feature s'il est local. Si
                        deux écrans ont vraiment deux concepts sous ce nom, désambiguïsez-les (comme
                        `.entete-passage` / `.entete-qualification`).

                        Nouvelle exception structurelle légitime ? L'ajouter à EXCEPTIONS **avec sa
                        raison**, pas ici.

                        Classes trouvées dans deux feuilles :
                        %s
                        """.formatted(String.join("\n", multiFeuilles)))
                .isEmpty();
    }

    @Test
    @DisplayName("Aucune classe CSS n'est définie deux fois dans la même feuille (la dernière gagnerait)")
    void aucune_classe_n_est_definie_deux_fois_dans_une_feuille() {
        List<String> redefinitions = new ArrayList<>();

        for (Path feuille : feuillesDeStyle()) {
            Map<String, Integer> occurrences = new TreeMap<>();
            Matcher regle = DEBUT_DE_REGLE.matcher(lire(feuille));
            while (regle.find()) {
                occurrences.merge(regle.group(1), 1, Integer::sum);
            }
            occurrences.entrySet().stream()
                    .filter(e -> e.getValue() >= 2)
                    .forEach(e -> redefinitions.add(
                            feuille.getFileName() + " : " + e.getKey() + " (" + e.getValue() + " fois)"));
        }

        assertThat(redefinitions)
                .as("""
                        Une classe CSS est définie deux fois dans la même feuille.

                        À spécificité égale, c'est la DERNIÈRE règle qui l'emporte : la seconde
                        définition impose ses propriétés à tout ce que la première habillait, sans que
                        rien ne rougisse. C'est ainsi que la bande chiffrée du compte rendu (#2358) a
                        posé une carte blanche et une bordure sur tous les comptes rendus textuels de
                        l'application, sous le nom commun `.compte-rendu`.

                        Deux concepts ne partagent pas un nom : désambiguïsez (comme
                        `.compte-rendu` / `.panneau-compte-rendu`). Si les deux règles décrivent
                        vraiment le même concept, fusionnez-les en une seule.

                        Classes redéfinies :
                        %s
                        """.formatted(String.join("\n", redefinitions)))
                .isEmpty();
    }

    private static List<Path> feuillesDeStyle() {
        try (Stream<Path> chemins = Files.walk(RACINE)) {
            return new ArrayList<>(
                    chemins.filter(p -> p.toString().endsWith(".css")).sorted().toList());
        } catch (IOException echec) {
            throw new UncheckedIOException("balayage des feuilles de style", echec);
        }
    }

    private static String lire(Path feuille) {
        try {
            return Files.readString(feuille);
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + feuille, echec);
        }
    }
}

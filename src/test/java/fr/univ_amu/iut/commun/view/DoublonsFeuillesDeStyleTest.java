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
/// ## Pourquoi ce test existe
///
/// Le défaut que #1974 a soldé revient tout seul : un même nom de classe vit dans deux feuilles, et
/// tôt ou tard les deux disent des choses différentes sans que personne ne le voie. Trois formes,
/// toutes rencontrées :
///
/// - la **copie** : `.message-erreur`, `.field-label`, `.menu-actions` recopiés à l'identique d'une
///   feuille de feature vers l'autre, surchargeant sans rien changer, jusqu'au jour où l'une dérive ;
/// - le **code mort** : `.fil-ariane` de la qualification, reliquat d'un breadcrumb déplacé dans le
///   chrome, ne ciblant plus rien ;
/// - la **collision** : `.entete`, deux écrans, deux paddings sous un même nom - et, pire,
///   `.carte-chevron`, dont la collision avec la classe de l'accueil a rendu **invisible** le chevron
///   des cartes de sites (un `-fx-opacity: 0` hérité).
///
/// Le point commun : **un même nom, deux feuilles**. Ce test refuse cela, quelle que soit la forme.
/// Une classe se définit dans **la feuille partagée** que les vues chargent (`design.css` /
/// `base.css`), ou dans **une seule** feuille de feature. Jamais deux.
///
/// ## La seule exception, et pourquoi elle est structurelle
///
/// `.root` : `palette.css` y pose les **jetons** de couleur, `base.css` la **police et le fond**.
/// La séparation est voulue - `palette.css` est chargée seule sur les scènes de capture, sans
/// `base.css`, précisément pour que les jetons se résolvent partout. Ce sont deux préoccupations sur
/// le sélecteur racine de JavaFX, pas une copie. C'est la seule entrée de [#EXCEPTIONS].
///
/// ## L'angle mort qu'il a fallu boucher
///
/// Ce cliquet n'a longtemps regardé que « le même nom dans **deux feuilles** », et a donc laissé
/// passer le même défaut **dans une seule** : `.compte-rendu`, défini une fois pour le compte rendu
/// textuel ([VueCompteRendu]) puis une seconde fois, plus bas dans `design.css`, pour la bande chiffrée
/// (#2358). À égalité de spécificité, c'est la dernière règle qui gagne : tous les comptes rendus
/// textuels de l'application ont hérité en silence d'une carte blanche, d'une bordure et de 16 px de
/// marge intérieure. Aucun test ne rougissait, et la seule chose qui l'a montré est une capture.
///
/// Une classe se définit donc **une fois**, dans une feuille et une seule : [#chaque_classe_a_une_seule_feuille]
/// couvre la première moitié, [#aucune_classe_n_est_definie_deux_fois_dans_une_feuille] la seconde.
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

    /// **Toute vue qui déclare des feuilles charge celle du socle** (#3966).
    ///
    /// `design.css` porte les classes partagées : `bouton-primaire`, `bouton-secondaire`,
    /// `bouton-danger`, les jetons de pastille, la densité des tables. Une vue qui ne la charge pas
    /// **ne rougit nulle part** : ses contrôles rendent simplement ceux de la plateforme, et le seul
    /// symptôme est un écran qui ne ressemble pas au reste du produit.
    ///
    /// C'est arrivé, et le défaut a survécu à toutes les revues visuelles : `EcranReglages.fxml` a été
    /// **la seule des 24 vues** à ne pas la charger. On l'a découvert en cherchant pourquoi une classe
    /// du socle posée sur un bouton n'avait aucun effet. La classe était inerte, et un test qui aurait
    /// vérifié sa seule présence l'aurait certifiée.
    ///
    /// ⚠️ Ce cliquet ne regarde que les vues qui déclarent **déjà** des feuilles : une vue qui n'en
    /// déclare aucune est un composant rendu dans une scène qui, elle, les porte. Les compter ferait
    /// rougir du travail correct, et un garde qui crie sur du bon travail est un garde qu'on ignore.
    @Test
    @DisplayName("#3966 : toute vue qui déclare des feuilles charge design.css, celle du socle")
    void chaque_vue_charge_la_feuille_du_socle() {
        List<String> sansSocle = vuesFxml().stream()
                .filter(vue -> lire(vue).contains("stylesheets="))
                .filter(vue -> !lire(vue).contains("design.css"))
                .map(Path::toString)
                .sorted()
                .toList();

        assertThat(sansSocle).as("""
                        Ces vues declarent des feuilles de style mais PAS `design.css`, celle du socle.

                        Leurs controles rendent donc ceux de la plateforme : un `bouton-primaire` y est
                        une classe inerte, et rien ne le signale. Ajoutez `@design.css` a leur attribut
                        `stylesheets`, apres `base.css` et avant la feuille de la vue, pour que
                        celle-ci reste autoritaire.

                        Vues concernees :
                        %s
                        """.formatted(String.join("\n", sansSocle))).isEmpty();
    }

    private static List<Path> vuesFxml() {
        try (Stream<Path> chemins = Files.walk(RACINE)) {
            return new ArrayList<>(
                    chemins.filter(p -> p.toString().endsWith(".fxml")).sorted().toList());
        } catch (IOException echec) {
            throw new UncheckedIOException("balayage des vues", echec);
        }
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

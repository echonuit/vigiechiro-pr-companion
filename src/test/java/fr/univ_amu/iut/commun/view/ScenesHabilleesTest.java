package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Toute fenêtre de l'application passe-t-elle par [Habillage] ? (#3374)
///
/// ## Le défaut que ce garde empêche de revenir
///
/// `base.css` était déclarée à la main dans deux FXML sur des dizaines. La fenêtre principale la
/// portait ; les **dix autres** - modales de point, de site, de rattachement, de connexion, de
/// qualification, dialogues de progression - naissaient d'un `new Scene(vue)` et rendaient avec la
/// police par défaut de JavaFX, différente de celle de la fenêtre qui les portait et différente d'une
/// machine à l'autre.
///
/// Le correctif est un point de passage unique. Sans garde, il tient jusqu'à la onzième fenêtre :
/// `new Scene(...)` reste la façon évidente d'en écrire une, et rien ne signale l'oubli - la fenêtre
/// s'ouvre, simplement pas habillée.
///
/// ## Ce que ce garde vérifie vraiment
///
/// Il lit les **sources**, pas le comportement : un test d'intégration devrait ouvrir chacune des
/// fenêtres, ce que la moitié refuse en headless. La contrepartie est assumée, et c'est le même choix
/// que les inventaires de `cli-surface.bats` : mieux vaut un inventaire vérifiable qu'une couverture
/// qu'on n'écrira pas.
class ScenesHabilleesTest {

    /// Le seul endroit qui fabrique une scène de fenêtre.
    private static final String FABRIQUE = "commun/view/Habillage.java";

    /// Les façons de demander une dimension à un nœud. Une liste, parce que le défaut ne tient pas à un
    /// nom : il tient au fait de **mesurer**.
    private static final List<String> MESURES = List.of(
            "getLayoutBounds(",
            "getBoundsInParent(",
            "getBoundsInLocal(",
            ".getWidth()",
            ".getHeight()",
            "prefWidth(",
            "prefHeight(");

    @Test
    @DisplayName("#3374 : aucune fenêtre de l'application ne se construit hors de Habillage")
    void toutes_les_scenes_passent_par_habillage() throws IOException {
        List<String> coupables;
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            coupables = sources.filter(chemin -> chemin.toString().endsWith(".java"))
                    .filter(chemin -> !normaliser(chemin).endsWith(FABRIQUE))
                    .filter(ScenesHabilleesTest::construitUneScene)
                    // Les 35 outils de capture construisent légitimement leur scène : ils l'habillent
                    // ensuite, directement ou en la confiant à `ApercuFx`. La dispense est donc
                    // CONDITIONNELLE - un outil qui garderait sa scène pour lui retombe dans la liste,
                    // et c'est précisément le cas qui produirait un aperçu ne montrant pas le produit.
                    // Trois l'ont été à l'écriture de ce garde : ils posaient palette+base à la main,
                    // sans jamais installer la police, donc `base.css` y demandait une famille non
                    // enregistrée.
                    .filter(chemin -> !(normaliser(chemin).contains("/outils/") && habille(chemin)))
                    .map(ScenesHabilleesTest::normaliser)
                    .sorted()
                    .toList();
        }

        assertThat(coupables)
                .as("« new Scene(...) » hors de Habillage : cette fenêtre s'ouvrira sans la police du "
                        + "produit ni ses feuilles de socle, donc avec un rendu dépendant de la machine, "
                        + "et rien ne le signalera. Utiliser Habillage.scene(racine) (#3374)")
                .isEmpty();
    }

    /// ⚠️ **Le pendant côté tests**, et il ne vise PAS les mêmes fichiers que ci-dessus.
    ///
    /// `new Scene(` apparaît dans une centaine de tests, et l'immense majorité a raison de l'écrire :
    /// ils vérifient un **comportement** - un clic, un intitulé, une navigation - et se moquent de la
    /// police. Interdire la construction directe partout serait une règle fausse, donc une règle qu'on
    /// désactive.
    ///
    /// Ceux qui **mesurent** sont autre chose : leur verdict dépend de la police effectivement rendue.
    /// Sans habillage, ils mesurent celle de la **machine hôte** - qui n'est pas celle du produit, et
    /// qui n'est pas la même partout.
    ///
    /// ## Ce que la mesure a montré, et qui n'est pas ce que je croyais (#3773)
    ///
    /// `CartesAccueilTest` a rendu **vert à 8 h 14 et rouge à 15 h 34** sur le **même commit** et la
    /// **même image** `macos-26-arm64`. Le diagnostic naturel - « macOS rend autrement » - est faux :
    /// [Typographie#installer] garde un `static boolean`, donc l'enregistrement est **global au JVM et
    /// fait une seule fois**, et un test qui ne l'appelle pas voit la police embarquée **si un voisin
    /// l'a installée avant lui**.
    ///
    /// ⚠️ Et **sur un poste de développement Linux**, rien de tout cela ne se voit : `Noto Sans` y est
    /// une police **système** (219 entrées sous `/usr/share/fonts/truetype/noto/`), donc trouvée
    /// installée ou non. Une mesure locale de ce défaut est aveugle par construction.
    ///
    /// ⚠️ **Sur le runner, c'est différent, et j'avais écrit trop large.** L'ADR 3361 l'a mesuré avant
    /// moi : l'alias `sans-serif` se résout « **Noto Sans sur un poste, une police plus large sur le
    /// runner** ». Ce que la CI Ubuntu voit exactement n'a pas été remesuré ici (#3826, passe 0).
    ///
    /// C'est bien pourquoi ce garde vaut mieux qu'une exécution : **il ne dépend d'aucune machine**.
    @Test
    @DisplayName("#3773 : un test qui MESURE une géométrie monte une scène habillée")
    void les_tests_qui_mesurent_montent_une_scene_habillee() throws IOException {
        List<String> coupables;
        try (Stream<Path> sources = Files.walk(Path.of("src/test/java"))) {
            coupables = sources.filter(chemin -> chemin.toString().endsWith(".java"))
                    .filter(ScenesHabilleesTest::construitUneScene)
                    .filter(ScenesHabilleesTest::mesureUneGeometrie)
                    .filter(chemin -> !habille(chemin))
                    .map(ScenesHabilleesTest::normaliser)
                    .sorted()
                    .toList();
        }

        assertThat(coupables)
                .as("ces tests mesurent une géométrie sur une scène qu'ils montent eux-mêmes : ils "
                        + "mesurent donc la police de la MACHINE, pas celle du produit. Passer par "
                        + "Habillage.scene(...), qui installe la police et pose les feuilles (#3773)")
                .isEmpty();
    }

    /// ⚠️ Contrôle de non-vacuité **fabriqué**, et il le faut : une fois les dix corrigés, plus aucun
    /// fichier du dépôt ne correspond au motif. Le test ci-dessus certifierait alors une absence qu'il
    /// ne saurait plus constater - exactement le défaut qu'il est censé empêcher, appliqué à lui-même.
    @Test
    @DisplayName("#3773 : le garde attrape bien un test fabriqué qui mesure sans habiller")
    void le_garde_attrape_un_coupable_fabrique(@TempDir Path bac) throws IOException {
        Path coupable = Files.writeString(
                bac.resolve("FauxTest.java"),
                "class FauxTest { void t() { var s = new Scene(new VBox()); "
                        + "s.getRoot().getLayoutBounds().getHeight(); } }");
        Path innocent = Files.writeString(
                bac.resolve("FauxSansMesureTest.java"),
                "class FauxSansMesureTest { void t() { var s = new Scene(new VBox()); s.getRoot(); } }");
        Path habille = Files.writeString(
                bac.resolve("FauxHabilleTest.java"),
                "class FauxHabilleTest { void t() { var s = Habillage.scene(new VBox()); "
                        + "s.getRoot().getLayoutBounds().getHeight(); } }");

        assertThat(construitUneScene(coupable) && mesureUneGeometrie(coupable) && !habille(coupable))
                .as("un test qui monte sa scène ET mesure doit être vu")
                .isTrue();
        assertThat(mesureUneGeometrie(innocent))
                .as("un test qui monte sa scène sans mesurer n'est pas concerné")
                .isFalse();
        assertThat(habille(habille))
                .as("un test qui passe par Habillage est en règle")
                .isTrue();
    }

    @Test
    @DisplayName("#3374 : le garde saurait voir un « new Scene » - il en trouve un dans Habillage lui-même")
    void le_garde_detecte_bien_ce_qu_il_cherche() {
        // Sans cette vérification, le test ci-dessus resterait vert si le motif cherché ne correspondait
        // plus à rien (une reformulation, un chemin de sources déplacé) : il certifierait une absence
        // qu'il ne sait plus constater.
        assertThat(construitUneScene(Path.of("src/main/java/fr/univ_amu/iut/" + FABRIQUE)))
                .as("Habillage construit bien des scènes : si le garde ne l'y voit pas, il ne voit rien")
                .isTrue();
    }

    private static boolean construitUneScene(Path source) {
        return lire(source).contains("new Scene(");
    }

    /// Le fichier demande-t-il une dimension à un nœud ?
    private static boolean mesureUneGeometrie(Path source) {
        String code = lire(source);
        return MESURES.stream().anyMatch(code::contains);
    }

    /// L'outil habille-t-il sa scène - lui-même, ou en la confiant à `ApercuFx` qui le fait ?
    private static boolean habille(Path source) {
        String code = lire(source);
        return code.contains("Habillage.") || code.contains("ApercuFx.");
    }

    private static String normaliser(Path source) {
        return source.toString()
                .replace('\\', '/')
                .replace("src/main/java/fr/univ_amu/iut/", "")
                .replace("src/test/java/fr/univ_amu/iut/", "");
    }

    private static String lire(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException echec) {
            throw new java.io.UncheckedIOException(echec);
        }
    }
}

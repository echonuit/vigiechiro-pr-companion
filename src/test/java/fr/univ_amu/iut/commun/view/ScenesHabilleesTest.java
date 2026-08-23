package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /// Les lectures qui n'appartiennent qu'au graphe de scène. Volontairement plus étroite que
    /// [#MESURES] : `getWidth()` et `getHeight()` existent aussi sur une `BufferedImage`, et un garde
    /// qui les inclurait se tromperait de cible dans le paquet `film`.
    private static final List<String> LECTURES_DE_SCENE =
            List.of("localToScene(", "localToScreen(", "getBoundsInLocal(", "getBoundsInParent(", "getLayoutBounds(");

    /// Les façons de faire exécuter quelque chose par le fil de l'application.
    private static final List<String> ROUTAGES = List.of("asyncFx(", ".interact(", "runLater(");

    /// Ce que ce garde attrape, et ce qu'il n'attrape pas.
    ///
    /// `recette.CadreVisible` lisait les bornes de ses nœuds depuis le fil du test. Onze appels
    /// partaient de `main` alors que JavaFX n'autorise que le fil de l'application, et le symptôme
    /// n'était pas une exception nette mais deux instabilités qui ont coûté deux enquêtes (#4200,
    /// #4187). `e2e.AttenteAvantClic` faisait bien depuis toujours : sa lecture vit dans une méthode
    /// appelée uniquement depuis `robot.interact(...)`.
    ///
    /// ⚠️ **La propriété est inter-procédurale, et ce garde ne l'est pas.** `AttenteAvantClic` route
    /// dans une méthode et lit dans une autre : aucune lecture des sources ne peut relier les deux sans
    /// devenir un analyseur de flot. Ce garde vérifie donc une propriété plus FAIBLE mais vraie : un
    /// helper qui lit le graphe de scène **route quelque part**. Il attrape le cas qui s'est produit -
    /// un helper qui ne route nulle part - et il ne prouve pas que chaque lecture est routée.
    ///
    /// Une classe qui **est** un `AnimationTimer` en est dispensée : son `handle` est appelé par la
    /// pulsation de JavaFX, donc elle est déjà sur le bon fil par construction.
    @Test
    @DisplayName("#4246 : un helper partagé qui lit le graphe de scène passe par le fil FX")
    void un_helper_qui_lit_la_scene_passe_par_le_fil_fx() throws IOException {
        List<String> coupables;
        List<Path> inspectes;
        try (Stream<Path> sources = Files.walk(Path.of("src/test/java"))) {
            inspectes = sources.filter(ScenesHabilleesTest::estUnHelperPartage).toList();
        }
        coupables = inspectes.stream()
                .filter(chemin -> litLeGrapheDeScene(lire(chemin)))
                .flatMap(chemin ->
                        lecturesNonRoutees(lire(chemin)).stream().map(lecture -> normaliser(chemin) + " : " + lecture))
                .sorted()
                .toList();

        // ⚠️ Un garde vert qui n'a rien lu est le faux vert le plus difficile à voir, et celui-ci l'a
        // été : son premier jet filtrait sur un chemin déjà amputé de son préfixe, n'inspectait AUCUN
        // fichier, et restait vert quand on retirait le routage de `CadreVisible`.
        assertThat(inspectes)
                .as("aucun helper partagé inspecté : le garde ne garde plus rien")
                .hasSizeGreaterThan(5);

        assertThat(coupables).as("""
                        Ces helpers partagés lisent le graphe de scène sans jamais passer par le fil de \
                        l'application.

                        JavaFX n'autorise ces lectures que sur son fil, et le symptôme n'est pas une \
                        exception franche : c'est une instabilité qui rougit ailleurs, une fois sur cinq, \
                        très loin de la cause. `CadreVisible` a coûté deux enquêtes à ce titre (#4200, \
                        #4187).

                        Un helper est appelé sans que son appelant sache sur quel fil il se trouve : \
                        c'est à lui de router, par `WaitForAsyncUtils.asyncFx(...)`, `robot.interact(...)` \
                        ou `Platform.runLater(...)` (#4246).""").isEmpty();
    }

    @Test
    @DisplayName("#4246 : le garde suit l'indirection, et ne se laisse pas rassurer par un routage voisin")
    void le_garde_suit_l_indirection() {
        String nu = "class Aide { double bas(Node n) { return n.localToScene(n.getBoundsInLocal()).getMaxY(); } }";
        assertThat(lecturesNonRoutees(nu))
                .as("une lecture sans aucun routage doit être vue")
                .isNotEmpty();

        String direct = "class Aide { double bas(Node n) { return asyncFx(() -> "
                + "n.localToScene(n.getBoundsInLocal()).getMaxY()).get(); } }";
        assertThat(lecturesNonRoutees(direct))
                .as("une lecture posée DANS le routage est en règle")
                .isEmpty();

        // Le cas d'`AttenteAvantClic` : on route ici, on lit là-bas.
        String parIndirection = "class Aide { void voir(FxRobot r) { r.interact(() -> decrire(r)); } "
                + "private String decrire(FxRobot r) { return \"\" + r.n().localToScene(r.n().getBoundsInLocal()); } }";
        assertThat(lecturesNonRoutees(parIndirection))
                .as("une lecture dans une méthode appelée DEPUIS le routage est en règle")
                .isEmpty();

        // Le cas qui a fait tomber la première version de ce garde : un routage VOISIN, qui ne
        // concerne pas la lecture, suffisait à rassurer une règle posée au niveau du fichier.
        String routageVoisin = "class Aide { void faire(FxRobot r) { r.interact(() -> r.pan().defiler()); } "
                + "double bas(Node n) { return n.localToScene(n.getBoundsInLocal()).getMaxY(); } }";
        assertThat(lecturesNonRoutees(routageVoisin))
                .as("un routage qui ne concerne pas la lecture ne la met pas en règle")
                .isNotEmpty();
    }

    /// Un fichier de `recette/` ou `e2e/` qui n'est pas lui-même un test : du code appelé sans que
    /// l'appelant sache sur quel fil il se trouve.
    ///
    /// ⚠️ Un `AnimationTimer` est dispensé : sa méthode `handle` est appelée par la pulsation de
    /// JavaFX, donc sur le bon fil par construction.
    /// ⚠️ Sur le chemin BRUT, pas sur [#normaliser]. Celui-ci retire le préfixe
    /// `src/test/java/fr/univ_amu/iut/`, si bien que `CadreVisible` s'y écrit `recette/CadreVisible.java`
    /// et qu'un test sur `"/recette/"` y est faux pour TOUS les fichiers. Écrit ainsi au premier jet, ce
    /// garde n'inspectait rien et rendait vert : c'est exactement le faux vert que son compteur de
    /// non-vacuité interdit désormais.
    private static boolean estUnHelperPartage(Path source) {
        String chemin = source.toString().replace('\\', '/');
        if (!chemin.endsWith(".java") || chemin.endsWith("Test.java")) {
            return false;
        }
        if (!chemin.contains("/recette/") && !chemin.contains("/e2e/")) {
            return false;
        }
        return !lire(source).contains("extends AnimationTimer");
    }

    private static boolean litLeGrapheDeScene(String code) {
        return LECTURES_DE_SCENE.stream().anyMatch(code::contains);
    }

    /// Les lectures de ce fichier qui n'ont AUCUN chemin vers le fil de l'application.
    ///
    /// L'analyse fait **un saut**, et il le faut : `AttenteAvantClic` route dans une méthode et lit dans
    /// une autre. Une règle « le fichier route quelque part » ne suffit pas non plus - mesuré, elle
    /// laisse passer un `CadreVisible` dont le routage des lectures est retiré mais qui garde un
    /// `robot.interact` pour son défilement.
    ///
    /// Trois choses, donc :
    ///
    /// 1. les **jetons de routage** sont les trois idiomes JavaFX, plus toute méthode de ce fichier dont
    ///    le corps en contient un - c'est ainsi que le `surLeFilFx` de `CadreVisible` en devient un ;
    /// 2. une **région routée** est ce que parenthèse un appel à l'un de ces jetons ;
    /// 3. une lecture est en règle si elle est dans une région, ou si le nom de la méthode qui la
    ///    contient est cité dans une région.
    static List<String> lecturesNonRoutees(String source) {
        String code = COMMENTAIRE.matcher(source).replaceAll(" ");
        List<String> jetons = new ArrayList<>(ROUTAGES);
        for (String methode : methodesQuiRoutent(code, ROUTAGES)) {
            jetons.add(methode + "(");
        }

        boolean[] routee = new boolean[code.length()];
        StringBuilder citeesDansLeRoutage = new StringBuilder();
        for (String jeton : jetons) {
            int depuis = 0;
            int trouve;
            while ((trouve = code.indexOf(jeton, depuis)) >= 0) {
                int fin = finDeLAppel(code, trouve + jeton.length() - 1);
                for (int i = trouve; i < fin; i++) {
                    routee[i] = true;
                }
                citeesDansLeRoutage.append(code, trouve, fin).append('\n');
                depuis = trouve + jeton.length();
            }
        }

        String citees = citeesDansLeRoutage.toString();
        List<String> fautives = new ArrayList<>();
        for (String lecture : LECTURES_DE_SCENE) {
            int depuis = 0;
            int trouve;
            while ((trouve = code.indexOf(lecture, depuis)) >= 0) {
                depuis = trouve + lecture.length();
                if (routee[trouve]) {
                    continue;
                }
                String englobante = methodeEnglobante(code, trouve);
                if (englobante.isEmpty() || !citees.contains(englobante + "(")) {
                    fautives.add(lecture + " dans " + (englobante.isEmpty() ? "(hors méthode)" : englobante));
                }
            }
        }
        return fautives;
    }

    /// Les méthodes de ce fichier dont le corps contient un idiome de routage.
    private static List<String> methodesQuiRoutent(String code, List<String> idiomes) {
        List<String> routantes = new ArrayList<>();
        Matcher declaration = DECLARATION.matcher(code);
        while (declaration.find()) {
            int ouvrante = code.indexOf('{', declaration.end() - 1);
            if (ouvrante < 0) {
                continue;
            }
            int fin = finDuBloc(code, ouvrante);
            String corps = code.substring(ouvrante, fin);
            if (idiomes.stream().anyMatch(corps::contains)) {
                routantes.add(declaration.group(1));
            }
        }
        return routantes;
    }

    /// Le nom de la méthode qui contient la position donnée : la dernière déclaration qui la précède.
    private static String methodeEnglobante(String code, int position) {
        String nom = "";
        Matcher declaration = DECLARATION.matcher(code);
        while (declaration.find() && declaration.start() < position) {
            nom = declaration.group(1);
        }
        return nom;
    }

    private static int finDeLAppel(String code, int ouvrante) {
        int profondeur = 0;
        for (int i = ouvrante; i < code.length(); i++) {
            if (code.charAt(i) == '(') {
                profondeur++;
            } else if (code.charAt(i) == ')' && --profondeur == 0) {
                return i + 1;
            }
        }
        return code.length();
    }

    private static int finDuBloc(String code, int ouvrante) {
        int profondeur = 0;
        for (int i = ouvrante; i < code.length(); i++) {
            if (code.charAt(i) == '{') {
                profondeur++;
            } else if (code.charAt(i) == '}' && --profondeur == 0) {
                return i + 1;
            }
        }
        return code.length();
    }

    /// Une déclaration de méthode : un nom, des paramètres, une accolade. Approximatif et suffisant -
    /// le garde ne compile pas le Java, il le lit.
    private static final Pattern DECLARATION =
            Pattern.compile("\\b(?:static\\s+|final\\s+|private\\s+|public\\s+|<[^>]+>\\s*)*[\\w.<>\\[\\]]+\\s+"
                    + "(\\w+)\\s*\\([^;{]*\\)\\s*(?:throws [\\w., ]+)?\\{");

    private static final Pattern COMMENTAIRE = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

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

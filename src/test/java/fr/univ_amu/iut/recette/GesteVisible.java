package fr.univ_amu.iut.recette;

import fr.univ_amu.iut.commun.view.InfobulleDeBlocage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import org.testfx.api.FxRobot;
import org.testfx.util.NodeQueryUtils;
import org.testfx.util.WaitForAsyncUtils;

/// Faire un geste **de façon qu'on le voie faire** (#4177, #4181).
///
/// ## Pourquoi ce geste est partagé
///
/// Un clip qui montre le menu s'ouvrir puis l'écran changer laisse le spectateur deviner **quelle**
/// entrée a été prise. Retour de la revue, mot pour mot : « on ne voit pas la souris aller sur le
/// menuitem réglage ».
///
/// La première correction avait remplacé `MenuItem.fire()` par un vrai clic, ce qui était nécessaire et
/// **pas suffisant** : `clickOn(libellé)` téléporte le pointeur et clique dans la foulée, et le menu se
/// referme aussitôt. Mesuré en extrayant les images autour du clic - à 56 % comme à 64 % du clip, le
/// curseur était encore sur le bouton du menu, et l'instant où il repose sur l'entrée n'existait sur
/// **aucune** trame.
///
/// Ce geste n'est pas neuf : `ScenarioPerceptifConnexionTest` l'avait inventé pour lui seul, après
/// un retour de revue - « on a l'impression que la modale apparaît par magie ». Il vit ici parce que
/// deux implémentations d'une même doctrine finissent par diverger, et que la seconde n'aurait pas
/// hérité de ce qu'a coûté la première.
///
/// D'où les trois temps : le menu s'ouvre, le pointeur **va** sur l'entrée et **s'y arrête**, puis il
/// clique. Le temps d'arrêt ne coûte qu'à une séance filmée.
public final class GesteVisible {

    /// Le temps laissé à la mise en page pour établir ses bornes. Généreux : ce délai n'est atteint
    /// que si la cible ne vient JAMAIS, cas où l'on veut un message plutôt qu'un silence.
    private static final int SECONDES_CADRE = 10;

    /// De quoi laisser passer une mise en page, jamais de quoi masquer un blocage.
    private static final long SELECTION_MS = 5_000;

    private GesteVisible() {}

    /// Fait défiler le `ScrollPane` du chrome jusqu'à ce que `selecteur` soit **dans le cadre**.
    ///
    /// `Node::isVisible` répond `true` pour un nœud sous le bord : c'est une propriété du nœud, pas de
    /// ce qu'on voit. Seul TestFX distingue les deux, et il le dit par un refus de clic - « returned 1
    /// nodes, but no nodes were visible ».
    ///
    /// Un geste hors du cadre n'est pas seulement incliquable : il serait **absent du clip**. C'est
    /// pourquoi cette aide vit ici et non chez un scénario. Elle y était, en privé, et une seconde
    /// copie aurait divergé de la première.
    public static void amenerDansLeCadre(FxRobot robot, String selecteur) {
        try {
            WaitForAsyncUtils.waitFor(SECONDES_CADRE, TimeUnit.SECONDS, () -> unePasse(robot, selecteur));
        } catch (TimeoutException jamais) {
            throw new IllegalStateException("« " + selecteur + " » n'est jamais venu dans le cadre en "
                    + SECONDES_CADRE + " s. Rendre la main sans l'avoir amené reporterait l'échec sur le"
                    + " clic suivant, qui l'annoncerait comme une absence de nœud.");
        }
    }

    /// Cale la page de `selecteur` sur son **bas**, et vérifie que `selecteur` y est dans le cadre.
    ///
    /// Dernier geste d'un clip dont le verdict est le dernier élément de sa page : il fixe la dernière
    /// image, que [#amenerDansLeCadre] ne fixe pas. Celui-ci place la cible par un quotient calculé sur
    /// la hauteur du contenu à cet instant, et JavaFX garde ensuite le décalage en pixels : une carte
    /// qui grandit après avoir été amenée laisse la page en deçà de son bas. Mesuré sur `S4-47` : 20 %
    /// d'écart entre deux tournages du même commit (#5870).
    ///
    /// Elle s'appelle **une fois le verdict affiché**, et refuse une cible qui n'est pas au bas :
    /// employée ailleurs, elle mettrait le verdict hors du cadre en ayant l'air de l'y amener.
    public static void allerAuBasDeLaPage(FxRobot robot, String selecteur) {
        // Par [Attente], et non par un `waitFor` en propre : une attente qui expire dit ce qu'elle
        // attendait (ADR 4974). La passe ouvre elle-même ses `interact`, d'où `que` et non `queSurLeFil`.
        Attente.que(
                () -> unePasseVersLeBas(robot, selecteur),
                "« " + selecteur + " » dans le cadre quand sa page est à son bas. Ce geste ne vaut que"
                        + " pour le dernier élément d'une page : ailleurs, amenerDansLeCadre",
                SECONDES_CADRE * 1000L);
    }

    /// Une passe : tous les panneaux dont la cible descend vont à leur maximum, puis le verdict.
    ///
    /// Rejouée comme [#unePasse], et pour la même raison : sur un écran qui vient de changer, le
    /// maximum posé peut ne pas porter du premier coup.
    private static boolean unePasseVersLeBas(FxRobot robot, String selecteur) {
        AtomicBoolean tenu = new AtomicBoolean();
        robot.interact(() -> {
            for (ScrollPane panneau : panneauxDont(robot.lookup(selecteur).query())) {
                panneau.setVvalue(panneau.getVmax());
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> {
            boolean auBas = panneauxDont(robot.lookup(selecteur).query()).stream()
                    .allMatch(panneau -> panneau.getVvalue() >= panneau.getVmax());
            tenu.set(auBas && estDansLeCadre(robot, selecteur));
        });
        return tenu.get();
    }

    /// Une passe de calcul, puis le verdict : la cible est-elle atteignable ?
    ///
    /// Le calcul se refait à chaque tour parce que ses **bornes** peuvent ne pas encore être établies -
    /// un écran qui vient de paraître rend une largeur nulle, et le quotient tombe alors à mi-course.
    /// Une passe unique sur des bornes fausses ne se rattrape pas toute seule.
    ///
    /// Le produit connaissait déjà ce mode de défaillance : [fr.univ_amu.iut.commun.view.DefilementChrome]
    /// diffère son calcul d'un tour de boucle, parce que « révéler tout de suite reviendrait à viser un
    /// nœud de hauteur nulle ». Ce geste-ci, du côté des bancs, ne l'avait jamais hérité (#4723).
    private static boolean unePasse(FxRobot robot, String selecteur) {
        AtomicBoolean atteignable = new AtomicBoolean();
        robot.interact(() -> {
            Node cible = robot.lookup(selecteur).query();
            for (ScrollPane panneau : panneauxDont(cible)) {
                amener(panneau, cible);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> atteignable.set(estDansLeCadre(robot, selecteur)));
        return atteignable.get();
    }

    /// Les panneaux de défilement dont `cible` **descend**, du plus proche au plus lointain.
    ///
    /// Le geste interrogeait le graphe entier et prenait le premier `.scroll-pane` rendu. L'écran de
    /// vérification en porte trois - celui du chrome, celui que sa mise en page pose, celui d'un champ
    /// de texte - et le `lookup` traverse même les **autres fenêtres** (#4778). Remonter les parents
    /// répond sans rien supposer de cet ordre.
    private static List<ScrollPane> panneauxDont(Node cible) {
        List<ScrollPane> panneaux = new ArrayList<>();
        for (Node noeud = cible.getParent(); noeud != null; noeud = noeud.getParent()) {
            if (noeud instanceof ScrollPane panneau) {
                panneaux.add(panneau);
            }
        }
        return panneaux;
    }

    /// Amène `cible` en haut du champ de `panneau`.
    ///
    /// Chacun des panneaux emboîtés se règle, du plus proche au plus lointain : le plus proche seul ne
    /// suffit pas, et le plus lointain seul laisse la cible **rognée** par celui du dedans. Mesuré sur
    /// les 25 combinaisons d'un banc à deux panneaux : une seule laisse le clic atteindre la cible, et
    /// cinq autres le font partir dans le vide en paraissant bonnes.
    /// **Le quotient est borné, sans garde** : il sort de `[0, 1]` onze fois sur quatre-vingt-seize
    /// appels réels, mais JavaFX normalise la valeur stockée. Hygiène, pas remède (#4795).
    private static void amener(ScrollPane panneau, Node cible) {
        Node contenu = panneau.getContent();
        if (contenu == null) {
            return;
        }
        double hauteurContenu = contenu.getBoundsInLocal().getHeight();
        double hauteurVue = panneau.getViewportBounds().getHeight();
        double y = cible.localToScene(cible.getBoundsInLocal()).getMinY();
        double yContenu = contenu.localToScene(contenu.getBoundsInLocal()).getMinY();
        panneau.setVvalue(Math.clamp((y - yContenu) / Math.max(1, hauteurContenu - hauteurVue), 0, 1));
    }

    /// La cible est-elle dans le cadre, **au sens de TestFX** ?
    ///
    /// Le prédicat même dont `moveTo` se sert : une seconde façon de lire aurait divergé de la
    /// première, et c'est ce refus-là que le geste doit prévenir.
    private static boolean estDansLeCadre(FxRobot robot, String selecteur) {
        return robot.lookup(selecteur)
                .match(NodeQueryUtils.isVisible())
                .tryQuery()
                .isPresent();
    }

    /// Amène le pointeur sur `cible`, l'y laisse voir, puis clique.
    ///
    /// `clickOn` seul **téléporte** le pointeur et clique dans la foulée : l'arrivée et l'appui
    /// tombent sur la même trame, et le geste décisif d'un clip n'existe sur aucune image. Constaté sur
    /// « Récupérer ce carré » (#4181) comme sur les entrées de menu (#4177) - c'est le même défaut, et
    /// la doctrine n'est pas « le menu » mais « on doit voir le geste ».
    public static void cliquer(FxRobot robot, String cible) {
        robot.moveTo(pointSurLeFil(robot, cible));
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.entreDeuxGestes(robot);

        // Le point se RECALCULE : entre l'arrivée et l'appui, la mise en page a pu bouger, et c'est
        // ce que la seconde résolution de `clickOn(cible)` faisait avant #5707.
        robot.clickOn(pointSurLeFil(robot, cible));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// Clique la cible que `reconnue` désigne, **résolue au moment du clic** (ADR 5068, #5734).
    ///
    /// Tenir une référence entre la résolution et le clic suppose que le graphe ne bouge pas. Il
    /// bouge : `MesSitesController` reconstruit sa liste de cartes à chaque changement, la carte
    /// tenue se détache, et `getScene()` rend `null`. Onze occurrences en trente jours.
    ///
    /// `robot.point(Predicate)` résout **et** situe : la référence ne traverse rien, et la lecture
    /// reste sur le fil JavaFX. Les ADR 5068 et 5707 sont satisfaites par le même geste, là où le
    /// remède de 5068 seul - passer un sélecteur - laissait la lecture hors du fil.
    ///
    /// @param reconnue ce qui distingue la cible, évalué SUR LE FIL
    /// @param ceQueOnCherche dit à la première personne du banc, et repris dans l'échec
    public static void cliquerLaCible(FxRobot robot, Predicate<Node> reconnue, String ceQueOnCherche) {
        robot.moveTo(pointSurLeFil(robot, reconnue, ceQueOnCherche));
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.entreDeuxGestes(robot);

        // La cible se RERESOLUT : c'est tout l'objet de ce geste. Entre l'arrivée du pointeur et
        // l'appui, la liste a pu se reconstruire une seconde fois.
        robot.clickOn(pointSurLeFil(robot, reconnue, ceQueOnCherche));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// Remplace le contenu du champ désigné par `texte`, et relit pour s'assurer de l'avoir fait.
    ///
    /// La sélection se pose sur le fil JavaFX, jamais au clavier : « tout sélectionner » est `⌘A` sur
    /// macOS et `^A` ailleurs, et `SHORTCUT_DOWN` ne résout pas la différence puisque `TypeRobotImpl`
    /// de TestFX 4.0.18 enfonce littéralement `KeyCode.SHORTCUT`, une touche virtuelle. La saisie,
    /// elle, reste un geste du robot, donc visible dans un clip.
    ///
    /// La relecture finale est là parce que le défaut d'origine était silencieux : le récit est dans
    /// [GesteVisibleRemplacementTest] (#5436).
    public static void remplacerLeTexte(FxRobot robot, String selecteur, String texte) {
        cliquer(robot, selecteur);
        TextInputControl champ = robot.lookup(selecteur).queryAs(TextInputControl.class);

        Attente.surLeFil(champ::selectAll, "sélectionner le contenu de « " + selecteur + " »", SELECTION_MS);
        robot.write(texte);
        WaitForAsyncUtils.waitForFxEvents();

        Callable<String> relecture = champ::getText;
        String lu = Attente.surLeFil(relecture, "relire « " + selecteur + " »", SELECTION_MS);
        if (!texte.equals(lu)) {
            throw new IllegalStateException("« " + selecteur + " » devait contenir « " + texte
                    + " » et contient « " + lu + " ». Rendre la main ici reporterait l'échec sur"
                    + " l'assertion suivante, qui l'annoncerait comme un défaut du dialogue.");
        }
    }

    /// Écrit `texte` à la suite du contenu du champ, caret posé à la fin, et relit pour le vérifier.
    ///
    /// **Pas un remplaçant exact** de `clickOn("#x").write("t")`, qui tape au pixel visé, donc au
    /// milieu d'un texte : « à la fin » par [TextInputControl#end] est la seule sémantique qu'une
    /// aide puisse promettre. Pour écraser le contenu, c'est [#remplacerLeTexte].
    ///
    /// La relecture confronte `avant + texte` et non `texte`, pour que l'échec tombe ici plutôt que
    /// sur l'assertion suivante. Les deux champs pré-remplis qui l'ont fait écrire sont dans #5869.
    public static void ecrireALaSuite(FxRobot robot, String selecteur, String texte) {
        cliquer(robot, selecteur);
        TextInputControl champ = robot.lookup(selecteur).queryAs(TextInputControl.class);

        Callable<String> lecture = champ::getText;
        String avant = Attente.surLeFil(lecture, "lire « " + selecteur + " » avant d'écrire", SELECTION_MS);
        Attente.surLeFil(champ::end, "poser le caret à la fin de « " + selecteur + " »", SELECTION_MS);
        robot.write(texte);
        WaitForAsyncUtils.waitForFxEvents();

        String lu = Attente.surLeFil(lecture, "relire « " + selecteur + " »", SELECTION_MS);
        if (!(avant + texte).equals(lu)) {
            throw new IllegalStateException("« " + selecteur + " » contenait « " + avant
                    + " » et devait contenir « " + avant + texte + " » après la saisie ; il contient"
                    + " « " + lu + " ». Rendre la main ici reporterait l'échec sur l'assertion"
                    + " suivante, qui l'annoncerait comme un défaut du dialogue.");
        }
    }

    /// Amène le pointeur sur `cible` et y fait paraître son infobulle, pour qu'un clip la montre.
    ///
    /// **Deux gestes, pas un** : `moveTo` met le pointeur à l'image et rien de plus, car sur un symbole
    /// de dix pixels il laisse `isHover()` **faux** (#5205). L'entrée de souris se poste donc aussi.
    ///
    /// **Attention** : le clip montre alors un survol que le banc ne sait pas obtenir en pointant. Ça
    /// donne à voir ce qu'un utilisateur voit, jamais qu'il y arrive : la case `S2-79` garde la
    /// question.
    public static void survoler(FxRobot robot, Node cible) throws TimeoutException {
        robot.moveTo(pointSurLeFil(robot, cible));
        WaitForAsyncUtils.waitForFxEvents();
        InfobulleDeBlocage.montrerParEntreeDeSouris(cible, robot);
        Respiration.leTempsDeLire(robot);
    }

    /// Même chose sur un noeud déjà en main, quand le scénario le tient plutôt que son sélecteur.
    public static void cliquer(FxRobot robot, Node cible) {
        robot.moveTo(pointSurLeFil(robot, cible));
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.entreDeuxGestes(robot);

        robot.clickOn(pointSurLeFil(robot, cible));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// Choisit `libelle` dans un menu **déjà en main**, quand plusieurs écrans portent le même
    /// identifiant.
    ///
    /// Cinq FXML du dépôt déclarent `fx:id="menuOutils"` : le chrome, l'analyse, le lot, la sélection
    /// d'écoute et le détail d'un site. Un `lookup` par identifiant ouvre donc le premier venu, et le
    /// scénario attend une entrée qui n'y est pas (#4728). C'est le défaut qu'`ApercuFx.exigerParLibelle`
    /// a corrigé côté aperçus.
    ///
    /// @param robot le robot du banc
    /// @param menu le menu que le scénario tient
    /// @param libelle l'entrée à choisir
    /// @throws TimeoutException si l'entrée ne paraît pas
    public static void choisir(FxRobot robot, Node menu, String libelle) {
        cliquer(robot, menu);
        WaitForAsyncUtils.waitForFxEvents();
        Attente.queSurLeFil(
                () -> robot.lookup(libelle).tryQuery().isPresent(),
                "l'entrée « " + libelle + " » paraît dans le menu ouvert",
                5_000L);
        Respiration.leTempsDeLire(robot);
        cliquer(robot, libelle);
    }

    /// Ouvre `idDuMenu`, amène le pointeur sur l'entrée `libelle`, l'y laisse voir, puis clique.
    ///
    /// Un menu qui ne s'ouvre pas rendrait un clip immobile que personne ne signalerait : l'attente
    /// **dit** donc ce qu'elle guettait (#4845).
    public static void choisir(FxRobot robot, String idDuMenu, String libelle) {
        robot.clickOn(pointSurLeFil(robot, idDuMenu));
        WaitForAsyncUtils.waitForFxEvents();
        Attente.queSurLeFil(
                () -> robot.lookup(libelle).tryQuery().isPresent(),
                "l'entrée « " + libelle + " » paraît dans le menu ouvert",
                5_000L);
        Respiration.leTempsDeLire(robot);

        // Le pointeur VA sur l'entrée, et s'y arrête, AVANT de cliquer. Sans cet arrêt, le clic et la
        // fermeture du menu tombent sur la même trame : on voit le menu, puis l'écran d'après, et jamais
        // le choix.
        cliquer(robot, libelle);
    }

    /// Le point d'écran où le pointeur doit aller, **situé sur le fil JavaFX** (ADR 5707).
    ///
    /// `moveTo("#id")` ne fait pas que bouger le pointeur : il **situe** sa cible, sur le fil
    /// appelant, en lisant les bornes de chaque candidat. Cela itère les éléments de tout `Path` du
    /// sous-arbre - le caret d'un champ en est un - pendant que le fil JavaFX les rebâtit.
    ///
    /// Le point vient de `robot.point(...)` et non d'un calcul à nous : `BoundsLocatorImpl`
    /// **intersecte** les bornes avec la scène avant d'ajouter les décalages, et le centre naïf
    /// tombait hors de la fenêtre pour un champ qui dépasse. L'ADR porte la mesure et le récit.
    private static Point2D pointSurLeFil(FxRobot robot, String cible) {
        return Attente.surLeFil(
                () -> robot.point(exigerVisible(robot, cible)).query(), "situer « " + cible + " »", SELECTION_MS);
    }

    /// La même, pour un nœud déjà en main : `moveTo(Node)` situe ses bornes hors du fil tout autant.
    ///
    /// **Aucun contrôle de visibilité ici, et c'est voulu** : `moveTo(Node)` n'en faisait pas non
    /// plus, seule la forme par sélecteur passant par `pointOfVisibleNode`. En ajouter un
    /// changerait le comportement des gestes qui tiennent un nœud, sous couvert de corriger un
    /// défaut de fil.
    private static Point2D pointSurLeFil(FxRobot robot, Node cible) {
        return Attente.surLeFil(() -> robot.point(cible).query(), "situer le nœud en main", SELECTION_MS);
    }

    /// Le point de la cible que `reconnue` désigne, résolu ET situé sur le fil (#5734).
    ///
    /// `robot.point(Predicate)` fait les deux, donc aucune référence ne sort de l'aller-retour. Si
    /// le prédicat ne reconnaît rien, TestFX lève, et `Attente.surLeFil` nomme ce qu'on cherchait.
    private static Point2D pointSurLeFil(FxRobot robot, Predicate<Node> reconnue, String ceQueOnCherche) {
        return Attente.surLeFil(() -> robot.point(reconnue).query(), "situer " + ceQueOnCherche, SELECTION_MS);
    }

    /// Le nœud que `cible` désigne, **visible au sens de TestFX**, ou un refus qui le dit.
    ///
    /// Le prédicat est celui de TestFX, importé et non réécrit : une seconde façon de juger la
    /// visibilité divergerait de celle sur laquelle [#amenerDansLeCadre] s'appuie pour savoir quand
    /// défiler, et c'est ce refus-là - « returned n nodes, but no nodes were visible » - que le
    /// geste doit continuer de rendre.
    private static Node exigerVisible(FxRobot robot, String cible) {
        return robot.lookup(cible)
                .match(NodeQueryUtils.isVisible())
                .tryQuery()
                .orElseThrow(() -> new IllegalStateException("« " + cible + " » ne désigne aucun nœud"
                        + " VISIBLE : soit il n'existe pas, soit il est sous un bord."));
    }
}

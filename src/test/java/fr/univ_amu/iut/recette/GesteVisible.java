package fr.univ_amu.iut.recette;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
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
        int[] courses = new int[1];
        try {
            WaitForAsyncUtils.waitFor(SECONDES_CADRE, TimeUnit.SECONDS, () -> unePasse(robot, selecteur, courses));
            // Une course absorbée est un passage qui aurait ROUGI avant #4823. Le taire rendrait le
            // remède invisible : on ne saurait plus si le banc est sain ou seulement rattrapé, et le
            // jour où la course s'aggrave, rien ne le dirait.
            if (courses[0] > 0) {
                System.err.println("GesteVisible : « " + selecteur + " » est venu dans le cadre après " + courses[0]
                        + " passe(s) avortées sur une course interne à JavaFX (#4823).");
            }
        } catch (TimeoutException jamais) {
            throw new IllegalStateException("« " + selecteur + " » n'est jamais venu dans le cadre en "
                    + SECONDES_CADRE + " s" + cequiaavorte(courses[0]) + ". Rendre la main sans l'avoir"
                    + " amené reporterait l'échec sur le clic suivant, qui l'annoncerait comme une"
                    + " absence de nœud.");
        }
    }

    /// Ce que le message doit dire des passes absorbées, et rien s'il n'y en a pas eu.
    ///
    /// Une passe rejouée n'est pas un échec, mais dix mille le sont : sans ce compte, un banc qui rame
    /// se lit comme un banc qui refuse (ADR 0008).
    private static String cequiaavorte(int courses) {
        if (courses == 0) {
            return "";
        }
        return ", dont " + courses + " passe(s) avortées sur une course interne à JavaFX pendant le"
                + " calcul des bornes";
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
    private static boolean unePasse(FxRobot robot, String selecteur, int[] courses) {
        AtomicBoolean atteignable = new AtomicBoolean();
        try {
            robot.interact(() -> {
                Node cible = robot.lookup(selecteur).query();
                for (ScrollPane panneau : panneauxDont(cible)) {
                    amener(panneau, cible);
                }
            });
            WaitForAsyncUtils.waitForFxEvents();
            robot.interact(() -> atteignable.set(estDansLeCadre(robot, selecteur)));
        } catch (RuntimeException rejet) {
            if (!estUneCourseDeBornes(rejet)) {
                throw rejet;
            }
            courses[0]++;
            return false;
        }
        return atteignable.get();
    }

    /// La passe a-t-elle avorté sur une course INTERNE À JAVAFX pendant le calcul des bornes ?
    ///
    /// La reconnaissance exige **le type ET la pile**. Rattraper toutes les `IndexOutOfBoundsException`
    /// éteindrait des pannes du dépôt, qui doivent rester des pannes (#4823).
    private static boolean estUneCourseDeBornes(Throwable rejet) {
        for (Throwable cause = rejet; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (avorteDansLesBornes(cause)) {
                return true;
            }
            for (Throwable etouffee : cause.getSuppressed()) {
                if (avorteDansLesBornes(etouffee)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean avorteDansLesBornes(Throwable cause) {
        if (!(cause instanceof ConcurrentModificationException || cause instanceof IndexOutOfBoundsException)) {
            return false;
        }
        for (StackTraceElement cadre : cause.getStackTrace()) {
            if ("javafx.scene.Parent".equals(cadre.getClassName())
                    && "updateCachedBounds".equals(cadre.getMethodName())) {
                return true;
            }
        }
        return false;
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
        robot.moveTo(cible);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.entreDeuxGestes(robot);

        robot.clickOn(cible);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// Même chose sur un noeud déjà en main, quand le scénario le tient plutôt que son sélecteur.
    public static void cliquer(FxRobot robot, Node cible) {
        robot.moveTo(cible);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.entreDeuxGestes(robot);

        robot.clickOn(cible);
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
        Attente.que(
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
        robot.clickOn(idDuMenu);
        WaitForAsyncUtils.waitForFxEvents();
        Attente.que(
                () -> robot.lookup(libelle).tryQuery().isPresent(),
                "l'entrée « " + libelle + " » paraît dans le menu ouvert",
                5_000L);
        Respiration.leTempsDeLire(robot);

        // Le pointeur VA sur l'entrée, et s'y arrête, AVANT de cliquer. Sans cet arrêt, le clic et la
        // fermeture du menu tombent sur la même trame : on voit le menu, puis l'écran d'après, et jamais
        // le choix.
        cliquer(robot, libelle);
    }
}

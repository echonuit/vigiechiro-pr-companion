package fr.univ_amu.iut.commun.view;

import java.net.URL;
import java.util.List;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Region;

/// Ce qu'une fenêtre de l'application porte **toujours** : sa police et ses feuilles de socle.
///
/// Embarquer une police ne la **sélectionne** pas : c'est `base.css` qui la demande. Déclarée à la
/// main dans chaque FXML, elle manquait aux dix fenêtres qui naissent d'un `new Scene(vue)` - modales
/// de point, de site, de rattachement, de connexion, de qualification, dialogues de progression -
/// lesquelles rendaient avec la police par défaut de JavaFX, différente de celle du chrome et
/// différente d'une machine à l'autre. C'est le défaut que [Typographie] visait, resté entier à côté.
///
/// La déclarer partout à la main marcherait, et se déferait à la fenêtre suivante : c'est le
/// raisonnement de [Modales] pour la fermeture par Échap. Un seul patron, appelé à la création de
/// chaque fenêtre, et `ScenesHabilleesTest` verrouille l'invariant.
///
/// **Les captures s'en servent aussi.** Elles montent leurs scènes sans le chrome ; en passant par le
/// même habillage, un aperçu montre l'écran tel que l'utilisateur le voit, par construction. La CI et
/// un poste rendent alors le même fichier octet pour octet, ce qui supprime les allers-retours du
/// garde de troncature (ADR 3374).
public final class Habillage {

    /// Les couleurs « looked-up » du module, que [#FEUILLE_DE_BASE] consomme.
    private static final String FEUILLE_PALETTE = "/fr/univ_amu/iut/commun/view/palette.css";

    /// Police et habillage du socle.
    private static final String FEUILLE_DE_BASE = "/fr/univ_amu/iut/commun/view/base.css";

    /// Les composants partagés (badges, cartes-sections, puces). Le chrome la déclare **après**
    /// `base.css`, et l'ordre porte du sens : `design.css` doit pouvoir la reprendre.
    private static final String FEUILLE_DESIGN = "/fr/univ_amu/iut/commun/view/design.css";

    private Habillage() {}

    /// Fabrique la scène d'une fenêtre de l'application, habillée.
    public static Scene scene(Parent racine) {
        Scene scene = new Scene(racine);
        poser(scene);
        return scene;
    }

    /// Variante dimensionnée, pour les fenêtres qui fixent leur taille d'ouverture.
    public static Scene scene(Parent racine, double largeur, double hauteur) {
        Scene scene = new Scene(racine, largeur, hauteur);
        poser(scene);
        return scene;
    }

    /// Pose la police et les feuilles de socle sur une scène **déjà construite**.
    ///
    /// ⚠️ L'ordre compte, et le mauvais échoue **en silence**. `MainView.fxml` déclare
    /// `palette.css, base.css, design.css` : `base.css` consomme `-couleur-fond`, défini par
    /// `palette.css`. Posée avant elle - ou sur la **scène** quand `palette.css` est sur le nœud
    /// racine - la couleur ne se résout pas, et JavaFX **avale la règle** en journalisant un
    /// `ClassCastException` sur `-fx-background-color`. La fenêtre s'ouvre sans son fond, sans que
    /// rien n'échoue. Un premier essai l'a effectivement produit.
    ///
    /// On insère donc sur le **nœud racine**, juste après `palette.css`, ce qui reconstitue l'ordre du
    /// chrome et laisse la feuille de la fonctionnalité, déclarée ensuite, prioritaire.
    public static void poser(Scene scene) {
        Typographie.installer();
        String base = url(FEUILLE_DE_BASE);
        List<String> surLaRacine = scene.getRoot().getStylesheets();
        List<String> surLaScene = scene.getStylesheets();

        // ⚠️ La présence de `base.css` ne prouve PAS celle du trio (#3978). Ce retour se contentait de
        // la constater et s'arrêtait là : vrai de `MainView.fxml`, qui déclare les trois feuilles, faux
        // d'`EcranReglages.fxml`, seul FXML du dépôt à déclarer `palette + base` sans `design`. Une
        // scène montée sur cette racine n'obtenait jamais les composants partagés, et le symptôme ne
        // se voyait que dans la galerie - en production l'écran est empilé dans le chrome, dont il
        // hérite les feuilles.
        //
        // C'est la forme du défaut décrit dans l'amendement de l'ADR 3374, un cran plus loin : là on
        // avait vérifié un ORDRE sans vérifier une LISTE ; ici une PRÉSENCE prise pour un ENSEMBLE.
        List<String> porteuse = null;
        if (surLaRacine.contains(base)) {
            porteuse = surLaRacine;
        } else if (surLaScene.contains(base)) {
            porteuse = surLaScene;
        } else if (insererApres(surLaRacine, base)) {
            // `palette.css` vit tantôt sur le nœud racine (déclarée par le FXML), tantôt sur la scène
            // (ajoutée à la main, comme pour la scène hôte d'un menu ouvert). On suit son niveau :
            // insérée ailleurs, `base.css` passerait DEVANT la feuille de la fonctionnalité.
            porteuse = surLaRacine;
        } else if (insererApres(surLaScene, base)) {
            porteuse = surLaScene;
        }

        if (porteuse == null) {
            // Aucune des deux, nulle part : un contenu de dialogue monté seul. On pose alors le **trio
            // du chrome**, dans son ordre - `MainView.fxml` déclare `palette, base, design`.
            //
            // ⚠️ Poser `base.css` sans `palette.css` la laisserait sans ses couleurs ; la poser sans
            // `design.css` prive la scène des composants partagés (badges, cartes-sections) que son
            // contenu utilise pourtant. Une scène nue n'est pas moins l'application qu'une autre.
            surLaRacine.add(0, url(FEUILLE_PALETTE));
            surLaRacine.add(1, base);
            // ⚠️ Par `garantirDesign` et non par un `add(2, ...)` aveugle (#3985) : une racine peut
            // porter `design.css` sans porter ni `palette.css` ni `base.css` - c'est la forme de
            // `CapturePublicationCorrections` - et l'ajouter sans regarder la posait DEUX fois.
            garantirDesign(surLaRacine, base);
            return;
        }
        garantirDesign(porteuse, base);
    }

    /// Pose `design.css` **juste après** `base.css` si elle manque, dans la liste qui porte déjà le
    /// socle.
    ///
    /// Juste après, et non en fin de liste : la feuille de la **fonctionnalité** est déclarée ensuite
    /// et doit rester prioritaire sur les composants partagés qu'elle surcharge.
    private static void garantirDesign(List<String> feuilles, String base) {
        String design = url(FEUILLE_DESIGN);
        if (feuilles.contains(design)) {
            return;
        }
        feuilles.add(feuilles.indexOf(base) + 1, design);
    }

    /// Pose les feuilles de socle sur le panneau d'un **dialogue** (#1499).
    ///
    /// Un `Alert` vit dans sa **propre scène** : il n'hérite pas des feuilles de la fenêtre qui l'ouvre.
    /// Sans ce geste, la confirmation s'affiche avec le rendu par défaut de JavaFX - titre doublé, icône
    /// « ? » système, boutons gris - au milieu d'une application entièrement habillée. C'est le même
    /// mécanisme que pour les scènes, à ceci près qu'un dialogue n'a pas de nœud racine à nous : les
    /// feuilles vont sur le `DialogPane` lui-même.
    ///
    /// Deux classes posaient déjà ces feuilles à la main, **chacune sa copie de la même boucle**, et
    /// quatre autres ne les posaient pas du tout. Un seul point de passage, comme pour les fenêtres :
    /// `DialoguesHabillesTest` verrouille l'invariant.
    /// ⚠️ **Attacher les feuilles ne suffit pas**, et l'oublier donne un correctif inerte qui se
    /// présente en succès. Aucune règle ne visait les dialogues : le premier essai a attaché les trois
    /// feuilles et la capture du dialogue est restée identique **au bit près**. `design.css` porte
    /// désormais les règles `.dialog-pane`, et ce point de passage retire en plus l'**icône système**
    /// (le « ? ») que JavaFX pose sur une confirmation, laquelle ne s'enlève pas par le CSS.
    public static void poser(DialogPane panneau) {
        Typographie.installer();
        List<String> feuilles = panneau.getStylesheets();
        for (String ressource : new String[] {FEUILLE_PALETTE, FEUILLE_DE_BASE, FEUILLE_DESIGN}) {
            String feuille = url(ressource);
            if (!feuilles.contains(feuille)) {
                feuilles.add(feuille);
            }
        }
        retirerLIconeSysteme(panneau);
    }

    /// Retire l'icône que JavaFX pose sur un dialogue portant un en-tête.
    ///
    /// ⚠️ `setGraphic(null)` ne retire **rien**, et c'est contre-intuitif : quand le graphique est nul,
    /// c'est le **skin** qui fournit l'icône par défaut de l'`AlertType`. Mettre `null` ne fait donc que
    /// laisser le thème décider. Un premier essai l'a cru, et « À propos » a garde son « i » pendant que
    /// les confirmations d'import perdaient leur « ? » - non pas grâce au correctif, mais parce qu'elles
    /// n'ont pas d'en-tête du tout.
    ///
    /// On fournit donc un graphique **vide** plutôt qu'aucun : le skin n'a plus rien à substituer.
    private static void retirerLIconeSysteme(DialogPane panneau) {
        panneau.setGraphic(new Region());
    }

    /// Insère `base.css` juste après `palette.css` dans cette liste, si elle s'y trouve.
    private static boolean insererApres(List<String> feuilles, String base) {
        for (int i = 0; i < feuilles.size(); i++) {
            if (feuilles.get(i).endsWith("palette.css")) {
                feuilles.add(i + 1, base);
                return true;
            }
        }
        return false;
    }

    /// URL d'une feuille du module, telle que `getStylesheets()` l'attend.
    ///
    /// ⚠️ **Nomme la feuille manquante**, comme [ChargeurFxml] nomme son FXML (#3700). Sans cela, un
    /// `target/classes` périmé donnait ici une `NullPointerException` **nue** : aucune indication de la
    /// ressource en cause, et aucun remède. Pire, comme cette méthode sert à habiller l'alerte que le
    /// filet global affiche, l'incident se rejouait à l'infini - une exécution réelle a produit
    /// **16 217 exceptions**, dont une seule portait le diagnostic.
    private static String url(String ressource) {
        URL emplacement = Habillage.class.getResource(ressource);
        if (emplacement == null) {
            throw new IllegalStateException("Feuille de style introuvable sur le classpath : « " + ressource
                    + " » (attendue à côté de " + Habillage.class.getName() + "). Le dossier target/classes est"
                    + " probablement obsolète après un « git pull » : reconstruisez avec"
                    + " « ./mvnw clean javafx:run ».");
        }
        return emplacement.toExternalForm();
    }
}

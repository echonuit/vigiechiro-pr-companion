package fr.univ_amu.iut.commun.view;

import java.util.List;
import javafx.scene.Parent;
import javafx.scene.Scene;

/// Ce qu'une fenêtre de l'application porte **toujours** : sa police et ses feuilles de socle.
///
/// ## Le défaut : `base.css` était déclarée à la main, donc oubliée
///
/// Seuls `MainView.fxml` et `EcranReglages.fxml` déclaraient `base.css`. La fenêtre **principale** la
/// portait donc, et toute vue de fonctionnalité en héritait : elle s'affiche *dans* le chrome. Mais
/// les **dix autres fenêtres** de l'application - modales de point, de site, de rattachement, de
/// connexion, de qualification, dialogues de progression - naissent d'un `new Scene(vue)` sur un FXML
/// qui déclare `palette.css` et `design.css`, jamais `base.css`.
///
/// Conséquence : elles rendaient avec la police **par défaut de JavaFX**, différente de celle de la
/// fenêtre qui les portait, et différente d'une machine à l'autre. Exactement le défaut que
/// [Typographie] visait, resté entier à côté : embarquer une police ne la **sélectionne** pas, c'est
/// `base.css` qui la demande.
///
/// ## Pourquoi ici, et pas dans chaque FXML
///
/// Ajouter `@base.css` aux dix FXML aurait marché, et se serait défait au onzième. C'est le
/// raisonnement de [Modales] pour la fermeture par Échap : un seul patron, appelé à la création de
/// chaque fenêtre, plutôt qu'une consigne recopiée. `ScenesHabilleesTest` verrouille l'invariant.
///
/// ## Les captures s'en servent aussi, et ce n'est pas un détail
///
/// Les outils de capture montent leurs scènes **sans** le chrome. En passant par le même habillage,
/// un aperçu montre l'écran tel que l'utilisateur le voit - par construction, et non parce qu'on y a
/// pensé. Mesuré : la CI et un poste de développement rendent alors le **même fichier, octet pour
/// octet**, ce qui supprime les allers-retours du garde de troncature (ADR 3374).
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
        if (surLaRacine.contains(base) || surLaScene.contains(base)) {
            return;
        }
        // `palette.css` vit tantôt sur le nœud racine (déclarée par le FXML), tantôt sur la scène
        // (ajoutée à la main, comme pour la scène hôte d'un menu ouvert). On suit son niveau : insérée
        // ailleurs, `base.css` passerait DEVANT la feuille de la fonctionnalité au lieu de derrière.
        if (insererApres(surLaRacine, base) || insererApres(surLaScene, base)) {
            return;
        }
        // Aucune des deux, nulle part : un contenu de dialogue monté seul. On pose alors le **trio du
        // chrome**, dans son ordre - `MainView.fxml` déclare `palette, base, design`.
        //
        // ⚠️ Poser `base.css` sans `palette.css` la laisserait sans ses couleurs ; la poser sans
        // `design.css` prive la scène des composants partagés (badges, cartes-sections) que son
        // contenu utilise pourtant. Une scène nue n'est pas moins l'application qu'une autre.
        surLaRacine.add(0, url(FEUILLE_PALETTE));
        surLaRacine.add(1, base);
        surLaRacine.add(2, url(FEUILLE_DESIGN));
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
    private static String url(String ressource) {
        return Habillage.class.getResource(ressource).toExternalForm();
    }
}

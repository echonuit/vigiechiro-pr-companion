package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// `Habillage` pose-t-il `base.css` **au bon niveau**, dans les trois formes de scène ? (#3374)
///
/// ## Pourquoi le niveau, et pas seulement la présence
///
/// `base.css` consomme `-couleur-fond`, défini par `palette.css`. Posée ailleurs que la palette,
/// la couleur ne se résout pas : JavaFX **avale la règle** en journalisant un `ClassCastException`, et
/// la fenêtre s'ouvre sans son fond **sans que rien n'échoue**. Un premier jet l'a produit.
///
/// Un test qui vérifierait seulement « `base.css` est là » resterait donc vert sur ce défaut. Ces cas
/// vérifient **où** elle est, et **après quoi**.
///
/// ## Le survivant de PIT, lu et gardé
///
/// PIT sur cette classe : 19 mutations, **16 tuées**. Le survivant qui compte est
/// `removed call to Typographie::installer` - rien ici ne constate que `poser` **installe la police**.
///
/// Le trou est réel, et l'assertion qui le comblerait **mentirait** : `Font.getFamilies()` contient
/// « Noto Sans » sur toute machine qui l'a en système - la mienne, par exemple. Le test passerait au
/// vert sans que la police soit chargée, ce qui est exactement le faux vert que `TypographieTest`
/// documente et refuse.
///
/// Il est donc laissé **sciemment** : la garantie que la police est bien dans le jar tient à
/// `TypographieTest#les_fichiers_sont_embarques`, qui, lui, ne dépend d'aucune machine.
@ExtendWith(ApplicationExtension.class)
class HabillageTest {

    private static final String BASE = "base.css";
    private static final String PALETTE = "palette.css";

    /// Les composants partagés, troisième du trio que le chrome déclare.
    private static final String DESIGN = "design.css";

    private static String url(String feuille) {
        return Habillage.class.getResource(feuille).toExternalForm();
    }

    /// Une feuille d'une **autre** fonctionnalité, désignée par son chemin absolu.
    private static String urlDeFeature(String chemin) {
        return Habillage.class.getResource(chemin).toExternalForm();
    }

    @Test
    @DisplayName("#3374 : palette sur le nœud racine - base.css s'y insère JUSTE APRÈS elle")
    void palette_sur_la_racine() {
        StackPane racine = new StackPane();
        racine.getStylesheets().addAll(url(PALETTE), url(DESIGN));
        Scene scene = new Scene(racine);

        Habillage.poser(scene);

        assertThat(racine.getStylesheets())
                .as("l'ordre du chrome : palette, base, puis la feuille de la fonctionnalité - qui reste "
                        + "prioritaire parce qu'elle est déclarée après")
                .containsExactly(url(PALETTE), url(BASE), url(DESIGN));
        assertThat(scene.getStylesheets())
                .as("rien sur la scène : `base.css` n'y résoudrait pas les couleurs de la palette, qui vit "
                        + "sur la racine")
                .isEmpty();
    }

    @Test
    @DisplayName("#3374 : palette sur la SCÈNE - base.css la suit là, et non sur la racine")
    void palette_sur_la_scene() {
        // La forme de la scène hôte d'un menu ouvert : `ApercuFx` y verse les feuilles héritées des
        // ancêtres du vrai menu, au niveau de la scène. Insérer base.css sur la RACINE la ferait passer
        // devant elles, donc devant la feuille de la fonctionnalité.
        Scene scene = new Scene(new StackPane());
        scene.getStylesheets().addAll(url(PALETTE), url(DESIGN));

        Habillage.poser(scene);

        assertThat(scene.getStylesheets()).containsExactly(url(PALETTE), url(BASE), url(DESIGN));
        assertThat(scene.getRoot().getStylesheets()).isEmpty();
    }

    @Test
    @DisplayName("#3374 : aucune des deux - la PAIRE est posée, jamais base.css seule")
    void ni_l_une_ni_l_autre() {
        // Le contenu d'un dialogue monté seul. `base.css` sans `palette.css` la laisserait sans ses
        // couleurs : c'est le cas qui a produit le ClassCastException avalé.
        Scene scene = new Scene(new StackPane());

        Habillage.poser(scene);

        assertThat(scene.getRoot().getStylesheets())
                .as("le TRIO du chrome, dans son ordre : sans `design.css`, une scène nue perd les "
                        + "composants partagés (badges, cartes-sections) que son contenu utilise")
                .containsExactly(url(PALETTE), url(BASE), url(DESIGN));
    }

    @Test
    @DisplayName("#3374 : la feuille de la FONCTIONNALITÉ reste la dernière, donc prioritaire")
    void la_feuille_de_feature_reste_derniere() {
        // La forme réelle d'une vue de fonctionnalité : son FXML déclare palette, design et sa propre
        // feuille. `base.css` s'insère APRÈS la palette, jamais après la feuille de la vue - sans quoi
        // le socle reprendrait la main sur ce que l'écran a voulu poser par-dessus.
        String saison = urlDeFeature("/fr/univ_amu/iut/saison/view/saison.css");
        StackPane racine = new StackPane();
        racine.getStylesheets().addAll(url(PALETTE), url(DESIGN), saison);
        Scene scene = new Scene(racine);

        Habillage.poser(scene);

        assertThat(racine.getStylesheets()).containsExactly(url(PALETTE), url(BASE), url(DESIGN), saison);
    }

    @Test
    @DisplayName("#3374 : poser deux fois n'ajoute rien - les points d'entrée s'appellent en cascade")
    void idempotent() {
        // `ExportGraphe` construit sa scène par `Habillage.scene(...)` puis la confie à `RenduPng`, qui
        // pose à son tour. Sans ce contrat, `base.css` s'empilerait à chaque niveau.
        Scene scene = Habillage.scene(new StackPane());
        int apresLePremier = scene.getRoot().getStylesheets().size();

        Habillage.poser(scene);

        assertThat(scene.getRoot().getStylesheets()).hasSize(apresLePremier);
    }
}

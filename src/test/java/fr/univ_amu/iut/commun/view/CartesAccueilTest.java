package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Garde-fou de mise en page des cartes d'accueil (#2046). [ApplicationExtension] initialise le
/// toolkit JavaFX ; aucune scène affichée.
///
/// Deux titres sur cinq sont plus longs que leur carte (« Espèces & observations », « Audit de
/// cohérence »). Ils étaient rendus par un `Label` sans `wrapText` : ils s'ellipsaient, sur le premier
/// écran de l'application et sur le **nom** de ses entrées. La description, elle, était déjà passée en
/// `Text` avec `wrappingWidth` pour cette raison exacte - le remède se trouvait dans le fichier, une
/// ligne au-dessus, sans avoir été appliqué au titre.
///
/// Ce défaut ne fait rougir **aucun** test de comportement : la carte s'ouvre, son rôle accessible est
/// bon, son intitulé accessible est complet. Seul le texte affiché était coupé.
///
/// Le test mesure donc une **hauteur**, pas un type de nœud : un titre qui ne tient pas sur la largeur
/// d'une carte doit occuper deux lignes. Formulé ainsi, il reste valable si la carte change un jour de
/// technique d'enroulement, et il échoue pour la bonne raison si l'enroulement disparaît.
@ExtendWith(ApplicationExtension.class)
class CartesAccueilTest {

    private static final String TITRE_LONG = "Espèces & observations";
    private static final String TITRE_COURT = "Sites";

    @Test
    @DisplayName("Un titre trop long pour la carte s'enroule au lieu d'être tronqué")
    void un_titre_long_s_enroule() {
        double hauteurCourt = titreDe(carte(TITRE_COURT)).getLayoutBounds().getHeight();
        double hauteurLong = titreDe(carte(TITRE_LONG)).getLayoutBounds().getHeight();

        assertThat(hauteurLong)
                .as(
                        "« %s » ne tient pas sur la largeur d'une carte : son titre doit passer sur une "
                                + "seconde ligne (donc être plus haut qu'un titre court de %s px), et non se "
                                + "faire couper par une ellipse",
                        TITRE_LONG, hauteurCourt)
                .isGreaterThan(hauteurCourt);
    }

    @Test
    @DisplayName("Le titre ne déborde jamais de la largeur de texte de la carte")
    void le_titre_reste_dans_la_carte() {
        Node titre = titreDe(carte(TITRE_LONG));
        Node description = parClasse(carte(TITRE_LONG), "carte-activite-desc");

        assertThat(titre.getLayoutBounds().getWidth())
                .as("un titre plus large que la description sortirait de la carte ou la ferait grandir : "
                        + "les deux blocs de texte d'une carte s'enroulent sur la même largeur")
                .isLessThanOrEqualTo(description.getLayoutBounds().getWidth());
    }

    /// Monte la carte dans une **scène habillée**, puis applique le CSS et la mise en page. Sans les
    /// feuilles du chrome le titre garde la police par défaut (~13 px) au lieu de son `15px bold`, et
    /// « Espèces & observations » **tient** alors sur une ligne : le test mesurerait une carte qui
    /// n'existe pas, et resterait vert avec le défaut en place.
    ///
    /// ⚠️ Les feuilles ne suffisent pas, et ce test l'a montré de la pire façon (#3526) : les poser à la
    /// main sans appeler [Typographie#installer] laisse la police embarquée **non enregistrée** auprès
    /// de JavaFX. `base.css` la demandait alors en vain, et le rendu retombait sur la police du système.
    ///
    /// Ce n'était pas une différence de plateforme, contrairement à ce que le premier diagnostic disait.
    /// `installer()` garde un `static boolean installee` : l'enregistrement est **global au JVM et fait
    /// une seule fois**. Dans un fork surefire, ce test voyait donc « Noto Sans » **si un autre test
    /// l'avait installée avant lui**, et la police du système sinon - c'est l'ordre d'exécution qui
    /// décidait.
    ///
    /// ⚠️ Et sous Linux, rien de tout cela ne se voit - non parce que le repli serait large, mais parce
    /// que `Noto Sans` y est une police **système** (219 entrées sous `/usr/share/fonts/truetype/noto/`
    /// sur la machine de développement) : la suite locale la trouve **installée ou non**, et aucune
    /// mesure faite là ne peut juger ce défaut.
    ///
    /// ⚠️ **Le runner Ubuntu, lui, n'a pas été mesuré**, et une première version de ce commentaire
    /// l'affirmait quand même. L'ADR 3361 dit seulement que `sans-serif` s'y résout en « une police
    /// plus large » (#3826, passe 0). Sous macOS, en revanche, la mesure existe : `Noto Sans` n'y est
    /// pas, seul `installer()` la fournit, et le verdict bascule avec l'ordre.
    ///
    /// La preuve est au dossier, et elle a été refaite exprès (#3773) : le **même commit**, sur la
    /// **même image** `macos-26-arm64` (`20260728.0273.1`), a rendu vert à 8 h 14 et rouge à 15 h 34.
    /// Puis, joué **seul** sous macOS - donc sans aucun voisin pour installer la police -, ce test
    /// **échoue**, avec les mêmes 17,666 px. Un test dont le verdict dépend de ses voisins, pas de ce
    /// qu'il mesure.
    ///
    /// Le titre court mesure 20,43 px avec la police embarquée, contre 17,666 px relevés sous macOS avec
    /// celle du système : c'est l'écart qui faisait tenir « Espèces & observations » sur une ligne.
    ///
    /// `Habillage.scene(...)` fait les deux - installer la police, poser le trio du chrome - et c'est
    /// la raison d'être de ce patron : une scène montée à la main en oublie toujours une moitié.
    private static VBox carte(String titre) {
        VBox carte = (VBox) CartesAccueil.carte(new ActiviteDeTest(titre));
        Scene scene = Habillage.scene(new StackPane(carte));
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        return carte;
    }

    private static Node titreDe(VBox carte) {
        return parClasse(carte, "carte-activite-titre");
    }

    private static Node parClasse(VBox carte, String classeCss) {
        return carte.getChildren().stream()
                .filter(n -> n.getStyleClass().contains(classeCss))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucun nœud « " + classeCss + " » dans la carte"));
    }

    /// Activité minimale : seul le titre varie d'un cas à l'autre.
    private record ActiviteDeTest(String titre) implements ActiviteAccueil {

        @Override
        public Prisme prisme() {
            return Prisme.values()[0];
        }

        @Override
        public int ordre() {
            return 0;
        }

        @Override
        public String iconeLiteral() {
            return "fas-leaf";
        }

        @Override
        public String couleur() {
            return "#27ae60";
        }

        @Override
        public String description() {
            return "Une description d'activité, assez longue pour occuper plusieurs lignes de carte.";
        }

        @Override
        public String pageDoc() {
            return "index";
        }

        @Override
        public void ouvrir() {
            // Rien à ouvrir : ce test ne regarde que la mise en page.
        }
    }
}

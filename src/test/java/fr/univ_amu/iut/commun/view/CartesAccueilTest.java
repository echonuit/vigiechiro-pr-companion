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

    /// Monte la carte dans une **scène qui porte les feuilles du chrome**, puis applique le CSS et la
    /// mise en page. Sans cela le titre garde la police par défaut (~13 px) au lieu de son `15px bold`,
    /// et « Espèces & observations » **tient** alors sur une ligne : le test mesurerait une carte qui
    /// n'existe pas, et resterait vert avec le défaut en place.
    private static VBox carte(String titre) {
        VBox carte = (VBox) CartesAccueil.carte(new ActiviteDeTest(titre));
        Scene scene = new Scene(new StackPane(carte));
        scene.getStylesheets()
                .addAll(
                        CartesAccueil.class.getResource("palette.css").toExternalForm(),
                        CartesAccueil.class.getResource("base.css").toExternalForm());
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

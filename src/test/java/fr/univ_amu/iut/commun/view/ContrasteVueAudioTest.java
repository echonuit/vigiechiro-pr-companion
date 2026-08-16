package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.nedjar.vigiechiro.audio.AudioView;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// **Contraste des commandes de la vue audio, mesuré sur rendu** (#3462).
///
/// ## Le défaut, et ce que la mesure a corrigé au diagnostic
///
/// Un utilisateur n'a pas su lancer l'écoute pendant toute une séance de vérification : *« je n'avais
/// pas vu le bouton lecture du fait de la sobriété de l'écran (bouton noir sur fond noir) »*. Le
/// relevé des pixels a déplacé le défaut :
///
/// | Ce qu'on compare | Mesuré | Seuil |
/// | --- | --- | --- |
/// | **texte** du bouton sur sa surface | 10,68:1 | 4,5:1 (WCAG 1.4.3) ✅ |
/// | **surface** du bouton sur le fond de la barre | **1,17:1** | 3:1 (WCAG 1.4.11) ❌ |
///
/// Le libellé était donc parfaitement lisible. Ce qui manquait, c'est que le bouton se **voie comme un
/// bouton**. C'est un défaut d'**affordance**, pas de lisibilité, et le remède n'est pas le même :
/// éclaircir le texte n'aurait rien réparé.
///
/// ## Pourquoi ce test-ci, et pas une ligne de plus dans ContrasteAATest
///
/// [ContrasteAATest] lit les **feuilles de style** et nomme lui-même ce qu'il ne sait pas faire :
/// *« les couleurs littérales posées sans leur fond dans la même règle […] seule une mesure sur rendu
/// le donnerait »*. C'est exactement ce cas : les couleurs viennent d'une **bibliothèque tierce**
/// (`fr.nedjar.vigiechiro:audio-view`), le fond et la surface sont posés par deux règles différentes,
/// et le résultat dépend de la **cascade**.
///
/// Ce test lit donc les couleurs que le moteur CSS a **effectivement appliquées** aux nœuds, pas ce
/// qu'une feuille déclare. La distinction n'est pas théorique : `AudioView.fxml` porte
/// `stylesheets="@audio-view.css"` **sur sa propre racine**, donc plus profond que les feuilles de
/// l'écran. Une surcharge écrite à spécificité égale serait **juste dans le fichier et sans effet à
/// l'écran** - un vert faux, sur un test qui lirait le CSS.
@ExtendWith(ApplicationExtension.class)
class ContrasteVueAudioTest {

    /// WCAG 2.2 §1.4.11 : seuil des composants d'interface **non textuels**. Ce qui fait qu'un contrôle
    /// se distingue de ce qui l'entoure, avant même qu'on lise son libellé.
    private static final double SEUIL_COMPOSANT = 3.0;

    private AudioView vueAudio;

    @Start
    void start(Stage stage) {
        vueAudio = new AudioView();
        // Le composant est monté sous les mêmes feuilles que les écrans qui l'emploient (qualification
        // et Sons & validation), sans quoi la cascade mesurée ne serait pas celle de production.
        StackPane racine = new StackPane(vueAudio);
        stage.setScene(Habillage.scene(racine, 900, 400));
        stage.show();
    }

    @Test
    @DisplayName("#3462 : chaque commande de la vue audio se détache de la barre à 3:1 au moins")
    void les_commandes_se_detachent_de_la_barre() {
        Region barre = (Region) vueAudio.lookup(".audio-view-toolbar");
        assertThat(barre)
                .as("la barre d'outils de la vue audio doit exister : sans elle ce test ne mesure rien")
                .isNotNull();

        Color fondDeLaBarre = premierFond(barre);
        List<String> souSSeuil = new ArrayList<>();

        for (Node noeud : barre.lookupAll(".button")) {
            Button bouton = (Button) noeud;
            // WCAG 1.4.11 se satisfait de l'un OU l'autre : une surface qui tranche, ou un contour qui
            // la borde. On retient donc le MEILLEUR des deux, sinon on exigerait les deux à la fois.
            double parLaSurface = contraste(premierFond(bouton), fondDeLaBarre);
            double parLeContour = contrasteDuContour(bouton, fondDeLaBarre);
            double retenu = Math.max(parLaSurface, parLeContour);

            if (retenu < SEUIL_COMPOSANT) {
                souSSeuil.add("« %s » : surface %.2f:1, contour %.2f:1"
                        .formatted(bouton.getText(), parLaSurface, parLeContour));
            }
        }

        assertThat(souSSeuil).as("""
                        Une commande de la vue audio ne se distingue pas de la barre qui la porte.

                        WCAG 2.2 §1.4.11 demande 3:1 entre un composant d'interface et ce qui
                        l'entoure. En dessous, le contrôle se LIT mais ne se VOIT pas : c'est ce qui a
                        empêché un utilisateur de lancer la moindre écoute pendant une séance entière
                        (#3462), alors que le texte du bouton était à 10,68:1.

                        Deux leviers, et la règle accepte les deux : une surface qui tranche, ou un
                        contour qui borde. Le second a été retenu parce qu'une surface assez claire
                        pour atteindre 3:1 dénaturerait une vue sombre à dessein - un spectrogramme se
                        lit sur du noir.

                        ⚠️ Ces boutons appartiennent à `fr.nedjar.vigiechiro:audio-view`, dont la
                        feuille est déclarée SUR SA PROPRE RACINE (`AudioView.fxml`), donc plus
                        profond que les feuilles de l'écran. Une surcharge à spécificité égale perd :
                        il faut monter à trois classes (`.audio-view .audio-view-toolbar .button`).
                        C'est précisément ce que ce test attrape et qu'une lecture de CSS manquerait.

                        Sous le seuil : %s
                        """.formatted(souSSeuil)).isEmpty();
    }

    /// Le premier fond **effectivement appliqué** à une région, ou transparent si elle n'en porte aucun.
    private static Color premierFond(Region region) {
        if (region.getBackground() == null || region.getBackground().getFills().isEmpty()) {
            return Color.TRANSPARENT;
        }
        return couleur(region.getBackground().getFills().get(0));
    }

    private static Color couleur(BackgroundFill remplissage) {
        Paint peinture = remplissage.getFill();
        return peinture instanceof Color teinte ? teinte : Color.TRANSPARENT;
    }

    /// Contraste du **contour** d'un bouton contre `fond`, ou 0 s'il n'en porte pas. Un contour de
    /// largeur nulle ne compte pas : il est déclaré mais ne peint rien.
    private static double contrasteDuContour(Region region, Color fond) {
        if (region.getBorder() == null || region.getBorder().getStrokes().isEmpty()) {
            return 0;
        }
        BorderStroke trait = region.getBorder().getStrokes().get(0);
        if (trait.getWidths().getTop() <= 0 || !(trait.getTopStroke() instanceof Color teinte)) {
            return 0;
        }
        return contraste(teinte, fond);
    }

    /// Rapport de contraste WCAG, `(L1 + 0,05) / (L2 + 0,05)`, comme [ContrasteAATest] le calcule sur
    /// les jetons de la palette. La formule est la même ; ce sont les **entrées** qui diffèrent, prises
    /// ici sur le rendu et non dans un fichier.
    private static double contraste(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color couleur) {
        return 0.2126 * canal(couleur.getRed())
                + 0.7152 * canal(couleur.getGreen())
                + 0.0722 * canal(couleur.getBlue());
    }

    private static double canal(double composante) {
        return composante <= 0.04045 ? composante / 12.92 : Math.pow((composante + 0.055) / 1.055, 2.4);
    }
}

package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Les contrôles que le socle prétend habiller rendent-ils **la hauteur qu'il annonce** ? (#4011)
///
/// ## Ce que ce garde vérifie, et ce qu'il ne vérifie pas
///
/// L'[ADR 4002](../../../../../../../dev-docs/decisions/4002-un-controle-se-juge-avec-ses-voisins-pas-seul.md)
/// acte que la **cohérence d'une rangée** n'est pas assertable : une barre porte légitimement des
/// contrôles de hauteurs différentes, et un garde qui crie sur du bon travail finit désactivé.
///
/// Ce qui **est** assertable, et que ce test tient : la promesse que le socle se fait à lui-même.
/// `design.css` déclare `-hauteur-controle-barre` sur cinq familles de contrôles ; ce test les monte
/// et **mesure**. Il ne dit rien de la composition d'un écran, tout de la tenue d'une feuille.
///
/// ## Le défaut qu'il empêche de revenir, et qui est arrivé en l'écrivant
///
/// ⚠️ La première version de la règle nommait `.combo-box-base` en croyant couvrir les listes. Une
/// `ChoiceBox` **ne dérive pas** de `ComboBoxBase` et ne porte donc pas cette classe : sur le même
/// rendu, la liste de tri du multisite (une `ComboBox`) était habillée, « 30 min » et « National
/// (aucun milieu) » (des `ChoiceBox`) restaient des contrôles de plateforme. Le défaut a été trouvé
/// **à l'œil**, en ouvrant deux aperçus - exactement ce que ce test rend inutile la prochaine fois.
///
/// C'est le même raisonnement que [ContrasteAATest] : *le défaut ne se voit ni à la compilation ni à
/// l'exécution sur la machine qui l'écrit, c'est justement pourquoi il a besoin d'un test.*
///
/// ## Pourquoi un test RENDU, et pas une lecture de CSS
///
/// Une lecture de source dirait que la règle existe. Elle ne dirait pas qu'elle **s'applique** : c'est
/// tout le sujet ici, puisque le défaut tenait à un sélecteur qui n'atteignait pas sa cible. Un garde
/// qui lit la feuille aurait été vert sur le bug qu'on corrige.
@ExtendWith(ApplicationExtension.class)
class SocleHauteurControlesTest {

    /// La valeur de `-hauteur-controle-barre` dans `palette.css`. Écrite en clair : un test qui relirait
    /// le jeton pour le comparer à lui-même serait vert quoi qu'il arrive.
    private static final double HAUTEUR_ATTENDUE = 34.0;

    /// Tolérance d'un pixel : une bordure d'un demi-pixel arrondit d'un côté ou de l'autre selon
    /// l'échelle de rendu. Au-delà, ce n'est plus un arrondi, c'est une règle qui ne s'applique pas.
    private static final double TOLERANCE = 1.0;

    private StackPane racine;

    @Start
    void start(Stage stage) {
        racine = new StackPane();
        stage.setScene(Habillage.scene(racine, 640, 200));
        stage.show();
    }

    @Test
    @DisplayName("#4011 : les cinq familles du socle rendent la hauteur que le jeton annonce")
    void toutes_les_familles_rendent_la_hauteur_du_jeton(FxRobot robot) {
        AtomicReference<Map<String, Control>> ref = new AtomicReference<>();
        robot.interact(() -> {
            Map<String, Control> controles = new LinkedHashMap<>();

            Button bouton = new Button("Enregistrer");
            bouton.getStyleClass().add("bouton-secondaire");
            controles.put("bouton-secondaire", bouton);

            controles.put("combo-box", new ComboBox<String>());
            controles.put("choice-box", new ChoiceBox<String>());
            controles.put("menu-button", new MenuButton("Certitude"));
            controles.put("spinner", new Spinner<Integer>(0, 100, 50));

            HBox rangee = new HBox(8);
            rangee.getChildren().addAll(controles.values());
            racine.getChildren().setAll(rangee);
            racine.applyCss();
            racine.layout();
            ref.set(controles);
        });

        assertThat(ref.get())
                .allSatisfy((nom, controle) -> assertThat(controle.getHeight())
                        .as(
                                "« %s » rend %.1f px, le socle en annonce %.1f",
                                nom, controle.getHeight(), HAUTEUR_ATTENDUE)
                        .isCloseTo(HAUTEUR_ATTENDUE, org.assertj.core.data.Offset.offset(TOLERANCE)));
    }
}

package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import java.util.List;
import java.util.function.Predicate;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Que devient une cible que l'écran a **reconstruite** entre la résolution et le clic (#5734) ?
///
/// Ce banc pose la mécanique de `MesSitesController` en six lignes : une liste observable, un
/// écouteur qui reconstruit tout, des cartes qui portent leur titre dans un enfant. Il ne recopie pas
/// l'écran de sites, il recopie **ce qui fait tomber** `ScenarioFicheSiteTest`.
///
/// Le premier cas **exige que la forme d'avant lève**. Sans lui, le second serait vert pour une
/// raison inconnue - une cible encore attachée, un prédicat qui ne reconnaît rien, un écouteur qui
/// n'a pas tourné. Et il rougirait si TestFX cessait de lever, ce qu'il faudrait savoir.
@ExtendWith(ApplicationExtension.class)
class GesteVisibleCibleReconstruiteTest {

    private static final String CHERCHE = "la carte intitulée « B »";

    private final ObservableList<String> titres = FXCollections.observableArrayList("A", "B", "C");

    private VBox liste;

    private int clics;

    /// Le titre de la carte que le dernier clic a touchée, pour que le cas dise QUI il a cliqué.
    private String dernierTitre;

    @Start
    void start(Stage stage) {
        liste = new VBox();
        // L'écouteur de `MesSitesController`, à l'identique : tout changement reconstruit TOUT.
        titres.addListener((ListChangeListener<String>) changement -> reconstruire());
        reconstruire();
        FenetreAjustable.poser(stage, liste, 600, 300);
        stage.show();
    }

    private void reconstruire() {
        liste.getChildren().clear();
        for (String titre : titres) {
            Label etiquette = new Label(titre);
            etiquette.getStyleClass().add("carte-titre");
            HBox carte = new HBox(etiquette);
            carte.getStyleClass().add("carte-site");
            carte.setPrefSize(400, 60);
            carte.setOnMouseClicked(evenement -> {
                clics++;
                dernierTitre = titre;
            });
            liste.getChildren().add(carte);
        }
    }

    /// Le prédicat de `ouvrirLaFiche`, dans sa forme : une carte dont un enfant porte ce titre.
    private static Predicate<Node> porteLeTitre(String titre) {
        return noeud -> noeud.getStyleClass().contains("carte-site")
                && noeud.lookupAll(".carte-titre").stream()
                        .anyMatch(enfant -> enfant instanceof Label label && titre.equals(label.getText()));
    }

    private HBox carteTenue(FxRobot robot) {
        return Attente.surLeFil(
                () -> robot.lookup(".carte-site").queryAllAs(HBox.class).stream()
                        .filter(porteLeTitre("B"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("aucune carte « B »")),
                "tenir " + CHERCHE,
                5_000L);
    }

    private void reconstruireLEcran() {
        Attente.surLeFil(() -> titres.add("D"), "déclencher la reconstruction de la liste", 5_000L);
        // `queSurLeFil` et non `que` : ce prédicat lit `getChildren()`, donc le graphe, et
        // l'ADR 5278 le veut lu sur le fil JavaFX. Son cliquet est à zéro.
        Attente.queSurLeFil(() -> liste.getChildren().size() == 4, "la liste s'est reconstruite", 5_000L);
    }

    @Test
    @DisplayName("#5734 : une carte TENUE que la liste a reconstruite n'a plus de scène")
    void une_carte_tenue_perd_sa_scene(FxRobot robot) {
        HBox tenue = carteTenue(robot);
        assertThat(Attente.surLeFil(() -> tenue.getScene() != null, "lire la scène de la carte", 5_000L))
                .as("la prémisse : avant la reconstruction, la carte tenue a bien une scène")
                .isTrue();

        reconstruireLEcran();

        assertThat(Attente.surLeFil(() -> tenue.getScene(), "relire la scène de la carte", 5_000L))
                .as("la carte tenue est détachée, et c'est ce que `getScene()` rend nul")
                .isNull();

        // C'est ici que le banc d'origine mourait : TestFX SITUE sa cible en lisant `getScene()`, et
        // le NPE porte sur `scene.getWidth()`. Le geste n'est donc pas en cause, la référence l'est.
        //
        // On éprouve `point(...)` et non `clickOn(...)`, pour deux raisons. C'est l'étape exacte qui
        // lève, donc le cas désigne la cause et non son symptôme. Et un `clickOn(noeud)` ici serait
        // compté par le cliquet de l'ADR 5068 : ce lot en retirerait un site et en ajouterait un
        // autre, laissant le compte immobile - un dispositif qui ne bouge pas se lit « rien n'a
        // changé », et ce lot aurait caché son propre gain.
        assertThatThrownBy(() -> robot.point(tenue).query())
                .as("situer un nœud détaché lève, et c'est ce que #5734 a vu en CI")
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("#5734 : une cible RÉSOLUE AU CLIC survit à la reconstruction")
    void une_cible_resolue_au_clic_survit(FxRobot robot) {
        carteTenue(robot);
        reconstruireLEcran();
        assertThat(clics).as("la prémisse : aucun clic n'a encore porté").isZero();

        GesteVisible.cliquerLaCible(robot, porteLeTitre("B"), CHERCHE);

        assertThat(clics)
                .as("le clic a porté sur la carte reconstruite, qu'aucune référence ne désignait plus")
                .isEqualTo(1);

        // ET IL A PORTÉ SUR LA BONNE. Compter les clics ne suffit pas : un geste qui ignorerait le
        // prédicat et cliquerait n'importe quelle carte rendrait le même compte. C'est ce que le
        // premier jet de ce cas laissait passer.
        assertThat(dernierTitre)
                .as("le prédicat vise un TITRE, pas une position dans la liste")
                .isEqualTo("B");

        assertThat(Attente.surLeFil(this::titresAffiches, "relire les titres affichés", 5_000L))
                .as("la liste reconstruite porte bien quatre cartes, dont celle qu'on visait")
                .containsExactly("A", "B", "C", "D");
    }

    private List<String> titresAffiches() {
        return liste.getChildren().stream()
                .flatMap(carte -> carte.lookupAll(".carte-titre").stream())
                .map(noeud -> ((Label) noeud).getText())
                .toList();
    }
}

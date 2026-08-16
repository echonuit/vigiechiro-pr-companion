package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.outils.LisibiliteCapture;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.diagnostic.view.NavigationDiagnostic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le fil d'Ariane **élide des segments entiers** au lieu de couper ses libellés (#3798).
///
/// ## Ce que ce garde tient, et pourquoi il peut le tenir
///
/// Avant #3798, les segments portaient la classe `abregeable`, qui fait **taire** [LisibiliteCapture].
/// Le fil pouvait donc se faire couper sans que rien ne rougisse - un silence déclaré par #3760, mais un
/// silence tout de même.
///
/// Puisque les segments ne se coupent plus, l'exemption a été retirée, et le juge qui refuse d'écrire un
/// aperçu tronqué retrouve sa juridiction sur le fil. Ce fichier vérifie les deux moitiés : qu'aucun
/// segment rendu n'est comprimé, **et** que l'exemption n'est pas revenue en douce.
///
/// ## Ce qu'il ne prouve pas
///
/// Que le choix des segments gardés est le **meilleur**. Il vérifie qu'aucun n'est coupé et qu'aucun
/// n'est perdu ; lequel mérite la place est un arbitrage, consigné dans l'ADR 3798.
@ExtendWith(ApplicationExtension.class)
class FilArianeElisionTest {

    /// Largeur d'ouverture par défaut ; la CI rejoue le même fichier à 900, la largeur minimale imposée
    /// par `TailleOuverture`, comme pour `BudgetHorizontalChromeTest`.
    private static final double LARGEUR = Double.parseDouble(System.getProperty("chrome.largeur", "1100"));

    /// L'écran le plus profond du produit : `Accueil › Mes sites › Carré 640380 › Détails du passage
    /// N° 1 › Diagnostic matériel`.
    private static final int SEGMENTS = 5;

    private Scene scene;
    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-fil-elision");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        new MigrationSchema(injector.getInstance(SourceDeDonnees.class)).migrer();
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        scene = new Scene(racine, LARGEUR, 720);
        stage.setScene(scene);
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#3798 : aucun segment rendu n'est coupé, à aucune des largeurs livrées")
    void aucun_segment_rendu_n_est_coupe(FxRobot robot) {
        ouvrirLEcranLePlusProfond(robot);

        List<Labeled> rendus = segmentsRendus();

        // Non-vacuité : un fil vide passerait au vert sans rien prouver.
        assertThat(rendus)
                .as("le fil ne rend AUCUN segment : c'est le dispositif qui est cassé, pas le produit")
                .isNotEmpty();

        assertThat(rendus)
                .allSatisfy(segment -> assertThat(segment.getWidth())
                        .as(
                                "« %s » est coupé : le fil doit élider des segments, pas rogner des libellés",
                                segment.getText())
                        .isGreaterThanOrEqualTo(Math.floor(segment.prefWidth(-1))));
    }

    @Test
    @DisplayName("#3798 : le juge de lisibilité n'est plus mis en sourdine sur le fil")
    void le_fil_ne_s_exempte_plus(FxRobot robot) {
        ouvrirLEcranLePlusProfond(robot);

        assertThat(segmentsRendus())
                .allSatisfy(segment -> assertThat(segment.getStyleClass())
                        .as(
                                "« %s » porte encore `abregeable` : le fil s'exempte du juge qui refuse"
                                        + " d'écrire un aperçu tronqué, alors qu'il n'en a plus besoin (#3798)",
                                segment.getText())
                        .doesNotContain(LisibiliteCapture.ABREGEABLE));
    }

    @Test
    @DisplayName("#3798 : un segment élidé reste atteignable, il change de forme et non d'existence")
    void aucun_segment_elide_n_est_perdu(FxRobot robot) {
        ouvrirLEcranLePlusProfond(robot);

        List<Labeled> rendus = segmentsRendus();
        MenuButton elision = menuDElision();
        int visibles = elision == null ? rendus.size() : rendus.size() - 1;
        int caches = elision == null ? 0 : elision.getItems().size();

        assertThat(visibles + caches)
                .as(
                        "largeur %s : %d segments rendus et %d dans le menu, il en manque au fil",
                        LARGEUR, visibles, caches)
                .isEqualTo(SEGMENTS);

        if (elision != null) {
            assertThat(elision.getItems())
                    .allSatisfy(entree -> assertThat(entree.getOnAction())
                            .as(
                                    "« %s » est dans le menu sans action : l'ancêtre a disparu sans recours",
                                    entree.getText())
                            .isNotNull());
        }
    }

    private void ouvrirLEcranLePlusProfond(FxRobot robot) {
        robot.interact(() -> injector.getInstance(NavigationDiagnostic.class)
                .ouvrir(new ContextePassage(999_999L, 1, new ContexteSite("640380", "A1", null))));
        robot.interact(() -> scene.getRoot().applyCss());
        robot.interact(() -> scene.getRoot().layout());
    }

    /// Les libellés du fil, séparateurs « › » exclus : eux ne portent aucune information.
    private List<Labeled> segmentsRendus() {
        return enfantsDuFil().stream()
                .filter(Labeled.class::isInstance)
                .map(Labeled.class::cast)
                .filter(noeud -> !noeud.getStyleClass().contains("fil-ariane-separateur"))
                .toList();
    }

    private MenuButton menuDElision() {
        return enfantsDuFil().stream()
                .filter(MenuButton.class::isInstance)
                .map(MenuButton.class::cast)
                .findFirst()
                .orElse(null);
    }

    private List<Node> enfantsDuFil() {
        FilAriane fil = (FilAriane) scene.lookup("#filAriane");
        return List.copyOf(fil.getChildren());
    }
}

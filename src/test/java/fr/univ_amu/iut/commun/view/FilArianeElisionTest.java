package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
import java.util.function.DoubleConsumer;
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

    /// Les **deux** largeurs que le produit livre, éprouvées par chaque test de ce fichier.
    ///
    /// Elles y étaient déjà annoncées - « la CI rejoue le même fichier à 900 » - et c'était **faux** :
    /// la largeur venait d'un `System.getProperty("chrome.largeur", "1100")` que **rien** ne posait, ni
    /// le `pom.xml`, ni un atelier, ni un script. Les deux gardes de largeur ne tournaient donc jamais
    /// qu'à 1100, c'est-à-dire jamais à la largeur où l'élision sert (#3960).
    ///
    /// Une boucle plutôt qu'une seconde exécution en intégration : un garde qui ne rougit qu'en CI ne
    /// protège pas celui qui écrit le code.
    private static final double[] LARGEURS_LIVREES = {TailleOuverture.LARGEUR_VOULUE, TailleOuverture.LARGEUR_MINIMALE};

    /// L'écran le plus profond du produit : `Accueil › Mes sites › Carré 640380 › Détails du passage
    /// N° 1 › Diagnostic matériel`.
    private static final int SEGMENTS = 5;

    private Scene scene;
    private Stage fenetre;
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
        // `Habillage` et non `new Scene` : ce fichier **mesure** des largeurs, donc son verdict dépend de
        // la police effectivement rendue (#3773). Recopier le banc voisin m'a fait hériter de sa dette :
        // lui ne mesure pas lui-même, il délègue à `LisibiliteCapture`, et le garde ne le vise pas.
        scene = Habillage.scene(racine, LARGEURS_LIVREES[0], 720);
        // Une fenêtre À SOI, et non celle du harnais. Ce banc dimensionne sa fenêtre à la main, et
        // `setWidth` fait passer un Stage en dimensionnement EXPLICITE : il cesse définitivement de
        // s'ajuster aux scènes qu'on lui pose ensuite. Le Stage du harnais TestFX est partagé par toutes
        // les classes d'un même fork - figé ici, il faisait échouer les suivantes sur des noeuds
        // « invisibles » (#4134). Reposer la largeur en sortie ne suffisait pas : la valeur revenait, le
        // dimensionnement explicite restait.
        fenetre = new Stage();
        fenetre.initOwner(stage);
        fenetre.setScene(scene);
        fenetre.show();
    }

    @AfterEach
    void nettoyerWorkspace(FxRobot robot) {
        System.clearProperty("vigiechiro.workspace");
        // La fenêtre appartient à ce banc : elle se referme avec lui. Laissée ouverte, elle resterait
        // dans les fenêtres que `lookup` parcourt, et les classes suivantes y trouveraient des noeuds.
        robot.interact(fenetre::close);
    }

    @Test
    @DisplayName("#3798 : aucun segment rendu n'est coupé, à aucune des largeurs livrées")
    void aucun_segment_rendu_n_est_coupe(FxRobot robot) {
        ouvrirLEcranLePlusProfond(robot);

        aChaqueLargeurLivree(robot, largeur -> {
            List<Labeled> rendus = segmentsRendus();

            // Non-vacuité : un fil vide passerait au vert sans rien prouver.
            assertThat(rendus)
                    .as("largeur %s : le fil ne rend AUCUN segment, c'est le dispositif qui est cassé", largeur)
                    .isNotEmpty();

            assertThat(rendus)
                    .allSatisfy(segment -> assertThat(segment.getWidth())
                            .as(
                                    "largeur %s : « %s » est coupé, le fil doit élider des segments et non"
                                            + " rogner des libellés",
                                    largeur, segment.getText())
                            .isGreaterThanOrEqualTo(Math.floor(segment.prefWidth(-1))));
        });
    }

    @Test
    @DisplayName("#3798 : le juge de lisibilité n'est plus mis en sourdine sur le fil")
    void le_fil_ne_s_exempte_plus(FxRobot robot) {
        ouvrirLEcranLePlusProfond(robot);

        aChaqueLargeurLivree(
                robot,
                largeur -> assertThat(segmentsRendus())
                        .allSatisfy(segment -> assertThat(segment.getStyleClass())
                                .as(
                                        "largeur %s : « %s » porte encore `abregeable`, le fil s'exempte du juge qui"
                                                + " refuse d'écrire un aperçu tronqué alors qu'il n'en a plus besoin",
                                        largeur, segment.getText())
                                .doesNotContain(LisibiliteCapture.ABREGEABLE)));
    }

    @Test
    @DisplayName("#3798 : un segment élidé reste atteignable, il change de forme et non d'existence")
    void aucun_segment_elide_n_est_perdu(FxRobot robot) {
        ouvrirLEcranLePlusProfond(robot);

        aChaqueLargeurLivree(robot, largeur -> {
            List<Labeled> rendus = segmentsRendus();
            MenuButton elision = menuDElision();
            int visibles = elision == null ? rendus.size() : rendus.size() - 1;
            int caches = elision == null ? 0 : elision.getItems().size();

            assertThat(visibles + caches)
                    .as(
                            "largeur %s : %d segments rendus et %d dans le menu, il en manque au fil",
                            largeur, visibles, caches)
                    .isEqualTo(SEGMENTS);

            if (elision != null) {
                assertThat(elision.getItems())
                        .allSatisfy(entree -> assertThat(entree.getOnAction())
                                .as(
                                        "largeur %s : « %s » est dans le menu sans action, l'ancêtre a disparu"
                                                + " sans recours",
                                        largeur, entree.getText())
                                .isNotNull());
            }
        });
    }

    /// Rejoue `verification` **à chaque largeur livrée**, et vérifie d'abord que la scène l'a
    /// réellement atteinte.
    ///
    /// Ce contrôle-là n'est pas décoratif. Une fenêtre rabattue par la plateforme rendrait la boucle
    /// muette sur le cas même qu'elle vise, et le garde annoncerait deux largeurs en n'en éprouvant
    /// qu'une - exactement le défaut que #3960 vient de corriger.
    private void aChaqueLargeurLivree(FxRobot robot, DoubleConsumer verification) {
        for (double largeur : LARGEURS_LIVREES) {
            robot.interact(() -> fenetre.setWidth(largeur));
            robot.interact(() -> {
                scene.getRoot().applyCss();
                scene.getRoot().layout();
            });

            assertThat(scene.getWidth())
                    .as("la scène n'a pas atteint %s : la boucle serait muette sur cette largeur", largeur)
                    .isEqualTo(largeur, within(1.0));

            verification.accept(largeur);
        }
    }

    private void ouvrirLEcranLePlusProfond(FxRobot robot) {
        robot.interact(() -> injector.getInstance(NavigationDiagnostic.class)
                .ouvrir(new ContextePassage(999_999L, 1, new ContexteSite("640380", "A1", null))));
        robot.interact(() -> scene.getRoot().applyCss());
        robot.interact(() -> scene.getRoot().layout());
    }

    /// Les libellés **rendus** du fil, séparateurs « › » exclus : eux ne portent aucune information.
    ///
    /// Le filtre sur `isManaged` n'est pas décoratif : les segments élidés restent enfants du fil et
    /// ne sont que démanagés. Les compter reviendrait à déclarer lisible ce qui ne s'affiche pas.
    private List<Labeled> segmentsRendus() {
        return enfantsDuFil().stream()
                .filter(Node::isManaged)
                .filter(Labeled.class::isInstance)
                .map(Labeled.class::cast)
                .filter(noeud -> !noeud.getStyleClass().contains("fil-ariane-separateur"))
                .toList();
    }

    private MenuButton menuDElision() {
        return enfantsDuFil().stream()
                .filter(Node::isManaged)
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

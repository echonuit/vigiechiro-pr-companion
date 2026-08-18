package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.outils.LisibiliteCapture;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.diagnostic.view.NavigationDiagnostic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.DoubleConsumer;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le chrome tient son **budget horizontal** aux deux largeurs qu'il livre (#3760, #3743).
///
/// `TailleOuverture` ouvre l'application à **1100** et lui interdit de descendre sous **900**. Les deux
/// barres du chrome doivent rester lisibles entre ces bornes, sur l'écran **le plus profond** du produit
/// (fil d'Ariane à cinq segments) et avec ses trois zones de statut remplies.
///
/// Le juge est [LisibiliteCapture], celui-là même qui refuse d'écrire un aperçu tronqué : il connaît les
/// trois modes de troncature, dont l'**invite** d'un champ de saisie, qui se coupe sans ellipse et ne
/// s'avoue donc pas.
///
/// ## Ce que ce garde ne dit pas
///
/// Il se tait sur les contrôles portant la classe `abregeable` - ici les segments du fil d'Ariane, qui
/// portent délibérément le déficit sous 1100. Ce silence est un **choix déclaré**, pas une victoire : à
/// 900, le fil rend 188 px et s'abrège. Ce que le garde tient, c'est que **rien d'autre** ne cède, et en
/// particulier ni le titre de l'application ni le bouton ← Retour.
@ExtendWith(ApplicationExtension.class)
class BudgetHorizontalChromeTest {

    /// Les **deux** largeurs livrées : celle d'ouverture, et la minimale imposée par `TailleOuverture`.
    ///
    /// ⚠️ Le nom de ce test annonçait « à 1100 comme à 900 » depuis #3760, et c'était **faux** : la
    /// largeur venait d'un `System.getProperty("chrome.largeur", "1100")` que **rien** ne posait. Le
    /// garde ne s'est jamais exécuté à 900, c'est-à-dire jamais au cas qu'il nomme (#3960).
    private static final double[] LARGEURS_LIVREES = {TailleOuverture.LARGEUR_VOULUE, TailleOuverture.LARGEUR_MINIMALE};

    /// L'état vivant le plus long que la barre de statut sache produire : l'espace disque insuffisant,
    /// dans sa forme courte (#3743).
    ///
    /// ⚠️ **Ce garde tient la mise en page, pas la copie.** La chaîne est écrite ici : rallonger le
    /// message du produit ne le fera pas rougir. C'est le test d'intégration de M-Lot
    /// (`zone_droite_annonce_l_espace_insuffisant`) qui garde ce que l'écran met vraiment dans la
    /// zone. Les deux vont ensemble et aucun ne remplace l'autre - vérifié en injectant ici la forme
    /// longue : `piedCentre` tronqué de 80 px, rouge.
    private static final String DROITE_LA_PLUS_LONGUE = "Espace insuffisant : 9,0 Go requis, 5,0 Go libres";

    private Scene scene;
    private Stage fenetre;
    private Injector injector;
    private NavigationViewModel navigation;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-budget-chrome");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        new MigrationSchema(injector.getInstance(SourceDeDonnees.class)).migrer();
        navigation = injector.getInstance(NavigationViewModel.class);
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        scene = new Scene(racine, LARGEURS_LIVREES[0], 720);
        fenetre = stage;
        stage.setScene(scene);
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#3760 : à 1100 comme à 900, le chrome reste lisible sur l'écran le plus profond")
    void le_chrome_tient_son_budget(FxRobot robot) {
        // Cinq segments : « Accueil › Mes sites › Carré 640380 › Détails du passage N° 1 › Diagnostic ».
        // Le passage n'existe pas : l'écran rend son bandeau d'erreur, et le fil est celui qu'on veut.
        robot.interact(() -> injector.getInstance(NavigationDiagnostic.class)
                .ouvrir(new ContextePassage(999_999L, 1, new ContexteSite("640380", "A1", null))));
        aChaqueLargeurLivree(
                robot,
                largeur -> assertThatCode(() -> LisibiliteCapture.refuserToutTexteIllisible(scene))
                        .as("largeur %s : un libellé de la barre du haut est élidé sans l'avoir déclaré", largeur)
                        .doesNotThrowAnyException());
    }

    @Test
    @DisplayName("#3743 : la zone droite la plus longue ne mange pas la zone centre")
    void le_pied_tient_son_budget(FxRobot robot) {
        // Sans navigation, et c'est nécessaire : dès qu'un écran est empilé, le chrome **lie**
        // `zonesStatut` à son `ResumeStatut`, et une valeur liée ne se pose plus. On garde donc la barre
        // libre pour y placer le pire cas que le produit sache produire.
        robot.interact(() -> navigation.setZonesStatut(new ZonesStatut(
                "Carré 640380 · A1 · N° 2", "Prêt à déposer · 2 séquences · 8 Ko", DROITE_LA_PLUS_LONGUE)));
        aChaqueLargeurLivree(
                robot,
                largeur -> assertThatCode(() -> LisibiliteCapture.refuserToutTexteIllisible(scene))
                        .as("largeur %s : une zone du pied est élidée sans l'avoir déclaré", largeur)
                        .doesNotThrowAnyException());
    }

    /// Rejoue `verification` **à chaque largeur livrée**, en vérifiant d'abord que la scène l'a
    /// atteinte : une fenêtre rabattue rendrait la boucle muette sur le cas qu'elle vise.
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
}

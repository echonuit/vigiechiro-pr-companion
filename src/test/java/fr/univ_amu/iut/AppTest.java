package fr.univ_amu.iut;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.TailleOuverture;
import java.nio.file.Files;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Smoke test JavaFX du bootstrap : vérifie que le chrome principal (`MainView`) est chargé via
/// le `FXMLLoader` + la `controllerFactory` Guice, et que la barre de navigation affiche bien le
/// titre de l'application. Tourne en headless via la Headless Platform JavaFX 26
/// (glass.platform=Headless), sans fenêtre ni serveur d'affichage, localement comme en CI.
@ExtendWith(ApplicationExtension.class)
class AppTest {

    private Stage stage;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        // Workspace JETABLE, comme les 108 autres classes de test. Sans lui, ce test-ci écrivait dans
        // `~/Documents/VigieChiro-Companion` - le VRAI dossier de l'utilisateur - et se heurtait au verrou
        // exclusif (#2731) dès qu'une autre session travaillait sur la machine. Le symptôme était un
        // blocage muet du démarrage, sans rapport apparent avec ce qu'on testait.
        System.setProperty(
                "vigiechiro.workspace", Files.createTempDirectory("vc-app").toString());
        stage.setScene(null); // évite la fuite de Scene entre tests (TestFX réutilise le Stage)
        new App().start(stage);
        reposerALaTailleDOuverture();
    }

    /// Repose la fenêtre à la taille où l'application s'ouvre, avant que quoi que ce soit ne se mesure.
    ///
    /// ⚠️ Le Stage primaire est **partagé par toutes les classes de test d'un même fork**, et il en
    /// garde la **taille**. Toute modale qui suit sa croissance appelle `sizeToScene()` sur la fenêtre
    /// qui la porte : en test, c'est celle-ci, et elle en ressort à la taille d'une modale. Or
    /// [App#start] dimensionne la **scène** et compte sur l'ajustement automatique, qu'un Stage déjà
    /// dimensionné n'écoute plus : la scène d'accueil est alors comprimée dans la fenêtre laissée par
    /// une autre classe.
    ///
    /// Sans ce rappel, le verdict dépend de l'**ordre d'exécution** : vert quand cette classe passe la
    /// première dans son fork, rouge dès qu'une classe de test s'ajoute ailleurs dans le dépôt et
    /// redistribue les forks. C'est ce qui est arrivé : #3452 a été fusionnée verte, et c'est #3453,
    /// qui ne touche pas l'accueil, qui a fait sortir le rouge.
    ///
    /// `Modales.suivreLaCroissance` connaissait déjà ce canal - son doc-comment le décrit pour une
    /// version antérieure qui figeait le Stage. `sizeToScene()` a réglé le figeage, pas le **partage**.
    private void reposerALaTailleDOuverture() {
        Rectangle2D ecran = Screen.getPrimary().getVisualBounds();
        TailleOuverture ouverture = TailleOuverture.bornee(ecran.getWidth(), ecran.getHeight());
        stage.setWidth(ouverture.largeur());
        stage.setHeight(ouverture.hauteur());
    }

    @AfterEach
    void nettoyerWorkspace(FxRobot robot) {
        System.clearProperty("vigiechiro.workspace");
        // ⚠️ TestFX RÉUTILISE le Stage primaire d'une classe de test à l'autre, dans le même fork. Les
        // tailles minimales posées par App.start (#3452) y resteraient donc collées, et la modale de la
        // classe suivante hériterait d'un plancher qui l'empêche de grandir : son test de croissance
        // échouait sur « 600 n'est pas supérieur à 600 ».
        //
        // Le fichier connaissait déjà ce canal de fuite - il remet la scène à null juste au-dessus. La
        // contrainte de taille passait par le même, et rien ne la relâchait.
        robot.interact(() -> {
            stage.setMinWidth(0);
            stage.setMinHeight(0);
        });
    }

    @Test
    void le_chrome_principal_est_affiche(FxRobot robot) {
        Label titre = robot.lookup("#titreApplication").queryAs(Label.class);
        assertThat(titre).isNotNull();
        assertThat(titre.getText()).isEqualTo("VigieChiro Companion");
    }

    @Test
    @DisplayName("#3452 : l'application pose le plancher en deçà duquel elle est inutilisable")
    void l_application_pose_les_tailles_minimales() {
        // ⚠️ Ce test ne prouve PAS que la fenêtre s'ouvre à la taille voulue, et aucun test de cette
        // classe ne le peut : une scène attachée à un Stage déjà affiché prend la taille du Stage, si
        // bien qu'il ne reste aucune trace lisible de celle qu'on lui avait demandée. Remplacer le
        // calcul de [App#start] par deux littéraux ne fait rougir personne ici - mesuré, pas supposé.
        // Ce qui reste couvert : le calcul lui-même par `TailleOuvertureTest`, et le fait que cette
        // taille SUFFIT à l'accueil par le test suivant.
        assertThat(stage.getMinWidth()).isEqualTo(TailleOuverture.LARGEUR_MINIMALE);
        assertThat(stage.getMinHeight()).isEqualTo(TailleOuverture.HAUTEUR_MINIMALE);
    }

    @Test
    @DisplayName("#3452 : à la taille d'ouverture, l'accueil tient dans la fenêtre")
    void l_accueil_tient_dans_la_fenetre_d_ouverture(FxRobot robot) {
        ScrollPane defilement = robot.lookup(".defilement-central").queryAs(ScrollPane.class);

        double contenu = defilement.getContent().getBoundsInLocal().getHeight();
        double champ = defilement.getViewportBounds().getHeight();

        // Mesuré avant correctif : la fenêtre s'ouvrait à 960x640, l'accueil demandait 816 px de contenu
        // pour 586 disponibles. Les 230 px manquants étaient exactement les deux cartes du bas - « Ma
        // saison » et « Audit de cohérence » d'un côté, « Sons & validation » de l'autre.
        assertThat(champ)
                .as("l'accueil ne doit pas ouvrir sur des activités coupées : %.0f px de contenu", contenu)
                .isGreaterThanOrEqualTo(contenu);
    }
}

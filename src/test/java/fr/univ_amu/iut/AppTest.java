package fr.univ_amu.iut;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.TailleOuverture;
import fr.univ_amu.iut.recette.Attente;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Smoke test JavaFX du bootstrap : vérifie que le chrome principal (`MainView`) est chargé via
/// le `FXMLLoader` + la `controllerFactory` Guice, et que la barre de navigation affiche bien le
/// titre de l'application. Tourne en headless via la Headless Platform JavaFX 26
/// (glass.platform=Headless), sans fenêtre ni serveur d'affichage, localement comme en CI.
@ExtendWith(ApplicationExtension.class)
class AppTest {

    private Stage stage;

    /// JUnit crée ce répertoire et le **supprime** en fin de test, là où
    /// `createTempDirectory` n'enlevait rien (#4876).
    @TempDir
    private Path dossierTemporaire;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        // Workspace JETABLE, comme les 108 autres classes de test. Sans lui, ce test-ci écrivait dans
        // `~/Documents/VigieChiro-Companion` - le VRAI dossier de l'utilisateur - et se heurtait au verrou
        // exclusif (#2731) dès qu'une autre session travaillait sur la machine. Le symptôme était un
        // blocage muet du démarrage, sans rapport apparent avec ce qu'on testait.
        System.setProperty("vigiechiro.workspace", dossierTemporaire.toString());
        stage.setScene(null); // évite la fuite de Scene entre tests (TestFX réutilise le Stage)
        new App().start(stage);
    }

    /// Les entrées de la mesure, portées par le message d'échec : un « 846 attendu >= 1336 » nu oblige
    /// à deviner ce qui a été mesuré.
    private record Mesure(double largeur, double hauteur, double contenu, double champ) {

        String detail() {
            return "ouverture %.0fx%.0f, contenu %.0f px, champ %.0f px".formatted(largeur, hauteur, contenu, champ);
        }
    }

    /// Met la mise en page à la taille d'ouverture **voulue** et rend le contenu et le champ visible
    /// du défilement central. Le Stage n'est pas touché.
    ///
    /// Redimensionner le Stage le figerait en dimensionnement explicite, définitivement et pour
    /// toutes les classes du même fork ; le défaut est revenu quatre fois (#1940, #1967, #4130, #4145).
    /// Voir l'[ADR
    /// 4134](../../../../../../dev-docs/decisions/4134-un-banc-n-emprunte-pas-l-etat-partage-il-ouvre-le-sien.md).
    ///
    /// La taille est celle que le produit **décide** (`TailleOuverture.LARGEUR_VOULUE`), jamais celle
    /// de l'écran du runner : lue sur l'écran, elle rendait deux verdicts opposés au même commit (#3622).
    private Mesure mesurerADimensionDOuverture(FxRobot robot) {
        TailleOuverture ouverture =
                TailleOuverture.bornee(TailleOuverture.LARGEUR_VOULUE, TailleOuverture.HAUTEUR_VOULUE);
        ScrollPane defilement = robot.lookup(".defilement-central").queryAs(ScrollPane.class);
        double[] mesures = new double[2];
        robot.interact(() -> {
            Region racine = (Region) stage.getScene().getRoot();
            racine.resize(ouverture.largeur(), ouverture.hauteur());
            racine.applyCss();
            racine.layout();
            mesures[0] = defilement.getContent().getBoundsInLocal().getHeight();
            mesures[1] = defilement.getViewportBounds().getHeight();
        });
        return new Mesure(ouverture.largeur(), ouverture.hauteur(), mesures[0], mesures[1]);
    }

    /// Rend le Stage partagé tel qu'il a été reçu : sans plancher, et à la taille de sa scène.
    ///
    /// Le `sizeToScene` est un remède **partiel** de #4785 : il ne supprime pas le rouge intermittent
    /// de [#le_stage_partage_reste_ajustable], il supprime sa **cascade**. À l'échec, la fenêtre reste
    /// à 600 quand son contenu en réclame 720, et les classes suivantes du fork en héritent ; un seul
    /// `sizeToScene` la porte à 720, ce qui prouve que rien n'est figé. Cause ouverte en #5018.
    ///
    /// L'appeler **ici** ne défait rien : l'assertion a déjà eu lieu. **Avant**, ce serait tout autre
    /// chose, et le premier jet du test l'a payé d'un mutant survivant.
    @AfterEach
    void nettoyerWorkspace(FxRobot robot) {
        System.clearProperty("vigiechiro.workspace");
        // TestFX RÉUTILISE le Stage primaire d'une classe de test à l'autre, dans le même fork. Les
        // tailles minimales posées par App.start (#3452) y resteraient donc collées, et la modale de la
        // classe suivante hériterait d'un plancher qui l'empêche de grandir : son test de croissance
        // échouait sur « 600 n'est pas supérieur à 600 ».
        //
        // Le fichier connaissait déjà ce canal de fuite - il remet la scène à null juste au-dessus. La
        // contrainte de taille passait par le même, et rien ne la relâchait.
        robot.interact(() -> {
            stage.setMinWidth(0);
            stage.setMinHeight(0);
            stage.sizeToScene();
        });
    }

    @Test
    @DisplayName("Le chrome principal est affiché, et il porte le nom du produit")
    void le_chrome_principal_est_affiche(FxRobot robot) {
        Label titre = robot.lookup("#titreApplication").queryAs(Label.class);
        assertThat(titre).isNotNull();
        assertThat(titre.getText()).isEqualTo("VigieChiro Companion");
    }

    /// Vérifie que cette classe rend le Stage partagé **tel qu'elle l'a reçu** : ajustable.
    ///
    /// La propriété se **mesure**, elle ne se lit pas dans les sources : le garde de l'[ADR
    /// 4134](../../../../../../dev-docs/decisions/4134-un-banc-n-emprunte-pas-l-etat-partage-il-ouvre-le-sien.md)
    /// attrape la forme connue, `alias.setWidth(`, et ne peut rien contre une fenêtre figée autrement.
    ///
    /// Les deux scènes **encadrent** le plancher de 600 px (#3452) et restent sous les 1 000 px de
    /// l'écran du banc : sous le plancher la mesure est aveugle, au-dessus l'échec parle d'autre chose.
    @Test
    @DisplayName("#4145 : le Stage partagé suit encore les scènes qu'on lui pose")
    void le_stage_partage_reste_ajustable(FxRobot robot) {
        // `setScene` SUFFIT, et il ne faut surtout pas appeler `sizeToScene` ici. Poser une scène sur
        // une fenêtre affichée la redimensionne - à moins qu'elle ne soit en dimensionnement explicite,
        // et c'est précisément ce qu'on mesure. `sizeToScene`, lui, DÉFIGE : le premier jet l'appelait,
        // et son mutant a survécu - le garde défaisait le défaut avant de le chercher.
        robot.interact(() -> stage.setScene(Habillage.scene(lignes(8))));
        WaitForAsyncUtils.waitForFxEvents();
        double aLOuverture = stage.getHeight();
        double contenuBas = hauteurDuContenu();

        robot.interact(() -> stage.setScene(Habillage.scene(lignes(40))));
        WaitForAsyncUtils.waitForFxEvents();
        // `waitForFxEvents` rend la main quand la file d'événements est vide, ce qui n'est pas la
        // même chose que « la mise en page de la nouvelle scène est faite ». Sous la charge des forks
        // parallèles, la lecture partait avant, et le contenu mesuré était encore celui de la petite
        // scène : la fenêtre valait 600, le contenu 600, et le garde accusait un figement qui n'avait
        // pas eu lieu (#4504).
        //
        // On attend donc que le CONTENU ait grandi, et on affirme ensuite que la FENÊTRE a suivi. Les
        // deux propriétés sont distinctes : un Stage figé laisse le contenu passer 600 sans bouger,
        // et l'assertion rougit toujours. Attendre la fenêtre, elle, serait tautologique.
        attendreLaMiseEnPage(aLOuverture);
        double contenuHaut = hauteurDuContenu();

        assertThat(stage.getHeight())
                .as(
                        "une scène trois fois plus haute est posée : un Stage ajustable la suit, un Stage"
                                + " passé en dimensionnement explicite reste où il est - et fait alors échouer"
                                + " toutes les classes qui passent après celle-ci dans le fork.%n%s",
                        geometrie(aLOuverture, contenuBas, contenuHaut))
                .isGreaterThan(aLOuverture);
    }

    /// La hauteur que le contenu de la scène courante réclame, une fois la mise en page faite.
    private double hauteurDuContenu() {
        return stage.getScene().getRoot().getBoundsInLocal().getHeight();
    }

    /// Attend que la mise en page de la scène posée ait dépassé `plancher`, et dit ce qu'elle a vu si
    /// elle expire.
    ///
    /// Le mécanisme du figement a été cherché là où il ne pouvait pas être : la suite entière jouée
    /// dans un fork unique, les 813 classes avant celle-ci, rend `AppTest` **vert**. Aucune classe du
    /// dépôt ne laisse donc le Stage en dimensionnement explicite ; c'est la mesure qui partait trop
    /// tôt.
    private void attendreLaMiseEnPage(double plancher) {
        Attente.que(
                () -> hauteurDuContenu() > plancher,
                () -> "la scène de 40 lignes n'a pas dépassé " + plancher + " : contenu "
                        + hauteurDuContenu() + ". Ce n'est pas un Stage figé, c'est une mise en page"
                        + " qui n'a pas eu lieu.",
                5_000L);
    }

    /// Ce que le rouge doit dire, et que « 600 n'est pas supérieur à 600 » ne dit pas (#4504).
    ///
    /// Deux causes produisent le même chiffre, et l'assertion seule ne les sépare pas. Le **Stage
    /// figé** : le contenu réclame sa hauteur, la fenêtre ne suit plus. La **scène qui n'a pas
    /// grandi** : le contenu lui-même reste sous le plancher de 600, et c'est alors la mesure qui est
    /// en cause, pas le voisin de fork. Sans les deux hauteurs, un journal de CI ne permet de trancher
    /// ni l'une ni l'autre, et deux diagnostics faux ont déjà été rendus sur ce défaut.
    ///
    /// Le plancher figure aussi : il vaut 600 tant que `App.start` l'a posé, et c'est lui qui remonte
    /// la petite scène à 600. Le lire évite de conclure au figement sur une fenêtre simplement bornée.
    private String geometrie(double aLOuverture, double contenuBas, double contenuHaut) {
        double fenetreAlors = stage.getHeight();
        return "  plancher %.0f | 8 lignes : fenêtre %.0f, contenu %.0f | 40 lignes : fenêtre %.0f, contenu %.0f%n"
                        .formatted(stage.getMinHeight(), aLOuverture, contenuBas, fenetreAlors, contenuHaut)
                + "  fenêtre après %d ms de plus : %.0f, lue SUR LE FIL : %.0f%n"
                        .formatted(REPRISE_MS, fenetreApresUnDelai(), hauteurSurLeFil())
                + "  scène : %.0f, minHeight %.0f, resizable %s%n"
                        .formatted(stage.getScene().getHeight(), stage.getMinHeight(), stage.isResizable())
                + "  Le contenu reste sous 600 : la scène n'a pas grandi. Le contenu dépasse et la"
                + " fenêtre ne suit pas : l'ajustement automatique de la fenêtre n'a pas eu lieu. Ce"
                + " n'est PAS un figement, mesuré en #4785 : un seul `sizeToScene` la rattrape, et le"
                + " teardown de cette classe le fait pour épargner la cascade.";
    }

    /// Le délai laissé à la fenêtre pour suivre, quand l'assertion vient de la trouver immobile.
    ///
    /// Il ne sert **qu'au rapport** : le test a déjà échoué quand on le lit. Sa seule raison d'être est
    /// de départager un figement, qui ne suit jamais, d'un retard, qui suit après coup (#4785).
    private static final long REPRISE_MS = 1_500L;

    /// La hauteur de la fenêtre lue **sur le fil JavaFX**, où elle est écrite.
    ///
    /// Une propriété JavaFX lue depuis le fil du test peut rendre une valeur périmée. Si les deux
    /// lectures diffèrent, le banc n'accuse pas un figement mais sa propre façon de lire (#4785).
    private double hauteurSurLeFil() {
        return Attente.surLeFil(stage::getHeight, "lire la hauteur du Stage sur son fil", 2_000L);
    }

    /// La hauteur de la fenêtre après [#REPRISE_MS], lue sur le fil JavaFX.
    private double fenetreApresUnDelai() {
        WaitForAsyncUtils.sleep(REPRISE_MS, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        return stage.getHeight();
    }

    /// Une racine de `combien` lignes, assez basse pour tenir sous l'écran du banc.
    private static VBox lignes(int combien) {
        VBox racine = new VBox();
        for (int i = 0; i < combien; i++) {
            racine.getChildren().add(new Label("ligne " + i));
        }
        return racine;
    }

    @Test
    @DisplayName("#3452 : l'application pose le plancher en deçà duquel elle est inutilisable")
    void l_application_pose_les_tailles_minimales() {
        // Ce test ne prouve PAS que la fenêtre s'ouvre à la taille voulue, et aucun test de cette
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
        Mesure mesure = mesurerADimensionDOuverture(robot);

        // Mesuré avant correctif : la fenêtre s'ouvrait à 960x640, l'accueil demandait 816 px de contenu
        // pour 586 disponibles. Les 230 px manquants étaient exactement les deux cartes du bas - « Ma
        // saison » et « Audit de cohérence » d'un côté, « Sons & validation » de l'autre.
        assertThat(mesure.champ())
                .as("l'accueil ne doit pas ouvrir sur des activités coupées - %s", mesure.detail())
                .isGreaterThanOrEqualTo(mesure.contenu());
    }
}

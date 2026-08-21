package fr.univ_amu.iut;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.TailleOuverture;
import java.nio.file.Files;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    }

    /// Les entrées de la mesure, portées par le message d'échec (#3622) : un « 846 attendu >= 1336 » nu
    /// oblige à deviner ce qui a été mesuré, et c'est ce qui a fait chercher ailleurs pendant des jours.
    private record Mesure(double largeur, double hauteur, double contenu, double champ) {

        String detail() {
            return "ouverture %.0fx%.0f, contenu %.0f px, champ %.0f px".formatted(largeur, hauteur, contenu, champ);
        }
    }

    /// Met la mise en page à la taille d'ouverture **sans toucher au Stage**, et rend le contenu et le
    /// champ visible du défilement central.
    ///
    /// ## Pourquoi on ne redimensionne pas la fenêtre
    ///
    /// ⚠️ `setWidth`/`setHeight` font passer un Stage en dimensionnement **explicite** : il cesse
    /// **définitivement** de s'ajuster aux scènes qu'on lui pose ensuite. Sans conséquence pour une
    /// fenêtre qu'on jette, mais le Stage du harnais TestFX est **partagé par toutes les classes d'un
    /// même fork** : figé ici, il fait échouer les suivantes sur des noeuds « invisibles », très loin
    /// de la cause et seulement selon l'ordre d'exécution.
    ///
    /// C'est exactement le défaut de #1940, que #1967 avait prédit revenir : « rien ne l'empêche […]
    /// aucun test ne rougit si on la réécrit ». Il est revenu par **ici**, en corrigeant #3452 : le
    /// garde posé alors surveille `Modales`, pas une classe de test qui fige le Stage elle-même.
    ///
    /// La mesure porte donc sur la **mise en page** : on redimensionne la racine, on force une passe,
    /// on lit. C'est ce que #3452 veut savoir - à cette taille, l'accueil coupe-t-il ses activités ? -
    /// et cela ne laisse aucune trace derrière.
    ///
    /// ## À la taille d'ouverture **voulue**, et non à celle que ce poste-ci ouvrirait (#3622)
    ///
    /// Le garde lisait `Screen.getPrimary().getVisualBounds()` puis bornait la taille dessus. Son
    /// attente dépendait donc de l'écran du **runner**, qui n'est ni le poste de développement ni la
    /// machine de l'utilisateur : deux exécutions du **même commit** ont rendu deux verdicts opposés,
    /// avec un contenu mesuré à **846 px** ici et à **1336** là - l'écart d'une grille de cartes qui se
    /// replie sur davantage de rangs faute de largeur.
    ///
    /// Ce que le garde veut dire est dans son titre : « à la taille d'ouverture ». C'est une valeur que
    /// le produit **décide** (`TailleOuverture.LARGEUR_VOULUE`), pas une que la machine lui impose.
    ///
    /// ⚠️ Ce que la borne couvrait est **perdu**, et il faut le dire : sur un écran plus petit que la
    /// taille voulue, l'application ouvre plus petit et l'accueil y est coupé. Mesuré, à 900x600 - le
    /// minimum autorisé - il ne tient pas. Ce n'est pas un défaut de ce garde-ci, c'est une question de
    /// produit, qui mérite d'être posée pour elle-même plutôt que tenue par un test dont personne ne
    /// contrôle l'entrée.
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
    @DisplayName("Le chrome principal est affiché, et il porte le nom du produit")
    void le_chrome_principal_est_affiche(FxRobot robot) {
        Label titre = robot.lookup("#titreApplication").queryAs(Label.class);
        assertThat(titre).isNotNull();
        assertThat(titre.getText()).isEqualTo("VigieChiro Companion");
    }

    /// Vérifie que cette classe rend le Stage partagé **tel qu'elle l'a reçu** : ajustable.
    ///
    /// ## Ce que ce garde remplace, et pourquoi il fallait le remplacer
    ///
    /// Ce texte-ci vivait au-dessus de `le_chrome_principal_est_affiche`, qui lit un libellé. Il
    /// annonçait donc une propriété qu'aucune assertion ne touchait : `queryAs` trouve un noeud quelle
    /// que soit sa position, et un Stage figé affiche son titre aussi bien qu'un Stage ajustable
    /// (#4145).
    ///
    /// C'est ce doc-comment qui a fait croire que le trou de #1967 était bouché. Il ne l'était pas : le
    /// défaut est revenu une **quatrième** fois par #4130, et c'est `ordre-alternatif` qui l'a signalé.
    ///
    /// ## Pourquoi une mesure, quand #4134 a déjà posé un garde
    ///
    /// #4134 lit les **sources** : aucune classe de test ne fige un Stage qu'elle a reçu. Il attrape la
    /// forme connue du défaut, `alias.setWidth(`. Il ne peut pas attraper une fenêtre figée par un autre
    /// chemin. La propriété se **mesure** : on pose une scène nettement plus haute, et la fenêtre doit
    /// suivre.
    ///
    /// ## Les deux tailles ne sont pas prises au hasard
    ///
    /// `App.start` pose un **plancher** de 600 px de haut (#3452). Mesuré : une scène de huit lignes
    /// laisse la fenêtre à 600 - le plancher, pas la scène - et une scène de quarante la porte à 720.
    /// Le premier jet visait 8 puis 24 lignes : les deux tenaient **sous** le plancher, la fenêtre
    /// restait à 600 dans les deux cas, et le garde rougissait sur du code sain.
    ///
    /// La seconde scène franchit donc le plancher. C'est ce qui rend la mesure lisible **sans** toucher
    /// à la configuration réelle de l'application : relâcher le plancher pour mesurer aurait éprouvé un
    /// Stage que le produit ne pose jamais.
    ///
    /// ⚠️ Les deux scènes restent **sous les 1000 px** de l'écran du banc headless : au-delà, le `blit`
    /// déborde et l'échec parle d'autre chose que du Stage.
    @Test
    @DisplayName("#4145 : le Stage partagé suit encore les scènes qu'on lui pose")
    void le_stage_partage_reste_ajustable(FxRobot robot) {
        // ⚠️ `setScene` SUFFIT, et il ne faut surtout pas appeler `sizeToScene` ici. Poser une scène sur
        // une fenêtre affichée la redimensionne - à moins qu'elle ne soit en dimensionnement explicite,
        // et c'est précisément ce qu'on mesure. `sizeToScene`, lui, DÉFIGE : le premier jet l'appelait,
        // et son mutant a survécu - le garde défaisait le défaut avant de le chercher.
        robot.interact(() -> stage.setScene(Habillage.scene(lignes(8))));
        WaitForAsyncUtils.waitForFxEvents();
        double aLOuverture = stage.getHeight();

        robot.interact(() -> stage.setScene(Habillage.scene(lignes(40))));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(stage.getHeight())
                .as("une scène trois fois plus haute est posée : un Stage ajustable la suit, un Stage"
                        + " passé en dimensionnement explicite reste où il est - et fait alors échouer"
                        + " toutes les classes qui passent après celle-ci dans le fork")
                .isGreaterThan(aLOuverture);
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
        Mesure mesure = mesurerADimensionDOuverture(robot);

        // Mesuré avant correctif : la fenêtre s'ouvrait à 960x640, l'accueil demandait 816 px de contenu
        // pour 586 disponibles. Les 230 px manquants étaient exactement les deux cartes du bas - « Ma
        // saison » et « Audit de cohérence » d'un côté, « Sons & validation » de l'autre.
        assertThat(mesure.champ())
                .as("l'accueil ne doit pas ouvrir sur des activités coupées - %s", mesure.detail())
                .isGreaterThanOrEqualTo(mesure.contenu());
    }
}

package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX du chrome (`MainView`) : la barre de navigation (← Retour + fil d'Ariane,
/// portés par le chrome donc présents sur tous les écrans) reflète l'historique et l'emplacement,
/// permet de revenir à l'écran précédent réel et de sauter à un ancêtre du fil, et respecte le verrou
/// (#54). Couvre #22 et #140.
@ExtendWith(ApplicationExtension.class)
class MainViewTest {

    private Injector injector;
    private SourceDeDonnees source;
    private Navigateur navigateur;
    private NavigationViewModel navigation;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-main");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        navigateur = injector.getInstance(Navigateur.class);
        navigation = injector.getInstance(NavigationViewModel.class);
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1000, 700));
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Le ← Retour est masqué sur l'accueil et apparaît dès qu'on entre dans une feature")
    void retour_masque_sur_accueil(FxRobot robot) {
        Button retour = robot.lookup("#boutonRetour").queryAs(Button.class);
        assertThat(retour.isVisible()).isFalse();

        robot.interact(() -> navigateur.afficher(new Group(), "sites", "Mes sites"));

        assertThat(retour.isVisible()).isTrue();
    }

    @Test
    @DisplayName("← Retour revient à l'écran précédent réel, puis à l'accueil (sans détour)")
    void retour_revient_a_l_ecran_precedent(FxRobot robot) {
        robot.interact(() -> {
            navigateur.afficher(new Group(), "sites", "Mes sites");
            navigateur.afficher(new Group(), "site-detail", "Carré 640380");
        });
        Button retour = robot.lookup("#boutonRetour").queryAs(Button.class);

        robot.interact(retour::fire);
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("sites");

        robot.interact(retour::fire);
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("accueil");
        assertThat(retour.isVisible()).isFalse();
        assertThat(robot.lookup("#cartesActivites").tryQuery()).isPresent();
    }

    @Test
    @DisplayName("Le fil d'Ariane reflète le parcours ; cliquer un ancêtre y ramène")
    void fil_ariane_reflete_le_parcours(FxRobot robot) {
        robot.interact(() -> {
            navigateur.afficher(new Group(), "sites", "Mes sites");
            navigateur.afficher(new Group(), "site-detail", "Carré 640380");
        });

        // Fil = Accueil › Mes sites › Carré 640380 (segments dans l'ordre, dernier non cliquable).
        HBox fil = robot.lookup("#filAriane").queryAs(HBox.class);
        var libelles = fil.getChildren().stream()
                .filter(n -> n.getStyleClass().contains("fil-ariane-segment")
                        || n.getStyleClass().contains("fil-ariane-courant"))
                .map(n -> ((Labeled) n).getText())
                .toList();
        assertThat(libelles).containsExactly("Accueil", "Mes sites", "Carré 640380");

        Hyperlink mesSites = fil.getChildren().stream()
                .filter(n -> n instanceof Hyperlink h && "Mes sites".equals(h.getText()))
                .map(Hyperlink.class::cast)
                .findFirst()
                .orElseThrow();
        robot.interact(mesSites::fire);

        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("sites");
    }

    @Test
    @DisplayName("Raccourcis clavier : Alt+← (retour) et Alt+Début (accueil) sont actifs sur le chrome")
    void raccourcis_clavier_navigation(FxRobot robot) {
        var altGauche = new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN);
        var altDebut = new KeyCodeCombination(KeyCode.HOME, KeyCombination.ALT_DOWN);
        Scene scene = robot.lookup("#boutonRetour").queryAs(Button.class).getScene();

        // Les deux raccourcis de navigation sont enregistrés sur la scène du chrome.
        assertThat(scene.getAccelerators()).containsKeys(altGauche, altDebut);

        // Navigation profonde : Accueil › Mes sites › Carré 640380.
        robot.interact(() -> {
            navigateur.afficher(new Group(), "sites", "Mes sites");
            navigateur.afficher(new Group(), "site-detail", "Carré 640380");
        });

        // Alt+← est bien câblé au RETOUR (écran précédent réel = sites), pas au saut à l'accueil.
        robot.interact(() -> scene.getAccelerators().get(altGauche).run());
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("sites");

        // Alt+Début saute directement à l'accueil depuis n'importe quel écran.
        robot.interact(() -> scene.getAccelerators().get(altDebut).run());
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("accueil");
        assertThat(robot.lookup("#boutonRetour").queryAs(Button.class).isVisible())
                .isFalse();
    }

    @Test
    @DisplayName("#54 : le ← Retour est grisé quand la navigation est verrouillée")
    void retour_grise_si_navigation_verrouillee(FxRobot robot) {
        robot.interact(() -> navigateur.afficher(new Group(), "import", "Importer une nuit"));
        Button retour = robot.lookup("#boutonRetour").queryAs(Button.class);
        assertThat(retour.isDisabled()).isFalse();

        robot.interact(() -> navigation.setNavigationVerrouillee(true));
        assertThat(retour.isDisabled()).isTrue();

        robot.interact(() -> navigation.setNavigationVerrouillee(false));
        assertThat(retour.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Tableau de bord : le bandeau de compteurs est masqué quand la base est vide (#141)")
    void bandeau_masque_si_base_vide(FxRobot robot) {
        FlowPane bandeau = robot.lookup("#bandeauIndicateurs").queryAs(FlowPane.class);
        assertThat(bandeau.isVisible()).isFalse();
        assertThat(bandeau.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("Tableau de bord : le bandeau affiche les compteurs après un retour sur l'accueil (#141)")
    void bandeau_affiche_compteurs_apres_donnees(FxRobot robot) {
        robot.interact(() -> {
            new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
            injector.getInstance(ServiceSites.class)
                    .creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, "u-1");
        });
        // On quitte l'accueil puis on y revient : le retour déclenche le recalcul des compteurs.
        robot.interact(() -> navigateur.afficher(new Group(), "sites", "Mes sites"));
        robot.interact(navigateur::afficherAccueil);

        FlowPane bandeau = robot.lookup("#bandeauIndicateurs").queryAs(FlowPane.class);
        assertThat(bandeau.isVisible()).isTrue();
        assertThat(robot.lookup(".indicateur-libelle").queryAllAs(Label.class))
                .extracting(Label::getText)
                .contains("Sites");
    }

    @Test
    @DisplayName("L'accueil affiche le hero nocturne et une carte (chip + chevron) par activité")
    void accueil_affiche_hero_et_cartes(FxRobot robot) {
        assertThat(robot.lookup(".hero-nocturne").tryQuery()).isPresent();

        int cartes = robot.lookup(".carte-activite").queryAll().size();
        assertThat(cartes).isPositive();
        // Chaque carte porte exactement une pastille d'icône et un chevron d'invite.
        assertThat(robot.lookup(".carte-chip").queryAll()).hasSize(cartes);
        assertThat(robot.lookup(".carte-chevron").queryAll()).hasSize(cartes);
    }

    @Test
    @DisplayName("Tableau de bord : un compteur à zéro est atténué (classe indicateur-vide) (#141)")
    void compteur_a_zero_est_attenue(FxRobot robot) {
        robot.interact(() -> {
            new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
            injector.getInstance(ServiceSites.class)
                    .creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, "u-1");
        });
        robot.interact(() -> navigateur.afficher(new Group(), "sites", "Mes sites"));
        robot.interact(navigateur::afficherAccueil);

        // Sites = 1, mais Points / Passages / Observations restent à 0 : ces pastilles sont atténuées.
        assertThat(robot.lookup(".indicateur-vide").queryAll()).isNotEmpty();
    }
}

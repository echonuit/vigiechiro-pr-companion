package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.PreferenceSourceEspece;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
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
    @DisplayName("#849 : basculer « Fiches espèces sur Wikipédia » mémorise la préférence de source")
    void bascule_source_fiches_memorise_la_preference(FxRobot robot) {
        PreferenceSourceEspece preference = injector.getInstance(PreferenceSourceEspece.class);
        assertThat(preference.prefereWikipedia()).as("défaut : GBIF").isFalse();

        MenuButton menu = robot.lookup("#menuOutils").queryAs(MenuButton.class);
        CheckMenuItem item = menu.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(item.isSelected()).as("case décochée au départ (GBIF)").isFalse();

        robot.interact(() -> item.setSelected(true));

        assertThat(preference.prefereWikipedia())
                .as("le choix Wikipédia est persisté")
                .isTrue();
    }

    @Test
    @DisplayName("L'accueil regroupe les cartes en deux sections de prismes (Collecte / Biodiversité)")
    void accueil_regroupe_en_deux_prismes(FxRobot robot) {
        FlowPane sections = robot.lookup("#cartesActivites").queryAs(FlowPane.class);
        assertThat(sections.getChildren()).as("une section par prisme").hasSize(2);
        assertThat(robot.lookup(".section-prisme-titre").queryAll())
                .extracting(noeud -> ((Label) noeud).getText())
                .containsExactlyInAnyOrder("Collecte & passages", "Espèces & biodiversité");
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
    @DisplayName("#144 : Ctrl+F est actif sur le chrome et donne le focus au champ de recherche")
    void ctrl_f_active_la_recherche(FxRobot robot) {
        var ctrlF = new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN);
        Scene scene = robot.lookup("#champRecherche").queryAs(TextField.class).getScene();
        assertThat(scene.getAccelerators()).containsKey(ctrlF);

        robot.interact(() -> scene.getAccelerators().get(ctrlF).run());

        assertThat(robot.lookup("#champRecherche").queryAs(TextField.class).isFocused())
                .isTrue();
    }

    @Test
    @DisplayName("#144 : saisir un n° de carré liste le site puis Entrée navigue (liste fermée, champ vidé)")
    void recherche_globale_liste_et_navigue(FxRobot robot) {
        // L'utilisateur courant (idUtilisateurCourant) est auto-créé au démarrage ; on seede le site
        // sous SON identité, car la recherche filtre par utilisateur courant.
        String utilisateur = injector.getInstance(Key.get(String.class, Names.named("idUtilisateurCourant")));
        robot.interact(() -> injector.getInstance(ServiceSites.class)
                .creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, utilisateur));

        TextField champ = robot.lookup("#champRecherche").queryAs(TextField.class);
        VBox panneau = robot.lookup("#panneauResultats").queryAs(VBox.class);
        @SuppressWarnings("unchecked")
        ListView<Object> liste =
                (ListView<Object>) robot.lookup("#listeResultats").queryAs(ListView.class);

        robot.interact(() -> champ.setText("640380"));
        attendreRecherche(); // laisse passer l'anti-rebond (#314 P3)
        assertThat(liste.getItems())
                .as("le site correspondant apparaît dans la liste")
                .isNotEmpty();
        assertThat(panneau.isVisible()).as("la liste déroulante est affichée").isTrue();

        robot.interact(() -> {
            liste.requestFocus();
            liste.getSelectionModel().select(0);
        });
        robot.type(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(navigation.vueCouranteProperty().get())
                .as("la sélection a navigué hors de l'accueil")
                .isNotEqualTo("accueil");
        assertThat(panneau.isVisible()).as("la liste se ferme après navigation").isFalse();
        assertThat(champ.getText()).as("le champ est vidé après navigation").isEmpty();
    }

    @Test
    @DisplayName("#144 : une recherche sans correspondance n'affiche pas la liste déroulante")
    void recherche_sans_resultat_garde_la_liste_fermee(FxRobot robot) {
        TextField champ = robot.lookup("#champRecherche").queryAs(TextField.class);
        VBox panneau = robot.lookup("#panneauResultats").queryAs(VBox.class);

        robot.interact(() -> champ.setText("zzz-introuvable"));
        attendreRecherche();

        assertThat(panneau.isVisible()).isFalse();
    }

    @Test
    @DisplayName("#314 P2 : après Échap, Entrée/↓ dans le champ n'agissent plus sur des résultats cachés")
    void echap_invalide_la_navigation_clavier(FxRobot robot) {
        String utilisateur = injector.getInstance(Key.get(String.class, Names.named("idUtilisateurCourant")));
        robot.interact(() -> injector.getInstance(ServiceSites.class)
                .creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, utilisateur));
        TextField champ = robot.lookup("#champRecherche").queryAs(TextField.class);
        VBox panneau = robot.lookup("#panneauResultats").queryAs(VBox.class);
        ListView<?> liste = robot.lookup("#listeResultats").queryAs(ListView.class);

        robot.interact(champ::requestFocus);
        robot.interact(() -> champ.setText("640380"));
        attendreRecherche();
        assertThat(panneau.isVisible()).isTrue();

        // Échap ferme la liste...
        robot.type(KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(panneau.isVisible()).isFalse();

        // ...et invalide la navigation clavier : Entrée ne navigue pas, ↓ ne déplace pas le focus.
        robot.type(KeyCode.ENTER);
        robot.type(KeyCode.DOWN);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(navigation.vueCouranteProperty().get())
                .as("Entrée sur une liste fermée ne doit pas naviguer")
                .isEqualTo("accueil");
        assertThat(liste.isFocused())
                .as("↓ sur une liste fermée ne doit pas y déplacer le focus")
                .isFalse();
    }

    /// Laisse passer l'anti-rebond de la recherche (#314 P3) avant d'observer les résultats.
    private static void attendreRecherche() {
        WaitForAsyncUtils.sleep(350, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
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

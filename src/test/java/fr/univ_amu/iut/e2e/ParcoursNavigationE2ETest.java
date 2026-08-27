package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.multisite.view.NavigationMultisite;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.view.NavigationSites;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Labeled;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test E2E de la **mécanique de navigation du socle** (#140), avec de **vrais écrans** : depuis
/// l'accueil, ouverture de la liste M-Sites puis du détail M-Site-detail, et vérification que :
///  - le **fil d'Ariane** du chrome reflète honnêtement le parcours (`Accueil › Mes sites › Carré N`),
///  - le **← Retour** remonte d'un cran à l'écran précédent réel (détail → liste → accueil), sans
///    jamais forcer un détour par l'accueil,
///  - cliquer un **segment** du fil remonte directement à cet ancêtre (état préservé).
@ExtendWith(ApplicationExtension.class)
class ParcoursNavigationE2ETest {

    private static final String ID_USER = "u-nav";

    private Injector injector;
    private NavigationViewModel navigation;
    private Site etang;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-nav-e2e");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        ServiceSites service = injector.getInstance(ServiceSites.class);
        etang = service.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(etang.id(), "A1", 43.5, 5.4, "Chêne");

        navigation = injector.getInstance(NavigationViewModel.class);
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        FenetreAjustable.poser(stage, racine, 1100, 720);
        FenetreAjustable.afficher(stage); // démarre sur l'accueil du chrome
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    /// Le fil **au sens de la navigation**, et non ce qui tient à l'écran : depuis #3798, un segment que
    /// la place ne permet pas d'afficher part dans le menu « … » sans quitter le fil.
    ///
    /// Ne lire que les segments rendus rendrait ces parcours dépendants de la largeur de la fenêtre :
    /// ils ont d'abord rougi ainsi, et sur **un seul** des trois jobs de la CI. Le menu occupe la place
    /// exacte des segments qu'il porte, donc les déplier là où il se trouve redonne l'ordre du fil.
    private java.util.List<String> libellesDuFil(FxRobot robot) {
        HBox fil = robot.lookup("#filAriane").queryAs(HBox.class);
        java.util.List<String> libelles = new java.util.ArrayList<>();
        for (javafx.scene.Node noeud : fil.getChildren()) {
            if (!noeud.isManaged()) {
                // Segment élidé : il reste enfant du fil, démanagé, et c'est le menu qui le porte.
                continue;
            }
            if (noeud instanceof javafx.scene.control.MenuButton menu) {
                menu.getItems().forEach(entree -> libelles.add(entree.getText()));
            } else if (noeud.getStyleClass().contains("fil-ariane-segment")
                    || noeud.getStyleClass().contains("fil-ariane-courant")) {
                libelles.add(((Labeled) noeud).getText());
            }
        }
        return libelles;
    }

    @Test
    @DisplayName("Accueil → Mes sites → détail : le fil est honnête et le ← Retour remonte sans détour")
    void fil_honnete_et_retour_multi_niveaux(FxRobot robot) {
        NavigationSites nav = injector.getInstance(NavigationSites.class);
        Button retour = robot.lookup("#boutonRetour").queryAs(Button.class);

        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("accueil");
        assertThat(retour.isVisible()).isFalse();

        robot.interact(nav::ouvrirAccueil); // liste M-Sites (vrai écran)
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("sites");
        assertThat(retour.isVisible()).isTrue();

        robot.interact(() -> nav.ouvrirDetail(etang)); // détail M-Site-detail (vrai écran)
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("site-detail");
        assertThat(libellesDuFil(robot)).containsExactly("Accueil", "Mes sites", "Carré 640380");

        robot.interact(retour::fire); // détail → liste (écran précédent réel, pas l'accueil)
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("sites");

        robot.interact(retour::fire); // liste → accueil
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("accueil");
        assertThat(retour.isVisible()).isFalse();
    }

    @Test
    @DisplayName("#1378 : « Voir sur la carte » EMPILE le contexte, le retour ramène au carré")
    void voir_sur_la_carte_empile_le_contexte(FxRobot robot) {
        NavigationSites nav = injector.getInstance(NavigationSites.class);
        Button retour = robot.lookup("#boutonRetour").queryAs(Button.class);

        robot.interact(nav::ouvrirAccueil);
        robot.interact(() -> nav.ouvrirDetail(etang));
        assertThat(libellesDuFil(robot)).containsExactly("Accueil", "Mes sites", "Carré 640380");

        // Le GESTE du constat S1-11, et non l'appel de la couche : on tire le vrai bouton de l'écran, qui
        // traverse son action, le port transversal et le chrome. Les autres cas de ce fichier passent par
        // l'objet de navigation ; celui-ci seul prouve le parcours entier.
        Button voirLaCarte = robot.lookup("#boutonVoirCarte").queryAs(Button.class);
        robot.interact(voirLaCarte::fire);

        // Le fil portait « Accueil › Carte & passages » : le chemin parcouru disparaissait, et avec lui
        // le moyen de revenir à ce qu'on regardait.
        assertThat(libellesDuFil(robot)).containsExactly("Accueil", "Mes sites", "Carré 640380", "Carte & passages");

        robot.interact(retour::fire);
        assertThat(navigation.vueCouranteProperty().get())
                .as("« venant de la vue Carré 130711, on aurait préféré que le retour aille là »")
                .isEqualTo("site-detail");
    }

    @Test
    @DisplayName("#1378 : rouvrir la carte déjà dans la pile y DÉPILE, elle ne s'y ajoute pas deux fois")
    void rouvrir_la_carte_deja_empilee_depile(FxRobot robot) {
        NavigationSites nav = injector.getInstance(NavigationSites.class);
        NavigationMultisite carte = injector.getInstance(NavigationMultisite.class);

        // La carte doit être PLUS BAS que la racine, sinon le test ne prouve rien : dépiler jusqu'à
        // l'élément 1 et repartir de zéro donnent le même fil, et la mutation survit (constaté).
        robot.interact(nav::ouvrirAccueil);
        robot.interact(() -> carte.ouvrirSurCarre("640380"));
        robot.interact(() -> nav.ouvrirDetail(etang));
        assertThat(libellesDuFil(robot)).containsExactly("Accueil", "Mes sites", "Carte & passages", "Carré 640380");

        robot.interact(() -> carte.ouvrirSurCarre("640380"));

        // C'est le cas du segment de fil du chrome audio, qui remonte vers « Carte & passages » en
        // appelant ouvrirSurCarre : l'anti-ré-entrance d'`empiler` dépile jusqu'à l'écran existant au
        // lieu d'en ajouter un second. Je l'avais AFFIRMÉ en passant à `empiler` ; ce test le vérifie.
        assertThat(libellesDuFil(robot))
                .as("la pile se raccourcit jusqu'à la carte, sans repartir de l'accueil")
                .containsExactly("Accueil", "Mes sites", "Carte & passages");
    }

    @Test
    @DisplayName("#1378 : « voir le point » et « placer le point » empilent aussi le contexte")
    void voir_et_placer_un_point_empilent_aussi(FxRobot robot) {
        NavigationSites nav = injector.getInstance(NavigationSites.class);
        NavigationMultisite carte = injector.getInstance(NavigationMultisite.class);

        robot.interact(nav::ouvrirAccueil);
        robot.interact(() -> nav.ouvrirDetail(etang));

        robot.interact(() -> carte.ouvrirSurPoint("640380", 43.4010, -1.5740));
        assertThat(libellesDuFil(robot))
                .as("« voir sur la carte » depuis un point vient du carré, comme le bouton d'en-tête")
                .containsExactly("Accueil", "Mes sites", "Carré 640380", "Carte & passages");

        robot.interact(() -> nav.ouvrirDetail(etang));
        robot.interact(() -> carte.ouvrirSurCarrePourPlacer("640380"));
        assertThat(libellesDuFil(robot))
                .as("« placer sur la carte » aussi : les trois entrées contextuelles se valent")
                .containsExactly("Accueil", "Mes sites", "Carré 640380", "Carte & passages");
    }

    @Test
    @DisplayName("#1378 : depuis l'accueil, la carte reste une RACINE")
    void carte_depuis_l_accueil_reste_une_racine(FxRobot robot) {
        NavigationSites nav = injector.getInstance(NavigationSites.class);
        NavigationMultisite carte = injector.getInstance(NavigationMultisite.class);

        // Il faut PARTIR DE LOIN, sinon le test ne prouve rien : depuis l'accueil seul, empiler et
        // repartir de zéro donnent la même pile, et la mutation survit (constaté).
        robot.interact(nav::ouvrirAccueil);
        robot.interact(() -> nav.ouvrirDetail(etang));
        assertThat(libellesDuFil(robot)).containsExactly("Accueil", "Mes sites", "Carré 640380");

        robot.interact(carte::ouvrirAccueil);

        // Le garde-fou du correctif : ouvrir la carte depuis SA PROPRE carte d'accueil n'a aucun contexte
        // à préserver. L'empiler partout ferait un fil qui ne se vide jamais.
        assertThat(libellesDuFil(robot)).containsExactly("Accueil", "Carte & passages");
    }

    @Test
    @DisplayName("Cliquer un segment du fil remonte directement à l'ancêtre (liste des sites préservée)")
    void clic_segment_du_fil_remonte_a_l_ancetre(FxRobot robot) {
        NavigationSites nav = injector.getInstance(NavigationSites.class);
        robot.interact(nav::ouvrirAccueil);
        robot.interact(() -> nav.ouvrirDetail(etang));

        HBox fil = robot.lookup("#filAriane").queryAs(HBox.class);
        Hyperlink mesSites = fil.getChildren().stream()
                .filter(n -> n instanceof Hyperlink h && "Mes sites".equals(h.getText()))
                .map(Hyperlink.class::cast)
                .findFirst()
                .orElseThrow();

        robot.interact(mesSites::fire);

        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("sites");
        assertThat(robot.lookup("#listeCartes").tryQuery()).isPresent();
    }
}

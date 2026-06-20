package fr.univ_amu.iut.e2e;

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
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.view.NavigationSites;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
///
/// Non tagué `@Tag("conformite")` : la navigation est du **socle** (`commun`), pas une feature à
/// construire par les équipes ; c'est un test de non-régression bloquant.
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
        stage.setScene(new Scene(racine, 1100, 720));
        stage.show(); // démarre sur l'accueil du chrome
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    private java.util.List<String> libellesDuFil(FxRobot robot) {
        HBox fil = robot.lookup("#filAriane").queryAs(HBox.class);
        return fil.getChildren().stream()
                .filter(n -> n.getStyleClass().contains("fil-ariane-segment")
                        || n.getStyleClass().contains("fil-ariane-courant"))
                .map(n -> ((Labeled) n).getText())
                .toList();
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

package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX du raccourci **M-Site-detail → M-Import** (#245). Sur le vrai injecteur
/// ([RacineInjecteur]) avec un site + un point seedés, on ouvre la fiche du site puis on clique le
/// bouton « Importer une nuit ». On vérifie que :
///  - l'assistant s'ouvre avec le site **déjà pré-sélectionné** dans le rattachement ;
///  - le bouton **Retour** ramène à la **fiche appelante** (et non à l'accueil) : la navigation
///    contextuelle **empile** sur le fil au lieu de le réinitialiser.
@ExtendWith(ApplicationExtension.class)
class SiteDetailVersImportViewTest {

    private static final String ID_USER = "u-1";

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-nav-import");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-01-01", ID_USER));
        new PointDao(source).insert(new PointDEcoute(null, "A1", 43.5, 5.4, null, site.id()));

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 760));
        injector.getInstance(NavigationSites.class).ouvrirDetail(site);
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Cliquer « Importer une nuit » sur la fiche ouvre l'assistant pré-rattaché au site")
    void importer_depuis_fiche_preselectionne_le_site(FxRobot robot) {
        robot.clickOn("#boutonImporterNuit");

        ComboBox<?> comboSites = robot.lookup("#comboSites").queryAs(ComboBox.class);
        assertThat(comboSites.getValue()).isInstanceOf(Site.class);
        assertThat(((Site) comboSites.getValue()).numeroCarre()).isEqualTo("640380");
    }

    @Test
    @DisplayName("Le bouton Retour depuis l'import ramène à la fiche site appelante (historique empilé)")
    void retour_revient_a_la_fiche_site(FxRobot robot) {
        robot.clickOn("#boutonImporterNuit");
        // Sur l'assistant d'import : la fiche site (et son bouton « Importer ») ne sont plus affichées.
        assertThat(robot.lookup("#boutonImporterNuit").tryQuery()).isEmpty();

        robot.clickOn("#boutonRetour");

        // De retour sur la fiche site : le bouton « Importer » réapparaît (il n'existe que sur
        // M-Site-detail). Avec une ouverture en racine, Retour mènerait à l'accueil et le bouton
        // resterait absent.
        assertThat(robot.lookup("#boutonImporterNuit").tryQuery()).isPresent();
    }
}

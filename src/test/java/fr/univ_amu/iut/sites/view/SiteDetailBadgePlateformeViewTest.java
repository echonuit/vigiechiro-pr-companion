package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX du **badge de statut plateforme sur le détail de site** (#734).
///
/// Le site de la fixture est **enregistré mais pas verrouillé** : c'est le cas qui compte, car c'est
/// celui où l'observateur ne comprend pas pourquoi il ne peut pas déposer. Le badge doit donc être
/// présent, porter le libellé de l'état, **et** expliquer la règle dans son infobulle : *un site doit être
/// verrouillé pour qu'on puisse y déposer*.
///
/// Le badge est celui du socle (`badge` + famille de couleur sémantique), pas un style ad hoc : c'est
/// explicitement ce que demandait l'issue.
@ExtendWith(ApplicationExtension.class)
class SiteDetailBadgePlateformeViewTest {

    private static final String ID_USER = "u-1";

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-badge-plateforme");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injector = Guice.createInjector(RacineInjecteur.modules());
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-01-01", ID_USER));
        // Enregistré côté plateforme, mais PAS verrouillé : le dépôt n'y est pas encore possible.
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(
                        LienVigieChiro.ENTITE_SITE, String.valueOf(site.id()), "6a4961f587bc8dba39481180", false));

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injector));
        Parent racine = loader.load();
        FenetreAjustable.poser(stage, racine, 1100, 760);
        injector.getInstance(NavigationSites.class).ouvrirDetail(site);
        FenetreAjustable.afficher(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#734 : le détail de site porte le badge plateforme, et son infobulle dit la règle du dépôt")
    void badge_plateforme_affiche_et_explique(FxRobot robot) {
        Label badge = robot.lookup(".badge").queryAs(Label.class);

        assertThat(badge.getText())
                .as("l'état est nommé, pas laissé à deviner")
                .isEqualTo("Enregistré sur Vigie-Chiro");
        assertThat(badge.getStyleClass())
                .as("badge du socle, avec sa famille de couleur sémantique (pas de style ad hoc)")
                .contains("badge", "badge-info");
        assertThat(badge.getTooltip()).isNotNull();
        assertThat(badge.getTooltip().getText())
                .as("la règle qui compte : un site doit être VERROUILLÉ pour qu'on puisse y déposer")
                .contains("verrouillé")
                .contains("pas encore possible");
    }
}

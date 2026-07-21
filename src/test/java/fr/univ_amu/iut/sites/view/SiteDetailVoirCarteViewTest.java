package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX du badge GPS de **M-Site-detail** (#154) : le lien « ✓ GPS — voir sur la
/// carte » d'un point géolocalisé doit ouvrir LA carte multi-sites **centrée sur ce point** (contrat
/// socle [OuvrirMultisite#ouvrirSurPoint]), et non un OpenStreetMap externe. On capture l'appel via une
/// implémentation d'`OuvrirMultisite` substituée dans l'injecteur réel.
@ExtendWith(ApplicationExtension.class)
class SiteDetailVoirCarteViewTest {

    private static final String ID_USER = "u-1";

    private final AtomicReference<String> pointFocalise = new AtomicReference<>();
    private final AtomicReference<String> carrePourPlacer = new AtomicReference<>();

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-voir-carte");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injector = Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(liaison -> liaison.bind(OuvrirMultisite.class).toInstance(new OuvrirMultisite() {
                    @Override
                    public void ouvrirSurCarre(String numeroCarre) {
                        pointFocalise.set(numeroCarre);
                    }

                    @Override
                    public void ouvrirSurPoint(String numeroCarre, double latitude, double longitude) {
                        pointFocalise.set(numeroCarre + "|" + latitude + "|" + longitude);
                    }

                    @Override
                    public void ouvrirSurCarrePourPlacer(String numeroCarre) {
                        carrePourPlacer.set(numeroCarre);
                    }
                })));
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-01-01", ID_USER));
        PointDao pointDao = new PointDao(source);
        pointDao.insert(new PointDEcoute(null, "A1", 43.4031, -1.5708, null, site.id()));
        // Point SANS GPS (lat/lon nuls) : doit proposer un lien « placer sur la carte » (#…).
        pointDao.insert(new PointDEcoute(null, "B2", null, null, null, site.id()));

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
    @DisplayName("#154 : « voir sur la carte » d'un point ouvre le multi-sites centré sur ce point")
    void voir_sur_la_carte_focalise_le_point(FxRobot robot) {
        Hyperlink badge = robot.lookup(".gps-ok").queryAs(Hyperlink.class);
        assertThat(badge.getText()).contains("voir sur la carte").doesNotContain("✓");
        // #2221 : le « ✓ » d'antan est une icône, plus un caractère dans le texte.
        assertThat(badge.getGraphic()).isInstanceOf(FontIcon.class);

        robot.interact(badge::fire);

        assertThat(pointFocalise.get())
                .as("focalisation sur le carré ET les coordonnées du point")
                .isEqualTo("640380|43.4031|-1.5708");
    }

    @Test
    @DisplayName("Un point sans GPS propose « placer sur la carte » qui ouvre le carré en mode édition")
    void point_sans_gps_propose_placer_sur_la_carte(FxRobot robot) {
        Hyperlink placer = robot.lookup(".gps-manquant").queryAs(Hyperlink.class);
        assertThat(placer.getText()).contains("placer sur la carte").doesNotContain("⚠");
        // #2221 : le « ⚠ » d'antan est une icône, plus un caractère dans le texte.
        assertThat(placer.getGraphic()).isInstanceOf(FontIcon.class);

        robot.interact(placer::fire);

        assertThat(carrePourPlacer.get())
                .as("ouvre la carte sur le carré du site, pour y placer le point")
                .isEqualTo("640380");
    }

    @Test
    @DisplayName("#801 : les actions d'en-tête « Importer une nuit » et « Voir sur la carte » ont une infobulle")
    void actions_entete_ont_une_infobulle(FxRobot robot) {
        Button importer = robot.lookup("#boutonImporterNuit").queryAs(Button.class);
        Button voirCarte = robot.lookup("#boutonVoirCarte").queryAs(Button.class);

        assertThat(importer.getTooltip()).isNotNull();
        assertThat(importer.getTooltip().getText()).contains("pré-rattaché");
        assertThat(voirCarte.getTooltip()).isNotNull();
        assertThat(voirCarte.getTooltip().getText()).contains("carré");
    }
}

package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Les deux **suppressions** de la fiche site, cliquées pour de vrai (#1405) : supprimer le site (et
/// tous ses points), supprimer un point d'écoute.
///
/// Ni l'une ni l'autre n'était couverte, et pour la seconde c'était structurel : les cartes de points
/// fabriquaient leur **propre** confirmateur, que rien n'exposait. Personne ne pouvait le remplacer,
/// donc le clic finissait invariablement sur un vrai dialogue qui **fige** TestFX headless. L'écran
/// détient maintenant **une seule** paire de porteurs (confirmateur + notificateur), partagée avec ses
/// cartes : les deux gestes deviennent jouables.
///
/// Test d'intégration sur le **vrai** injecteur et une **vraie** base : ce qui est vérifié après le
/// clic, ce n'est pas qu'un mock a été appelé, c'est que la ligne a **disparu de la base** - ou qu'elle
/// y est **toujours** quand l'utilisateur a dit non.
@ExtendWith(ApplicationExtension.class)
class SiteDetailSuppressionsViewTest {

    private static final String ID_USER = "u-1";
    private static final String CARRE = "640380";
    private static final String CODE_POINT = "A1";

    /// Ce que le confirmateur a **demandé**.
    private final List<String> confirmations = new ArrayList<>();

    /// Ce que le notificateur a **dit**, au lieu de l'afficher (niveau compris).
    private final List<String> annonces = new ArrayList<>();

    /// Ce que le double de confirmation répondra : chaque test le pose avant de cliquer.
    private boolean confirme = true;

    private SourceDeDonnees source;
    private Site site;
    private Long idPoint;
    private SiteDetailController controleur;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-suppressions-site");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injector = RacineInjecteur.creer();
        source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        // Un site, un point, aucun passage : les deux suppressions sont ouvertes.
        site = new SiteDao(source)
                .insert(new Site(null, CARRE, "Étang", Protocole.STANDARD, null, "2026-01-01", ID_USER));
        idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, CODE_POINT, 43.5, 5.4, null, site.id()))
                .id();

        // Le chrome, pour que le retour à l'accueil après suppression du site soit une vraie navigation.
        FXMLLoader chrome = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        chrome.setControllerFactory(injector::getInstance);
        stage.setScene(new Scene(chrome.load(), 1100, 760));

        // On rejoue NavigationSites.ouvrirDetail(site) à la main : c'est le seul moyen de garder la main
        // sur le controller, donc de remplacer ses deux dialogues avant le premier clic.
        FXMLLoader loader = new FXMLLoader(SiteDetailController.class.getResource("SiteDetail.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.confirmateur().definir(message -> {
            confirmations.add(message);
            return confirme;
        });
        controleur
                .notificateur()
                .definir((niveau, entete, message) -> annonces.add(niveau + " | " + entete + " | " + message));
        controleur.afficher(site);
        injector.getInstance(Navigateur.class).empiler(vue, "site-detail", "Carré " + CARRE, controleur);
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    /// Rattache un passage au point : le site n'est alors plus supprimable (le service le refuse), et le
    /// lien « Supprimer » de la carte se ferme (#789). L'écran est rouvert pour refléter la nuit.
    private void rattacherUnPassage(FxRobot robot) {
        new EnregistreurDao(source).insert(new Enregistreur("1925492", "V1.01", null));
        new PassageDao(source)
                .insert(new Passage(
                        null,
                        2,
                        2026,
                        "2026-06-22",
                        "20:25:00",
                        "07:47:00",
                        null,
                        StatutWorkflow.TRANSFORME,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        "1925492"));
        robot.interact(() -> controleur.afficher(site));
    }

    private List<Site> sitesEnBase() {
        return new SiteDao(source).findByUtilisateur(ID_USER);
    }

    private List<PointDEcoute> pointsEnBase() {
        return new PointDao(source).findBySite(site.id());
    }

    /// Le lien « Supprimer » **de la carte du point** (à ne pas confondre avec le bouton « Supprimer »
    /// du site, qui porte le même libellé dans l'en-tête).
    private Hyperlink lienSupprimerPoint(FxRobot robot) {
        return robot.lookup("#cartesPoints")
                .lookup((Node noeud) ->
                        noeud instanceof Hyperlink lien && lien.getText().contains("Supprimer"))
                .queryAs(Hyperlink.class);
    }

    @Test
    @DisplayName("#1405 : « Supprimer » le site, confirmé : le site disparaît de la base")
    void suppression_du_site_confirmee(FxRobot robot) {
        robot.interact(() -> robot.lookup("#boutonSupprimer").queryButton().fire());

        assertThat(confirmations)
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .as("la confirmation dit que la suppression emporte aussi les points d'écoute")
                        .contains("ses points d'écoute"));
        assertThat(sitesEnBase())
                .as("le site a réellement été supprimé, pas seulement masqué")
                .isEmpty();
    }

    @Test
    @DisplayName("#1405 : « Supprimer » le site, refusé : le site est toujours là")
    void suppression_du_site_refusee(FxRobot robot) {
        confirme = false;

        robot.interact(() -> robot.lookup("#boutonSupprimer").queryButton().fire());

        assertThat(sitesEnBase()).hasSize(1);
        assertThat(annonces).as("un refus n'a pas à être commenté").isEmpty();
    }

    @Test
    @DisplayName("#789 : site dont un point porte un passage : « Supprimer » est fermé, et le clic ne fait rien")
    void suppression_du_site_fermee_quand_un_point_porte_un_passage(FxRobot robot) {
        rattacherUnPassage(robot);

        // Le refus n'est pas annoncé après coup : il est prévenu avant. Le bouton est fermé (#789), et
        // JavaFX n'émet aucune action sur un bouton désactivé - le clic est donc réellement sans effet.
        assertThat(robot.lookup("#boutonSupprimer").queryButton().isDisabled()).isTrue();

        robot.interact(() -> robot.lookup("#boutonSupprimer").queryButton().fire());

        assertThat(confirmations)
                .as("on ne demande pas de confirmer un geste fermé")
                .isEmpty();
        assertThat(annonces).isEmpty();
        assertThat(sitesEnBase()).hasSize(1);
    }

    @Test
    @DisplayName("#1405 : « Supprimer » un point d'écoute, confirmé : le point disparaît de la base")
    void suppression_du_point_confirmee(FxRobot robot) {
        robot.interact(() -> lienSupprimerPoint(robot).fire());

        assertThat(confirmations)
                .singleElement()
                .satisfies(message -> assertThat(message).contains(CODE_POINT));
        assertThat(pointsEnBase()).isEmpty();
    }

    @Test
    @DisplayName("#1405 : « Supprimer » un point d'écoute, refusé : le point est toujours là")
    void suppression_du_point_refusee(FxRobot robot) {
        confirme = false;

        robot.interact(() -> lienSupprimerPoint(robot).fire());

        assertThat(pointsEnBase()).hasSize(1);
        assertThat(annonces).isEmpty();
    }

    @Test
    @DisplayName("#789 : un point qui porte des passages : le lien « Supprimer » est fermé avant le clic")
    void point_avec_passages_le_lien_est_ferme(FxRobot robot) {
        rattacherUnPassage(robot);

        // L'utilisateur n'a pas à découvrir le refus après coup : le geste est fermé, et l'enveloppe
        // porte le tooltip qui explique pourquoi (#789).
        assertThat(lienSupprimerPoint(robot).isDisabled()).isTrue();
        assertThat(pointsEnBase()).hasSize(1);
    }
}

package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.outils.LisibiliteCapture;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.InfobulleDeBlocage;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointPublieDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.sites.model.dao.SiteTiersDao;
import fr.univ_amu.iut.sites.viewmodel.PublicationDepuisLaFiche;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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

/// Test d'intégration TestFX de la **mention du carré d'un tiers** (#6132), sur les deux gestes qui
/// publient un point : le lien d'une carte, et la case de la modale de création.
///
/// Trois carrés, tous ouverts par l'application complète et un jeton posé, pour que le geste soit
/// réellement offert et que la mention soit la **seule** chose qui les distingue :
///
/// | Carré | État | La carte de `A1` et la modale |
/// |---|---|---|
/// | `640380` Étang | relié, marqué « tiers » | la mention, et le geste ouvert |
/// | `640381` Lavoir | relié, sans marque | pas de mention, le geste ouvert |
/// | `640382` Bergerie | jamais relié | pas de mention, et le gris qu'il avait déjà |
///
/// Le carré du tiers porte aussi `C3`, déjà publié : sa carte n'offre plus le geste, donc pas la mention.
@ExtendWith(ApplicationExtension.class)
class PublicationSurLeCarreDUnTiersViewTest {

    private static final String ID_USER = "u-1";
    private static final String LIBELLE_ACTION = "Publier sur Vigie-Chiro";
    private static final String MENTION = PublicationDepuisLaFiche.MENTION_CARRE_D_UN_TIERS;

    private Injector injecteur;
    private Site tiers;
    private Site aSoi;
    private Site jamaisRelie;

    /// JUnit crée ce répertoire et le **supprime** en fin de test.
    @TempDir
    private Path dossierTemporaire;

    @Start
    void start(Stage stage) throws Exception {
        System.setProperty("vigiechiro.workspace", dossierTemporaire.toString());
        injecteur = Guice.createInjector(RacineInjecteur.modules());
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        long idTiers = semerPoint(source, "640380", "Étang", "A1", 43.52, 5.46).idSite();
        new PointPublieDao(source)
                .marquer(
                        semerPoint(source, "640380", "Étang", "C3", 43.53, 5.47).idPoint());
        long idASoi = semerPoint(source, "640381", "Lavoir", "A1", 43.62, 5.46).idSite();
        long idJamaisRelie =
                semerPoint(source, "640382", "Bergerie", "A1", 43.72, 5.46).idSite();
        LienVigieChiroDao liens = new LienVigieChiroDao(source);
        liens.upsert(new LienVigieChiro(
                LienVigieChiro.ENTITE_SITE, String.valueOf(idTiers), "6a4961f587bc8dba39481180", false));
        liens.upsert(new LienVigieChiro(
                LienVigieChiro.ENTITE_SITE, String.valueOf(idASoi), "6a4961f587bc8dba39481181", false));
        new SiteTiersDao(source).marquer(idTiers);
        SiteDao sites = new SiteDao(source);
        tiers = sites.findById(idTiers).orElseThrow();
        aSoi = sites.findById(idASoi).orElseThrow();
        jamaisRelie = sites.findById(idJamaisRelie).orElseThrow();
        // Connecté avant d'ouvrir : le geste est offert, et ce test ne parle que de la mention.
        injecteur
                .getInstance(StockageConnexion.class)
                .enregistrer("jeton-de-test", new ProfilVigieChiro(ID_USER, "chiro", "observateur"));

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injecteur));
        Parent racine = loader.load();
        FenetreAjustable.poser(stage, racine, 1100, 760);
        injecteur.getInstance(NavigationSites.class).ouvrirDetail(tiers);
        FenetreAjustable.afficher(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    private static JeuDeDonneesPassage semerPoint(
            SourceDeDonnees source, String carre, String nom, String code, double latitude, double longitude) {
        return JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre(carre)
                .nomSite(nom)
                .point(code)
                .position(latitude, longitude)
                .semerSiteEtPoint();
    }

    @Test
    @DisplayName("#6132 : sur le carré d'un tiers, la carte dit la mention sous un lien qui reste ouvert")
    void la_carte_d_un_carre_de_tiers_porte_la_mention(FxRobot robot) {
        Node carte = carte(robot, "A1");

        assertThat(libellesDe(carte)).contains(LIBELLE_ACTION, MENTION);
        assertThat(lienPublier(robot, "A1").isDisable())
                .as("la mention informe : elle ne grise pas le geste")
                .isFalse();
        assertThat(InfobulleDeBlocage.texteDe(lienPublier(robot, "A1").getParent()))
                .as("l'infobulle reste celle du geste possible, pas un motif de refus")
                .isEqualTo("Ajouter ce point aux localités du carré sur Vigie-Chiro.");
        Label mention = mentionDe(carte);
        assertThat(mention.localToScene(mention.getBoundsInLocal()).getMinY())
                .as("lue près du geste : juste sous la ligne d'actions de la carte")
                .isGreaterThan(lienPublier(robot, "A1")
                        .localToScene(lienPublier(robot, "A1").getBoundsInLocal())
                        .getMinY());
        assertThatCode(() -> robot.interact(() -> LisibiliteCapture.refuserToutTexteIllisible(carte.getScene())))
                .as("la mention se lit en entier sur la carte : ni coupée ni abrégée")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("#6132 : un point déjà publié ne porte pas la mention : la carte n'offre plus le geste")
    void un_point_deja_publie_ne_porte_pas_la_mention(FxRobot robot) {
        assertThat(libellesDe(carte(robot, "C3"))).doesNotContain(LIBELLE_ACTION, MENTION);
    }

    @Test
    @DisplayName("#6132 : sur un carré à soi, pas de mention, et le lien est ouvert comme avant")
    void pas_de_mention_sur_un_carre_a_soi(FxRobot robot) {
        ouvrir(robot, aSoi);

        assertThat(libellesDe(carte(robot, "A1"))).contains(LIBELLE_ACTION).doesNotContain(MENTION);
        assertThat(robot.lookup(".mention-tiers").queryAll())
                .as("aucun nœud vide ne réserve de place sur la carte")
                .isEmpty();
        assertThat(lienPublier(robot, "A1").isDisable()).isFalse();
    }

    @Test
    @DisplayName("#6132 : sur un carré jamais relié, pas de mention, et le gris est celui d'avant")
    void pas_de_mention_sur_un_carre_jamais_relie(FxRobot robot) {
        ouvrir(robot, jamaisRelie);

        assertThat(libellesDe(carte(robot, "A1"))).contains(LIBELLE_ACTION).doesNotContain(MENTION);
        assertThat(robot.lookup(".mention-tiers").queryAll()).isEmpty();
        assertThat(InfobulleDeBlocage.texteDe(lienPublier(robot, "A1").getParent()))
                .as("on ne sait pas à qui il est : l'écran ne dit que ce qu'il disait déjà")
                .contains("pas encore enregistré");
    }

    @Test
    @DisplayName("#6132 : la modale dit la mention sous la case, qui se coche sur le carré d'un tiers")
    void la_modale_d_un_carre_de_tiers_porte_la_mention(FxRobot robot) {
        ouvrirLaModale(robot);
        Label mention = robot.lookup("#mentionTiers").queryAs(Label.class);
        CheckBox publier = robot.lookup("#chkPublier").queryAs(CheckBox.class);
        Scene modale = mention.getScene();

        assertThat(mention.isVisible()).isTrue();
        assertThat(mention.isManaged()).isTrue();
        assertThat(mention.getText()).isEqualTo(MENTION);
        assertThat(mention.localToScene(mention.getBoundsInLocal()).getMinY())
                .as("sous la case, donc lue avant de cocher")
                .isGreaterThan(publier.localToScene(publier.getBoundsInLocal()).getMinY());

        robot.interact(
                () -> robot.lookup("#champPosition").queryAs(TextField.class).setText("43.5298, 5.4474"));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(publier.isDisable())
                .as("position saisie : la case s'ouvre, mention ou non")
                .isFalse();
        robot.interact(publier::fire);
        assertThat(publier.isSelected())
                .as("cocher sur le carré d'un tiers tient : rien ne décoche, rien ne confirme")
                .isTrue();
        assertThat(mention.isVisible()).isTrue();
        assertThatCode(() -> robot.interact(() -> LisibiliteCapture.refuserToutTexteIllisible(modale)))
                .as("la mention se lit en entier dans la modale")
                .doesNotThrowAnyException();
        fermer(robot, modale);
    }

    @Test
    @DisplayName("#6132 : la modale d'un carré à soi n'a pas de mention, et n'en réserve pas la place")
    void la_modale_d_un_carre_a_soi_n_a_pas_de_mention(FxRobot robot) {
        ouvrir(robot, aSoi);
        ouvrirLaModale(robot);
        Label mention = robot.lookup("#mentionTiers").queryAs(Label.class);
        CheckBox publier = robot.lookup("#chkPublier").queryAs(CheckBox.class);

        assertThat(mention.isVisible()).isFalse();
        assertThat(mention.isManaged())
                .as("non gérée : elle ne décale rien dans la modale")
                .isFalse();
        robot.interact(
                () -> robot.lookup("#champPosition").queryAs(TextField.class).setText("43.6298, 5.4474"));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(publier.isDisable()).isFalse();
        fermer(robot, mention.getScene());
    }

    private void ouvrir(FxRobot robot, Site site) {
        robot.interact(() -> injecteur.getInstance(NavigationSites.class).ouvrirDetail(site));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void ouvrirLaModale(FxRobot robot) {
        Button ajouter = robot.lookup("+ Ajouter un point").queryButton();
        robot.interact(ajouter::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void fermer(FxRobot robot, Scene modale) {
        robot.interact(() -> ((Stage) modale.getWindow()).close());
    }

    private static Label mentionDe(Node carte) {
        return carte.lookupAll(".mention-tiers").stream()
                .map(Label.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune mention sur cette carte"));
    }

    /// Le lien « Publier » de la carte de ce code. Échoue si la carte ne le porte pas.
    private Hyperlink lienPublier(FxRobot robot, String code) {
        return carte(robot, code).lookupAll(".hyperlink").stream()
                .map(Hyperlink.class::cast)
                .filter(lien -> LIBELLE_ACTION.equals(lien.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune action « " + LIBELLE_ACTION + " » sur la carte " + code));
    }

    /// La carte du point portant ce code, cherchée sur son libellé et non sur son rang.
    private Node carte(FxRobot robot, String code) {
        return robot.lookup(".carte-point").queryAll().stream()
                .map(Node.class::cast)
                .filter(carte -> libellesDe(carte).contains(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune carte de point « " + code + " » à l'écran"));
    }

    /// Les libellés portés par une carte : ce que l'utilisateur y lit réellement.
    private static Set<String> libellesDe(Node carte) {
        Set<String> textes = new LinkedHashSet<>();
        carte.lookupAll(".label").forEach(noeud -> textes.add(((Label) noeud).getText()));
        carte.lookupAll(".hyperlink").forEach(noeud -> textes.add(((Hyperlink) noeud).getText()));
        return textes;
    }
}

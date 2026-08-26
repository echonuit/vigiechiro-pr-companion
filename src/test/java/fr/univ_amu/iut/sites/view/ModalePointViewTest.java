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
import fr.univ_amu.iut.commun.view.InfobulleDeBlocage;
import fr.univ_amu.iut.recette.FenetreDuBanc;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX de la **modale d'ajout d'un point** ouverte depuis M-Site-detail :
/// ouverture du Stage modal, pilotage du bouton par la validité R2 et apparition de la carte du
/// point après enregistrement.
///
/// Les actions sont déclenchées sur le thread JavaFX (`robot.interact` + `fire()` + saisie via la
/// propriété liée) plutôt qu'avec le robot souris/clavier de l'OS, pour rester déterministe sous
/// xvfb comme sous Wayland (cf. note dans `MesSitesViewTest`).
@ExtendWith(ApplicationExtension.class)
class ModalePointViewTest {

    // Cette classe ne cite plus `S1-24`, et ce n'est pas un oubli.
    // Elle monte `ModalePoint.fxml` seule : rien ne montrait que le point venait de la modale, faute
    // de voir la fiche AVANT (#4175). Le cas est joué par `ScenarioFicheSiteTest`, depuis la fiche.
    //
    // Ses assertions restent : elles gardent le câblage, ce qui est un autre travail que de le montrer.

    private static final String ID_USER = "u-test";
    private static final String BOUTON_AJOUTER = "+ Ajouter un point";
    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-modale-point");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        ServiceSites service = injector.getInstance(ServiceSites.class);
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(site.id(), "A1", 43.5, 5.4, "Chêne");
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        // `Habillage` via `FenetreDuBanc` : ce cas est FILMÉ (#3773, #4149).
        FenetreDuBanc.poser(stage, racine, 1180, 900);
        injector.getInstance(NavigationSites.class).ouvrirDetail(site);
        FenetreDuBanc.afficher(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Un code valide active le bouton et ajoute la carte du point")
    void ajouter_un_point_valide(FxRobot robot) {
        ouvrirModale(robot);

        TextField champCode = robot.lookup("#champCode").queryAs(TextField.class);
        Respiration.avantLeGeste(robot);

        // Le code se TAPE : ce cas fait juger un bouton qui s'active à la saisie, et `setText` posait
        // le code d'un coup - le bouton changeait d'état sans qu'on voie ce qui le fait changer (#4149).
        robot.clickOn(champCode).write("B2");
        WaitForAsyncUtils.waitForFxEvents();
        Button valider = robot.lookup("#boutonValider").queryAs(Button.class);
        assertThat(valider.isDisabled()).isFalse();
        Respiration.leTempsDeLire(robot);

        robot.clickOn(valider);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);

        List<String> codes = robot.lookup(".carte-point-code").queryAllAs(Label.class).stream()
                .map(Label::getText)
                .toList();
        assertThat(codes).contains("A1", "B2");
    }

    @Test
    @DisplayName("#3458 : la case « publier » est là, GRISÉE, et dit d'aller se connecter")
    void la_case_publier_est_grisee_sans_jeton(FxRobot robot) {
        ouvrirModale(robot);

        CheckBox publier = robot.lookup("#chkPublier").queryAs(CheckBox.class);

        // L'injecteur applicatif charge `PublicationPointModule` : la publication est donc INSTALLÉE,
        // et la case existe. Ce qui manque ici est le jeton - le seul refus prévisible.
        assertThat(publier.getParent().isVisible())
                .as("la case s'affiche à la création, pour que le geste se décide au bon moment")
                .isTrue();
        assertThat(publier.isDisable()).isTrue();
        assertThat(publier.isSelected()).isFalse();
        assertThat(InfobulleDeBlocage.texteDe(publier.getParent()))
                .as("une case grisée sans motif ne dit pas ce qu'il faut corriger (#789)")
                .contains("Connectez-vous");
    }

    @Test
    @DisplayName("Un code invalide (R2) laisse le bouton de validation désactivé")
    void code_invalide_desactive_le_bouton(FxRobot robot) {
        ouvrirModale(robot);

        TextField champCode = robot.lookup("#champCode").queryAs(TextField.class);
        robot.interact(() -> champCode.setText("ZZ"));

        Button valider = robot.lookup("#boutonValider").queryAs(Button.class);
        assertThat(valider.isDisabled()).isTrue();
    }

    @Test
    @DisplayName("#153 : la carte-outil trace le carré et suit la saisie GPS (approximatif → réel)")
    void la_carte_outil_suit_la_saisie_gps(FxRobot robot) {
        ouvrirModale(robot);
        WaitForAsyncUtils.waitForFxEvents();

        // Le carré du site (640380) est tracé, et un marqueur est présent même sans GPS : il démarre au
        // centre du carré, en position approximative (anneau pointillé).
        assertThat(robot.lookup(".carte-carre").queryAll())
                .as("carré du site tracé")
                .isNotEmpty();
        assertThat(premierePastille(robot).getStrokeDashArray())
                .as("sans GPS : marqueur approximatif (pointillé), au centre du carré")
                .isNotEmpty();

        // Saisir un GPS valide (dans le carré) déplace le marqueur, qui devient une position réelle :
        // pastille pleine, sans pointillés. C'est la synchro champs → carte.
        TextField latitude = robot.lookup("#champLatitude").queryAs(TextField.class);
        TextField longitude = robot.lookup("#champLongitude").queryAs(TextField.class);
        robot.interact(() -> {
            latitude.setText("43.4031");
            longitude.setText("-1.5708");
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(premierePastille(robot).getStrokeDashArray())
                .as("GPS saisi : marqueur réel (pastille pleine, sans pointillés)")
                .isEmpty();

        // Une latitude hors bornes (200) est refusée par le formulaire ET ne doit pas être projetée :
        // le marqueur redevient approximatif (au centre du carré) plutôt qu'un point réel aberrant.
        robot.interact(() -> latitude.setText("200"));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(premierePastille(robot).getStrokeDashArray())
                .as("GPS hors bornes : pas de projection, retour au marqueur approximatif")
                .isNotEmpty();
    }

    /// Pastille (cercle) du premier marqueur de la carte-outil : enfant du groupe portant le libellé.
    private static Circle premierePastille(FxRobot robot) {
        Node libelle =
                robot.lookup(".carte-point-libelle").queryAll().iterator().next();
        Group marqueur = (Group) libelle.getParent();
        return (Circle) marqueur.getChildren().stream()
                .filter(Circle.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    private void ouvrirModale(FxRobot robot) {
        Button ajouter = robot.lookup(BOUTON_AJOUTER).queryButton();
        robot.interact(ajouter::fire);
    }

    @Test
    @DisplayName("#1970 : le grisage de « Ajouter » dit POURQUOI, et le motif suit la saisie")
    void le_grisage_dit_pourquoi(FxRobot robot) {
        ouvrirModale(robot);
        StackPane enveloppe = robot.lookup("#enveloppeValider").queryAs(StackPane.class);
        Button valider = robot.lookup("#boutonValider").queryAs(Button.class);

        assertThat(valider.isDisabled()).isTrue();
        assertThat(InfobulleDeBlocage.texteDe(enveloppe))
                .as("le message qui disait cela vivait derrière ce bouton grisé : personne ne le lisait")
                .contains("rouge");

        robot.interact(() -> robot.lookup("#champCode").queryAs(TextField.class).setText("B2"));

        assertThat(valider.isDisabled()).isFalse();
        assertThat(InfobulleDeBlocage.texteDe(enveloppe)).doesNotContain("rouge");
    }
}

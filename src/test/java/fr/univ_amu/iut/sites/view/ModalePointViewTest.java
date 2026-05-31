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
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de la **modale d'ajout d'un point** ouverte depuis M-Site-detail :
/// ouverture du Stage modal, pilotage du bouton par la validité R2 et apparition de la carte du
/// point après enregistrement.
///
/// Les actions sont déclenchées sur le thread JavaFX (`robot.interact` + `fire()` + saisie via la
/// propriété liée) plutôt qu'avec le robot souris/clavier de l'OS, pour rester déterministe sous
/// xvfb comme sous Wayland (cf. note dans `MesSitesViewTest`).
@ExtendWith(ApplicationExtension.class)
class ModalePointViewTest {

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
    stage.setScene(new Scene(racine, 1100, 720));
    injector.getInstance(NavigationSites.class).ouvrirDetail(site);
    stage.show();
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
    robot.interact(() -> champCode.setText("B2"));
    Button valider = robot.lookup("#boutonValider").queryAs(Button.class);
    assertThat(valider.isDisabled()).isFalse();

    robot.interact(valider::fire);

    List<String> codes =
        robot.lookup(".carte-point-code").queryAllAs(Label.class).stream()
            .map(Label::getText)
            .toList();
    assertThat(codes).contains("A1", "B2");
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

  private void ouvrirModale(FxRobot robot) {
    Button ajouter = robot.lookup(BOUTON_AJOUTER).queryButton();
    robot.interact(ajouter::fire);
  }
}

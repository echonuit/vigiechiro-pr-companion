package fr.univ_amu.iut.importation.view;

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
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de l'assistant **M-Import** : chargement du chrome + de la vue via
/// Guice sur une base SQLite jetable, affichage de l'écran, état initial du bouton « Importer »
/// (désactivé tant que rien n'est inspecté/rattaché) et alimentation de la combo des sites.
///
/// On ne pilote pas le `DirectoryChooser` natif (non testable headless) : la chaîne complète
/// inspection → rattachement → import est couverte en unitaire par `ImportationViewModelTest`.
@ExtendWith(ApplicationExtension.class)
class ImportationViewTest {

  private static final String ID_USER = "u-test";
  private Injector injector;

  @Start
  void start(Stage stage) throws Exception {
    Path workspace = Files.createTempDirectory("vc-import");
    System.setProperty("vigiechiro.workspace", workspace.toString());
    injector = RacineInjecteur.creer();
    SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
    new MigrationSchema(source).migrer();
    seeder(source);
    FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
    loader.setControllerFactory(injector::getInstance);
    Parent racine = loader.load();
    stage.setScene(new Scene(racine, 1100, 760));
    injector.getInstance(NavigationImportation.class).ouvrir();
    stage.show();
  }

  private void seeder(SourceDeDonnees source) {
    new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
    ServiceSites service = injector.getInstance(ServiceSites.class);
    Site etang =
        service.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
    service.ajouterPoint(etang.id(), "A1", 43.5, 5.4, "Chêne");
  }

  @AfterEach
  void nettoyerWorkspace() {
    System.clearProperty("vigiechiro.workspace");
  }

  @Test
  @DisplayName("L'assistant d'import s'affiche avec son titre")
  void affiche_le_titre(FxRobot robot) {
    Label titre = robot.lookup(".titre-page").queryAs(Label.class);

    assertThat(titre.getText()).isEqualTo("Importer une nuit d'enregistrement");
  }

  @Test
  @DisplayName("Le bouton « Importer » est désactivé tant que rien n'est inspecté ni rattaché")
  void bouton_importer_desactive_au_depart(FxRobot robot) {
    Button importer = robot.lookup("#boutonImporter").queryAs(Button.class);

    assertThat(importer.isDisabled()).isTrue();
  }

  @Test
  @DisplayName("La combo des sites est alimentée par les sites de l'utilisateur (chargerSites)")
  void combo_sites_alimentee(FxRobot robot) {
    ComboBox<?> comboSites = robot.lookup("#comboSites").queryAs(ComboBox.class);

    assertThat(comboSites.getItems()).hasSize(1);
  }
}

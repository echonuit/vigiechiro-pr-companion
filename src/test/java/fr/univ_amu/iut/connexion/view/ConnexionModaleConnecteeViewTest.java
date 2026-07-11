package fr.univ_amu.iut.connexion.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.ConnexionViewModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Affordance de la modale de connexion (#717) **à l'état connecté** : un profil est pré-stocké avant le
/// chargement, si bien que la modale s'ouvre déjà connectée. On vérifie que la saisie du token est
/// verrouillée (champ + bouton « Se connecter » grisés), que « Se déconnecter » est actif, et que le
/// badge d'identité est au vert (`badge-succes`). Déterministe, sans appel réseau ni asynchronisme.
@ExtendWith(ApplicationExtension.class)
class ConnexionModaleConnecteeViewTest {

    private static final ProfilVigieChiro PROFIL = new ProfilVigieChiro("6a1b", "Sébastien", "Observateur");

    private ConnexionModaleController controleur;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-connexion-connectee");
        StockageConnexion stockage = new StockageConnexion(new Workspace(workspace), Horloge.systeme());
        // Pré-connecté : la modale s'ouvre sur l'état « connecté ».
        stockage.enregistrer("TOK", PROFIL);
        ClientVigieChiro client = mock(ClientVigieChiro.class);
        OuvreurDeLien ouvreur = url -> {};
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            ConnexionViewModel viewModel() {
                return new ConnexionViewModel(stockage, client, Set.of());
            }

            @Provides
            OuvreurDeLien ouvreurDeLien() {
                return ouvreur;
            }
        });
        FXMLLoader loader = new FXMLLoader(ConnexionModaleController.class.getResource("ConnexionModale.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        stage.setScene(new Scene(vue));
        stage.show();
    }

    @Test
    @DisplayName("#798 : « Se déconnecter » confirme avant d'effacer le jeton local")
    void deconnexion_confirme_avant_effacement(FxRobot robot) {
        List<String> demandes = new ArrayList<>();
        controleur.setConfirmateur(message -> {
            demandes.add(message);
            return false; // l'utilisateur refuse
        });

        robot.clickOn("#boutonDeconnecter");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(demandes).as("la déconnexion demande confirmation").hasSize(1);
        assertThat(demandes.get(0)).contains("jeton");
        // Refus → toujours connecté : badge d'identité au vert et saisie du token verrouillée.
        assertThat(robot.lookup("#labelIdentite").queryAs(Label.class).getStyleClass())
                .contains("badge-succes");
        assertThat(robot.lookup("#champToken").queryAs(TextField.class).isDisabled())
                .isTrue();
    }

    @Test
    @DisplayName("Connecté : saisie verrouillée, déconnexion active, badge d'identité au vert")
    void etat_connecte(FxRobot robot) {
        assertThat(robot.lookup("#labelIdentite").queryAs(Label.class).getText())
                .contains("Sébastien")
                .contains("Observateur");
        assertThat(robot.lookup("#labelIdentite").queryAs(Label.class).getStyleClass())
                .contains("badge-succes");
        assertThat(robot.lookup("#champToken").queryAs(TextField.class).isDisabled())
                .as("le token n'est plus saisissable une fois connecté")
                .isTrue();
        assertThat(robot.lookup("#boutonConnecter").queryAs(Button.class).isDisabled())
                .isTrue();
        assertThat(robot.lookup("#boutonDeconnecter").queryAs(Button.class).isDisabled())
                .isFalse();
    }
}

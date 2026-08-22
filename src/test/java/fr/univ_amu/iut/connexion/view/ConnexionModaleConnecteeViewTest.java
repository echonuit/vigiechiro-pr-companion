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
import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.ConnexionViewModel;
import fr.univ_amu.iut.recette.Respiration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
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

    // ⚠️ Cette classe ne cite plus `S1-11`, et ce n'est pas un oubli. Elle monte `ConnexionModale.fxml`
    // SEULE : son clip montrait une modale sur fond noir, sans l'écran d'où part le geste ni celui où
    // l'on retombe ([ADR 4188]). Le cas est joué par `ScenarioPerceptifIssuesConnexionTest`, qui ouvre
    // la modale depuis le menu principal.
    //
    // ⚠️ Le garde `ClipDeModaleTest` ne l'avait pas vue : son motif ancrait « Modale » au DÉBUT du nom
    // de fichier, et `ConnexionModale.fxml` lui échappait. Il regardait deux fichiers sur quatre et se
    // déclarait vert.
    //
    // Ses assertions restent : elles gardent le câblage de la modale.

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
                return new ConnexionViewModel(stockage, client, Set.of(), java.util.Optional.empty());
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
        // ⚠️ `Habillage`, et non `new Scene` : ce cas est FILMÉ, et une scène montée sans habillage
        // porte la police de la MACHINE (#3773, #4149).
        stage.setScene(Habillage.scene(vue));
        stage.show();
    }

    private static TextField champToken(FxRobot robot) {
        return robot.lookup("#champToken").queryAs(TextField.class);
    }

    @Test
    @DisplayName("#798 : « Se déconnecter » confirme avant d'effacer le jeton local")
    void deconnexion_confirme_avant_effacement(FxRobot robot) {
        // ⚠️ Le dialogue DE LA PRODUCTION, ouvert sans bloquer. Le test le remplaçait par une lambda
        // muette : rien ne paraissait, et la revue l'a vu - « la confirmation ne s'affiche pas » (#4170).
        //
        // `ConfirmationNavigation.dialogue(...)` existe précisément pour cela : même type, même
        // habillage, même texte. Ce qui se voit est donc juste, à une chose près qui ne se voit pas -
        // la fenêtre ne BLOQUE pas, là où `showAndWait` figerait le banc.
        List<String> demandes = new ArrayList<>();
        List<Alert> ouverts = new ArrayList<>();
        controleur.confirmateur().definir(message -> {
            demandes.add(message);
            Alert dialogue = new ConfirmationNavigation().dialogue(message);
            dialogue.initOwner(champToken(robot).getScene().getWindow());
            dialogue.show();
            ouverts.add(dialogue);
            return false; // l'utilisateur refuse
        });

        // L'état connecté au repos : c'est de lui qu'on part, et c'est à lui qu'on revient après le refus.
        Respiration.leTempsDeLire(robot);
        robot.clickOn("#boutonDeconnecter");
        WaitForAsyncUtils.waitForFxEvents();
        // La confirmation est à l'écran : c'est ce que ce cas fait juger, et c'est ce qui manquait.
        Respiration.surLeMomentCle(robot);

        assertThat(ouverts)
                .as("un dialogue de confirmation a bien paru, et non une lambda muette")
                .hasSize(1);
        assertThat(ouverts.get(0).isShowing()).isTrue();
        robot.interact(() -> ouverts.get(0).close());
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.apresLeGeste(robot);

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

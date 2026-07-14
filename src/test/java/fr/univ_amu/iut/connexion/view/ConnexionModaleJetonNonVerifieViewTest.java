package fr.univ_amu.iut.connexion.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.ConnexionViewModel;
import java.nio.file.Files;
import java.nio.file.Path;
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

/// La modale ouverte sur un **jeton conservé non vérifié** (#1369) : une connexion tentée hors ligne
/// n'a plus jeté le jeton. À l'ouverture, la modale le REVÉRIFIE d'elle-même (ici la plateforme est
/// toujours injoignable : l'état reste « non vérifié », dit honnêtement), la saisie reste possible
/// (recoller un autre jeton) et la déconnexion aussi (jeter ce jeton). Socle synchrone en test, pas
/// de réseau.
@ExtendWith(ApplicationExtension.class)
class ConnexionModaleJetonNonVerifieViewTest {

    private ClientVigieChiro client;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-connexion-non-verifie");
        StockageConnexion stockage = new StockageConnexion(new Workspace(workspace), Horloge.systeme());
        // Jeton conservé par une connexion hors ligne (#1369) : enregistré, jamais vérifié.
        stockage.enregistrer("TOK-HORS-LIGNE", null);
        client = mock(ClientVigieChiro.class);
        when(client.moi()).thenReturn(ReponseApi.injoignable("délai d'attente dépassé"));
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
        stage.setScene(new Scene(vue));
        stage.show();
    }

    @Test
    @DisplayName("#1369 : jeton non vérifié → état dit, revérification tentée, saisie et déconnexion possibles")
    void jeton_non_verifie_affiche_et_reverifie(FxRobot robot) {
        // La revérification a été tentée à l'ouverture, sans geste de l'utilisateur.
        verify(client, atLeastOnce()).moi();

        assertThat(robot.lookup("#labelIdentite").queryAs(Label.class).getText())
                .contains("non vérifié");
        Label bandeau = robot.lookup("#bandeauStatut").queryAs(Label.class);
        assertThat(bandeau.getText()).contains("injoignable").contains("reste enregistré");

        assertThat(robot.lookup("#champToken").queryAs(TextField.class).isDisabled())
                .as("pas vérifié = pas connecté : on peut recoller un autre jeton")
                .isFalse();
        assertThat(robot.lookup("#boutonDeconnecter").queryAs(Button.class).isDisabled())
                .as("le jeton conservé doit pouvoir être jeté")
                .isFalse();
    }
}

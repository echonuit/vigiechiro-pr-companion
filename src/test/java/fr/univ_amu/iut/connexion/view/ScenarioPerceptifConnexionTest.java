package fr.univ_amu.iut.connexion.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.Modales;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.ConnexionViewModel;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.Jugement;
import fr.univ_amu.iut.recette.Seance;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le scénario qui **joue** `S1-26`, pour qu'un humain le tranche en regardant (#3791, EPIC #3667).
///
/// ## Ce que ce test prouve, et ce qu'il ne prouve pas
///
/// Il prouve que la modale **s'ouvre** : le champ de saisie est là, à l'écran, à la fin du geste.
/// Il ne prouve **pas** que l'ouverture s'est faite sans saut - aucune assertion ne voit un contenu
/// qui se replace, elle voit un contenu correct une fois posé. C'est pourquoi il porte
/// `jugement = HUMAIN` : le verdict revient à qui regarde le clip.
///
/// ⚠️ L'assertion de jeu n'est pas décorative. Un scénario qui n'assert rien du tout **échouerait en
/// silence** : robot mort, clip noir, et le contrôle de couverture du montage n'y verrait qu'une
/// fenêtre de moins - ce qui est parfaitement légitime pour un test sans interface. `HUMAIN` dit qui
/// rend le verdict, pas qu'on ne vérifie rien.
///
/// ## ⚠️ Pourquoi la modale n'est PAS ouverte dans le `@Start`
///
/// [ConnexionModaleViewTest] la montre dans son `@Start`, ce qui est juste pour asserter un état.
/// Ici ce serait fatal : les repères de la séance filmée sont posés **autour du test**, si bien que
/// l'ouverture tomberait avant le premier et n'apparaîtrait sur aucune image. Le clip raterait
/// exactement ce qu'il faut juger.
///
/// L'ouverture reproduit donc `NavigationConnexion.ouvrir()`, du chargement FXML jusqu'à
/// [Habillage#scene(Parent)] - qui n'est pas un détail : c'est ce qui embarque la typographie, et
/// donc ce qui fait que le clip montre l'application telle qu'elle est livrée.
@ExtendWith(ApplicationExtension.class)
class ScenarioPerceptifConnexionTest {

    /// Le temps d'arrêt avant le geste : l'écran au repos sert de référence à qui compare.
    private static final long AVANT_MS = 600;

    /// Et après : de quoi voir si quelque chose se replace une fois la modale posée.
    private static final long APRES_MS = 1_200;

    private Injector injector;

    @Start
    void start(Stage stage) throws IOException {
        Path workspace = Files.createTempDirectory("vc-scenario-connexion");
        StockageConnexion stockage = new StockageConnexion(new Workspace(workspace), Horloge.systeme());
        ClientVigieChiro client = mock(ClientVigieChiro.class);
        injector = Guice.createInjector(new AbstractModule() {
            @Provides
            ConnexionViewModel viewModel() {
                return new ConnexionViewModel(stockage, client, Set.of());
            }

            @Provides
            OuvreurDeLien ouvreurDeLien() {
                return lien -> {
                    // Le scénario n'ouvre aucun navigateur : rien à voir sur le film, et rien à
                    // lancer sur la machine qui filme.
                };
            }
        });
        // La scène hôte reste VIDE, et c'est le point : ce qu'il faut voir est l'ouverture, elle
        // doit donc se produire pendant le test et non avant lui.
        stage.setScene(Habillage.scene(new StackPane()));
        stage.show();
    }

    @Test
    @CasDeRecette(value = "S1-26", jugement = Jugement.HUMAIN)
    @DisplayName("S1-26 · la modale de connexion s'ouvre : à regarder, rien ne doit se replacer après coup")
    void la_modale_de_connexion_s_ouvre(FxRobot robot) {
        respirer(robot, AVANT_MS);

        robot.interact(this::ouvrirLaModaleCommeLApplication);
        WaitForAsyncUtils.waitForFxEvents();

        respirer(robot, APRES_MS);

        assertThat(robot.lookup("#champToken").tryQuery())
                .as("le geste a-t-il seulement eu lieu ? Sans cette question, un robot mort rendrait"
                        + " un clip noir que personne ne signalerait.")
                .isPresent();
    }

    // ----------------------------------------------------------------------------------------

    /// Le même chemin que `NavigationConnexion.ouvrir()`, joué depuis le test pour que l'ouverture
    /// tombe dans la fenêtre filmée.
    private void ouvrirLaModaleCommeLApplication() {
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationConnexion.class, "ConnexionModale.fxml");
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            Stage modale = new Stage();
            modale.initModality(Modality.APPLICATION_MODAL);
            modale.setTitle("Connexion Vigie-Chiro");
            modale.setScene(Habillage.scene(vue));
            Modales.fermerParEchap(modale);
            modale.show();
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }

    /// Ne s'arrête que si l'on filme : hors séance filmée, ces respirations n'allongeraient le build
    /// que pour personne.
    private static void respirer(FxRobot robot, long millis) {
        if (Seance.filmee()) {
            robot.sleep(millis);
        }
    }
}

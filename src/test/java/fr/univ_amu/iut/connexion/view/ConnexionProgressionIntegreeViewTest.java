package fr.univ_amu.iut.connexion.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.ConnexionViewModel;
import fr.univ_amu.iut.connexion.viewmodel.RefletDuJeton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// #2642 : l'avancement de la connexion paraît **dans** sa modale, pas dans une seconde fenêtre.
///
/// Ce qui se joue ici ne se voit sur **aucune capture** : un aperçu rend une scène, pas une pile de
/// fenêtres. C'est donc le seul dispositif capable de tenir l'arbitrage, et c'est pour ça qu'il existe.
///
/// Le montage prend un [ExecuteurTacheSynchrone] : le travail se déroule pendant le clic, ce qui rend
/// observable ce que l'utilisateur voit **pendant** l'opération - un instantané impossible à prendre
/// après coup, puisque tout est alors déjà retiré.
@ExtendWith(ApplicationExtension.class)
class ConnexionProgressionIntegreeViewTest {

    private final List<String> vuPendantLOperation = new ArrayList<>();

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-connexion-progression");
        StockageConnexion stockage = new StockageConnexion(new Workspace(workspace), Horloge.systeme());
        ClientVigieChiro client = mock(ClientVigieChiro.class);
        when(client.moi()).thenAnswer(appel -> {
            // Instantané pris pendant le travail : nombre de fenêtres ouvertes, et état de la zone.
            vuPendantLOperation.add("fenetres=" + Window.getWindows().size());
            vuPendantLOperation.add("barre="
                    + (stage.getScene().lookup("#zoneProgression") != null
                            && stage.getScene().lookup("#zoneProgression").isVisible()));
            vuPendantLOperation.add(
                    "champ_grise=" + ((TextField) stage.getScene().lookup("#champToken")).isDisabled());
            vuPendantLOperation.add("fermer_grise=" + ((Button) stage.getScene().lookup("#boutonFermer")).isDisabled());
            return new ReponseApi.Succes<>(new ProfilVigieChiro("u-1", "Testeuse", "Observateur"));
        });

        Injector injector = Guice.createInjector(new AbstractModule() {
            /// Le reflet du jeton (#4205), exigé par le controller : ici sur un exécuteur direct, la
            /// forme que `RevisionDonnees` documente pour les tests (`Runnable::run`).
            @Provides
            RefletDuJeton refletDuJeton() {
                return new RefletDuJeton(stockage, Runnable::run);
            }

            @Provides
            ConnexionViewModel viewModel() {
                return new ConnexionViewModel(stockage, client, Set.of(), java.util.Optional.empty());
            }

            @Provides
            OuvreurDeLien ouvreurDeLien() {
                return url -> {};
            }

            @Provides
            ExecuteurTache executeur() {
                return new ExecuteurTacheSynchrone();
            }
        });
        FXMLLoader loader = new FXMLLoader(ConnexionModaleController.class.getResource("ConnexionModale.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        stage.setScene(new Scene(vue));
        stage.show();
    }

    @Test
    @DisplayName("#2642 : coller un jeton n'ouvre AUCUNE seconde fenêtre - la barre est dans la modale")
    void la_progression_reste_dans_la_modale(FxRobot robot) {
        int fenetresAvant = Window.getWindows().size();

        robot.clickOn("#champToken").write("TOK-1");
        robot.clickOn("#boutonConnecter");

        assertThat(vuPendantLOperation)
                .as("pendant l'opération : une seule fenêtre, la barre visible, la saisie gelée")
                .containsExactly("fenetres=" + fenetresAvant, "barre=true", "champ_grise=true", "fermer_grise=true");
        assertThat(Window.getWindows()).hasSize(fenetresAvant);
    }

    @Test
    @DisplayName("#2642 : « Fermer » redevient cliquable une fois l'opération finie")
    void fermer_redevient_cliquable(FxRobot robot) {
        robot.clickOn("#champToken").write("TOK-1");
        robot.clickOn("#boutonConnecter");

        // Pendant l'opération il est grisé (cf. l'instantané ci-dessus) ; le laisser grisé après serait
        // pire que de n'avoir rien fait : la modale ne se fermerait plus.
        assertThat(robot.lookup("#boutonFermer").queryAs(Button.class).isDisabled())
                .isFalse();
    }

    @Test
    @DisplayName("#2642 : la zone d'avancement est repliée tant qu'il ne se passe rien")
    void la_zone_est_repliee_au_repos(FxRobot robot) {
        StackPane zone = robot.lookup("#zoneProgression").queryAs(StackPane.class);

        assertThat(zone.isVisible()).isFalse();
        // Non gérée autant qu'invisible : sinon elle réserverait sa place et laisserait un trou à hauteur
        // de la barre dans une modale au repos.
        assertThat(zone.isManaged()).isFalse();
        assertThat(zone.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("#2642 : l'opération finie, la zone se replie et rend la main")
    void la_zone_se_replie_apres_coup(FxRobot robot) {
        robot.clickOn("#champToken").write("TOK-1");
        robot.clickOn("#boutonConnecter");

        StackPane zone = robot.lookup("#zoneProgression").queryAs(StackPane.class);
        assertThat(zone.getChildren()).isEmpty();
        assertThat(zone.isVisible()).isFalse();
        assertThat(zone.lookupAll(".progress-bar")).isEmpty();
        assertThat(robot.lookup("#zoneProgression").queryAs(StackPane.class).lookup(".button"))
                .as("plus de bouton Annuler une fois l'opération terminée")
                .isNull();
    }

    /// Garde-fou : la barre n'est pas posée en dur dans le FXML, elle n'existe que pendant une opération.
    /// Une barre figée dans la vue paraîtrait juste sur une capture tout en n'étant reliée à rien.
    @Test
    @DisplayName("#2642 : aucune barre n'est codée en dur dans le FXML")
    void aucune_barre_en_dur_dans_le_fxml(FxRobot robot) {
        assertThat(robot.lookup("#zoneProgression").queryAs(StackPane.class).lookupAll(".progress-bar"))
                .isEmpty();
    }
}

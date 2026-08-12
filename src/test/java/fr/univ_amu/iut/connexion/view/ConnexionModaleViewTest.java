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
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.ConnexionViewModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX de la modale « Connexion VigieChiro » (#727/#741) : chargement du FXML via
/// Guice (client mocké, ouvreur de lien espionné, stockage sur un dossier temporaire vierge), état
/// initial « non connecté », ouverture de la plateforme (étape 1) et copie du marque-page (étape 2).
/// Pas de réseau.
@ExtendWith(ApplicationExtension.class)
class ConnexionModaleViewTest {

    private final AtomicReference<String> urlOuverte = new AtomicReference<>();

    private ClientVigieChiro client;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-connexion");
        StockageConnexion stockage = new StockageConnexion(new Workspace(workspace), Horloge.systeme());
        client = mock(ClientVigieChiro.class);
        OuvreurDeLien ouvreur = urlOuverte::set;
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
    @DisplayName("État initial : « Non connecté », champ actif, déconnexion désactivée, bandeau masqué")
    void etat_initial(FxRobot robot) {
        assertThat(robot.lookup("#labelIdentite").queryAs(Label.class).getText())
                .isEqualTo("Non connecté");
        // Non connecté : la saisie du token est possible, la déconnexion non.
        assertThat(robot.lookup("#champToken").queryAs(TextField.class).isDisabled())
                .isFalse();
        assertThat(robot.lookup("#boutonDeconnecter").queryAs(Button.class).isDisabled())
                .isTrue();
        assertThat(robot.lookup("#bandeauStatut").queryAs(Label.class).isVisible())
                .as("aucun statut à afficher au départ")
                .isFalse();
    }

    @Test
    @DisplayName("Étape 1 : « Ouvrir Vigie-Chiro » ouvre la plateforme dans le navigateur")
    void ouvrir_site(FxRobot robot) {
        robot.clickOn("Ouvrir Vigie-Chiro");

        assertThat(urlOuverte.get()).contains("vigiechiro");
    }

    @Test
    @DisplayName("Étape 2 : « Copier le marque-page » copie et affiche l'instruction dans le bandeau")
    void copier_marque_page(FxRobot robot) {
        robot.clickOn("Copier le marque-page");

        Label bandeau = robot.lookup("#bandeauStatut").queryAs(Label.class);
        assertThat(bandeau.isVisible()).isTrue();
        assertThat(bandeau.getText()).contains("Marque-page copié");
    }

    @Test
    @DisplayName("#3453 : la zone de progression aussi agrandit la fenêtre, AVANT que le bandeau n'arrive")
    void zone_de_progression_agrandit_la_fenetre(FxRobot robot) {
        StackPane zone = robot.lookup("#zoneProgression").queryAs(StackPane.class);
        Stage fenetre = (Stage) robot.lookup("#racine").query().getScene().getWindow();
        double avant = fenetre.getHeight();

        // Le TRANSITOIRE, et non l'état stabilisé : pendant la récupération du référentiel, la zone de
        // progression paraît seule - le bandeau, lui, n'arrive qu'à la fin. Ne surveiller que le bandeau
        // laissait donc le contenu déborder tout le temps de l'appel réseau, puis se recaler d'un coup.
        //
        // C'est précisément ce qu'aucune capture ne peut montrer : elle photographie un état stabilisé,
        // jamais le chemin pour y arriver. D'où un constat qui n'est sorti que d'une séance pilotée.
        robot.interact(() -> {
            zone.setVisible(true);
            zone.setManaged(true);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(fenetre.getHeight())
                .as("la fenêtre suit la zone de progression, sans attendre le bandeau")
                .isGreaterThan(avant);
    }

    @Test
    @DisplayName("#1534 : le bandeau qui paraît agrandit la fenêtre au lieu de pousser les boutons dehors")
    void bandeau_agrandit_la_fenetre(FxRobot robot) {
        Stage fenetre = (Stage) robot.lookup("#racine").query().getScene().getWindow();
        double avant = fenetre.getHeight();

        // La modale a été dimensionnée à l'ouverture avec le bandeau masqué (managed=false). Le rendre
        // visible réserve sa place ; sans suivi de croissance, la fenêtre ne bougeait pas et « Se
        // déconnecter »/« Fermer » passaient sous la ligne de flottaison.
        robot.clickOn("Copier le marque-page");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(robot.lookup("#bandeauStatut").queryAs(Label.class).isManaged())
                .as("le bandeau doit occuper sa place une fois affiché")
                .isTrue();
        assertThat(fenetre.getHeight())
                .as("la fenêtre grandit pour loger le bandeau (Modales.suivreLaCroissance), au lieu de "
                        + "laisser son apparition pousser les boutons du bas hors du cadre (#1534)")
                .isGreaterThan(avant);
    }

    @Test
    @DisplayName("Étape 3 : se connecter sans token affiche une invite dans le bandeau, sans réseau")
    void connecter_sans_token(FxRobot robot) {
        robot.clickOn("Se connecter");

        Label bandeau = robot.lookup("#bandeauStatut").queryAs(Label.class);
        assertThat(bandeau.isVisible()).isTrue();
        assertThat(bandeau.getText()).contains("Collez d'abord");
    }

    @Test
    @DisplayName("#1255 : un token valide connecte (socle synchrone en test) et le bandeau passe au succès")
    void connecter_token_valide(FxRobot robot) {
        when(client.moi()).thenReturn(ReponseApi.succes(new ProfilVigieChiro("u1", "chiro", "observateur")));

        robot.clickOn("#champToken").write("jeton-valide");
        robot.clickOn("Se connecter");

        Label bandeau = robot.lookup("#bandeauStatut").queryAs(Label.class);
        assertThat(bandeau.getText()).contains("Connexion réussie");
        assertThat(robot.lookup("#labelIdentite").queryAs(Label.class).getText())
                .contains("chiro");
    }

    @Test
    @DisplayName("#1255 : un token refusé par le serveur est restitué dans le bandeau, saisie déverrouillée")
    void connecter_token_invalide(FxRobot robot) {
        when(client.moi()).thenReturn(ReponseApi.refuse(401, "token invalide"));

        robot.clickOn("#champToken").write("jeton-perime");
        robot.clickOn("Se connecter");

        Label bandeau = robot.lookup("#bandeauStatut").queryAs(Label.class);
        assertThat(bandeau.getText()).contains("Token invalide ou expiré");
        assertThat(robot.lookup("#champToken").queryAs(TextField.class).isDisabled())
                .as("la vérification finie, la saisie doit être déverrouillée pour réessayer")
                .isFalse();
    }

    @Test
    @DisplayName("#1284 : plateforme injoignable → le bandeau le dit, sans accuser le jeton")
    void connecter_plateforme_injoignable(FxRobot robot) {
        // Avant #1284, ce cas affichait « Token invalide ou expiré » : l'observateur jetait un jeton
        // parfaitement valide parce que le Wi-Fi était coupé.
        when(client.moi()).thenReturn(ReponseApi.injoignable("délai d'attente dépassé"));

        robot.clickOn("#champToken").write("jeton-valide-mais-hors-ligne");
        robot.clickOn("Se connecter");

        Label bandeau = robot.lookup("#bandeauStatut").queryAs(Label.class);
        assertThat(bandeau.getText())
                .contains("injoignable")
                .contains("délai d'attente dépassé")
                .doesNotContain("Token invalide");
        assertThat(robot.lookup("#champToken").queryAs(TextField.class).isDisabled())
                .isFalse();
    }

    @Test
    @DisplayName("#1255 : une erreur réseau est restituée dans le bandeau au lieu de verrouiller la modale")
    void connecter_erreur_reseau(FxRobot robot) {
        when(client.moi()).thenThrow(new RuntimeException("Vigie-Chiro injoignable"));

        robot.clickOn("#champToken").write("jeton-quelconque");
        robot.clickOn("Se connecter");

        Label bandeau = robot.lookup("#bandeauStatut").queryAs(Label.class);
        assertThat(bandeau.getText()).contains("Vérification impossible").contains("Vigie-Chiro injoignable");
        assertThat(robot.lookup("#champToken").queryAs(TextField.class).isDisabled())
                .as("l'échec ne doit pas laisser la modale verrouillée « en cours »")
                .isFalse();
        assertThat(robot.lookup("#boutonConnecter").queryAs(Button.class).isDisabled())
                .isFalse();
    }
}

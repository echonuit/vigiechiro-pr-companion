package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de l'écran **M-Sites** : chargement du chrome + de la vue d'accueil
/// via Guice sur une base SQLite jetable, affichage des cartes seedées et navigation vers le
/// détail au déclenchement d'une carte.
///
/// Les interactions sont déclenchées **sur le thread JavaFX** (`robot.interact` + handlers /
/// `fire()`) plutôt que via le robot souris/clavier de l'OS : ce dernier dépend d'un serveur
/// d'affichage capable de synthétiser des entrées (il se bloque sous Wayland et ne route pas
/// fiablement les clics sous un xvfb sans gestionnaire de fenêtres). On teste ainsi le câblage
/// réel (handler de carte → navigation) de façon déterministe dans tous les environnements.
@ExtendWith(ApplicationExtension.class)
class MesSitesViewTest {

    private static final String ID_USER = "u-test";
    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-mes-sites");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        // Composition réelle, mais exécuteur SYNCHRONE (#1212) : le chargement des cartes passe par
        // l'occupation ; en asynchrone les assertions courraient contre le fil de chargement.
        injector =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ExecuteurTache.class)
                                .to(ExecuteurTacheSynchrone.class)
                                .in(Singleton.class);
                    }
                }));
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        seeder(source);
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 720));
        injector.getInstance(NavigationSites.class).ouvrirAccueil();
        stage.show();
    }

    private void seeder(SourceDeDonnees source) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        ServiceSites service = injector.getInstance(ServiceSites.class);
        Site etang = service.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(etang.id(), "A1", 43.5, 5.4, "Chêne");
        Site zac = service.creerSite("752204", "ZAC Nord", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(zac.id(), "A1", null, null, null);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#1045 : le bouton « Synchroniser depuis VigieChiro » est visible dans l'app complète")
    void bouton_synchro_visible(FxRobot robot) {
        Button bouton = robot.lookup("#btnSyncVigieChiro").queryAs(Button.class);

        assertThat(bouton.isVisible())
                .as("app complète : la passerelle est liée, le bouton est offert")
                .isTrue();
    }

    @Test
    @DisplayName("#1212 : l'overlay d'occupation est en place, masqué une fois le chargement terminé")
    void overlay_occupation_masque_apres_chargement(FxRobot robot) {
        Node voile = robot.lookup(".occupation-voile").query();

        assertThat(voile).as("overlay d'occupation superposé à l'écran").isNotNull();
        assertThat(voile.isVisible())
                .as("chargement terminé (exécuteur synchrone) : overlay masqué, cartes affichées")
                .isFalse();
    }

    @Test
    @DisplayName("Les cartes des sites seedés sont affichées")
    void affiche_les_cartes(FxRobot robot) {
        List<String> titres = robot.lookup(".carte-titre").queryAllAs(Label.class).stream()
                .map(Label::getText)
                .toList();

        assertThat(titres).contains("Carré 640380", "Carré 752204");
    }

    @Test
    @DisplayName("Déclencher une carte ouvre le détail du site correspondant")
    void clic_carte_ouvre_le_detail(FxRobot robot) {
        HBox carte = trouverCarte(robot, "Carré 640380");

        robot.interact(() -> carte.getOnMouseClicked().handle(clicGauche()));

        // Le titre du détail est déporté en barre de statut (#693) : on confirme l'ouverture du bon
        // site par le bandeau d'identité (numéro de carré) plutôt que par un titre d'en-tête.
        Label numeroCarre = robot.lookup("#valNumeroCarre").queryAs(Label.class);
        assertThat(numeroCarre.getText()).isEqualTo("640380");
    }

    private static HBox trouverCarte(FxRobot robot, String titre) {
        return robot.lookup(".carte-site").queryAllAs(HBox.class).stream()
                .filter(carte -> contientTitre(carte, titre))
                .findFirst()
                .orElseThrow();
    }

    private static boolean contientTitre(HBox carte, String titre) {
        return carte.lookupAll(".carte-titre").stream()
                .anyMatch(noeud -> noeud instanceof Label label && titre.equals(label.getText()));
    }

    private static MouseEvent clicGauche() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null);
    }
}

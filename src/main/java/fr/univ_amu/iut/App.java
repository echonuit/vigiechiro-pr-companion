package fr.univ_amu.iut;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.view.OuvreurDeLienSysteme;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/// Point d'entrée JavaFX du SAÉ 2.01 - VigieChiro PR Companion.
///
/// Bootstrap (patron CM4) : construit la racine de composition Guice, puis charge le chrome
/// principal (`commun/view/MainView.fxml`) en branchant la `controllerFactory` du `FXMLLoader`
/// sur l'injecteur. Les controllers sont ainsi instanciés par Guice (injection par constructeur),
/// ce qui leur donne accès aux ViewModels et services du socle et des features.
public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Injector injector = RacineInjecteur.creer();

        // Garantit que le schéma existe avant le premier accès à la base (migration idempotente).
        // Même amorçage que le CLI (Cli) et les outils Capture* : sans cela, le premier écran qui
        // lit la base échoue sur « no such table ».
        injector.getInstance(MigrationSchema.class).migrer();

        // Branche le navigateur web (coordonnées GPS -> OpenStreetMap, etc.) sur le HostServices
        // JavaFX, seul disponible depuis une Application. Hors application graphique (CLI/tests),
        // l'ouvreur reste inactif (cf. OuvreurDeLienSysteme).
        injector.getInstance(OuvreurDeLienSysteme.class).initialiser(getHostServices());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent root = loader.load();

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("VigieChiro PR Companion");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package fr.univ_amu.iut;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.view.OuvreurDeLienSysteme;
import fr.univ_amu.iut.importation.model.ExtracteurZip;
import fr.univ_amu.iut.passage.model.BackfillHorodatageCapture;
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

        // Backfill applicatif de l'horodatage de capture (#530) : les séquences importées avant la colonne
        // recorded_at (V09) n'ont pas d'heure ; on la reconstruit en re-parsant leur nom de fichier. Idempotent
        // (ne touche que les séquences sans horodatage) et peu coûteux, rejoué à chaque démarrage.
        injector.getInstance(BackfillHorodatageCapture.class).remplir();

        // Filet anti-fuite : supprime au démarrage les temporaires d'extraction `import-zip-*` résiduels
        // d'une session précédente interrompue (application fermée/plantée pendant ou après un import
        // `.zip`). Ce nettoyage n'était rejoué qu'avant une nouvelle extraction de zip ; une suite d'imports
        // de dossier ne le déclenchait jamais, laissant un résidu de plusieurs Go saturer le disque et
        // faire échouer l'import suivant (« disque plein »). Best-effort.
        ExtracteurZip.nettoyerTemporairesResiduels(
                injector.getInstance(Workspace.class).racine());

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

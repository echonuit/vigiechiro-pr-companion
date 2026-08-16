package fr.univ_amu.iut;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.Amorcage;
import fr.univ_amu.iut.commun.model.ConfigurationJournalisation;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.view.AlerteDemarrage;
import fr.univ_amu.iut.commun.view.CauseLisible;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvreurDeLienSysteme;
import fr.univ_amu.iut.commun.view.SignalementIncident;
import fr.univ_amu.iut.commun.view.TailleOuverture;
import fr.univ_amu.iut.commun.view.Typographie;
import fr.univ_amu.iut.importation.model.ExtracteurZip;
import fr.univ_amu.iut.passage.model.BackfillHorodatageCapture;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

/// Point d'entrée JavaFX du SAÉ 2.01 - VigieChiro Companion.
///
/// Bootstrap (patron CM4) : construit la racine de composition Guice, puis charge le chrome
/// principal (`commun/view/MainView.fxml`) en branchant la `controllerFactory` du `FXMLLoader`
/// sur l'injecteur. Les controllers sont ainsi instanciés par Guice (injection par constructeur),
/// ce qui leur donne accès aux ViewModels et services du socle et des features.
public class App extends Application {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    @Override
    public void start(Stage primaryStage) throws Exception {
        // La police du produit, avant toute scène : sans elle, le rendu dépend de la machine (#3361).
        Typographie.installer();
        // Filet global (#795) : une exception non capturée (fil JavaFX ou tâche de fond) était jusqu'ici
        // perdue en console. On la signale à l'utilisateur par une alerte, et on la **journalise** avec sa
        // trace (#1523) : un incident laisse désormais une trace inspectable, même à message nul.
        // ⚠️ Le filet vit dans sa propre classe depuis qu'il a bouclé sur lui-même (#3700) : habiller
        // l'alerte échouait pour la raison même qu'elle rapportait, et l'échec repassait par ici.
        Thread.setDefaultUncaughtExceptionHandler(new SignalementIncident(LOG, Platform::runLater, erreur -> {
            Alert alerte = new Alert(Alert.AlertType.ERROR);
            Habillage.poser(alerte.getDialogPane());
            alerte.setHeaderText("Une erreur inattendue est survenue");
            // #3470 : et surtout PAS `erreur.getMessage()`. Derrière une enveloppe, la JDK fabrique un
            // message qui est le `toString()` de sa cause : l'utilisateur lisait
            // « java.lang.reflect.InvocationTargetException » là où la panne disait « Accès refusé ».
            alerte.setContentText(CauseLisible.messageDe(erreur));
            alerte.showAndWait();
        }));

        // Migrer, PUIS composer (ADR 1038). La composition lit `app_setting` pour filtrer les features
        // (Fonctionnalites.filtreActives) : migrer d'abord garantit que les drapeaux sont lus dans une
        // base à jour. Sans cet ordre, une migration portant sur une clé `feature.*` s'appliquerait trop
        // tard et le choix de l'utilisateur serait ignoré, sans message, pendant tout un lancement
        // (#2187). Le schéma est créé au besoin (migration idempotente) : sans cela, le premier écran qui
        // lit la base échouerait sur « no such table ».
        Injector injector;
        try {
            injector = Amorcage.migrerPuisComposer();
        } catch (DataAccessException dossierOccupe) {
            // Le dossier de travail est déjà tenu par une autre fenêtre (#2731). On le dit et on sort,
            // plutôt que de laisser deux instances écrire la même base : la seconde ne s'en
            // apercevrait qu'à un échec SQLite tardif, au milieu d'une écriture.
            LOG.log(Level.WARNING, dossierOccupe, () -> "Démarrage refusé : dossier de travail occupé");
            AlerteDemarrage.refusDeDemarrage("VigieChiro Companion est déjà ouvert", dossierOccupe.getMessage());
            Platform.exit();
            return;
        }

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

        FXMLLoader loader = ChargeurFxml.chargeur(getClass(), "commun/view/MainView.fxml");
        loader.setControllerFactory(injector::getInstance);
        Parent root = loader.load();

        // #3452 : sans dimensionnement, JavaFX retombe sur la taille PRÉFÉRÉE du contenu - mesurée à
        // 960x640, où l'accueil (818 px de contenu) ouvrait sur deux cartes d'activité coupées.
        //
        // La taille se pose sur la SCÈNE, jamais par `setWidth`/`setHeight` sur la fenêtre : ces deux-là
        // figent le Stage et tuent son auto-ajustement au contenu.
        Rectangle2D ecran = Screen.getPrimary().getVisualBounds();
        TailleOuverture taille = TailleOuverture.bornee(ecran.getWidth(), ecran.getHeight());
        primaryStage.setScene(Habillage.scene(root, taille.largeur(), taille.hauteur()));
        primaryStage.setMinWidth(TailleOuverture.LARGEUR_MINIMALE);
        primaryStage.setMinHeight(TailleOuverture.HAUTEUR_MINIMALE);
        primaryStage.setTitle("VigieChiro Companion");
        chargerIcones(primaryStage);

        // Garde-fou de fermeture (#906) : si une opération critique est en cours (import, génération
        // d'archives, dépôt) ou une saisie non enregistrée est en attente, on demande confirmation avant de
        // fermer, sinon la tâche serait interrompue en laissant un état incohérent.
        Navigateur navigateur = injector.getInstance(Navigateur.class);
        primaryStage.setOnCloseRequest(evenement -> {
            if (!navigateur.confirmerFermeture()) {
                evenement.consume();
            }
        });

        primaryStage.show();
    }

    /// Rend le dossier de travail à la fermeture (#2731). Sans cela, le verrou ne serait relâché que
    /// par la mort du processus : c'est ce que fait le système, mais s'en remettre à lui laisserait le
    /// dossier réservé pendant toute une fermeture propre.
    @Override
    public void stop() {
        Amorcage.libererLeWorkspace();
    }

    /// Donne à la fenêtre l'icône de l'application, en plusieurs tailles (#2144).
    ///
    /// L'empaquetage (jpackage) et l'exécution sont deux chemins **distincts** : une icône correcte
    /// dans l'installeur laisserait quand même la fenêtre, la barre des tâches et l'Alt-Tab avec
    /// l'icône Duke générique. C'est ici que ce second chemin est traité.
    ///
    /// Plusieurs tailles sont fournies plutôt qu'une seule : JavaFX choisit alors la plus proche du
    /// contexte, au lieu de réduire une grande image et d'en faire une bouillie aux petites tailles.
    ///
    /// Une icône manquante ne doit **pas** empêcher l'application de démarrer : c'est une perte
    /// cosmétique, pas fonctionnelle. Elle est donc journalisée, jamais propagée.
    private void chargerIcones(Stage fenetre) {
        for (int taille : new int[] {16, 32, 48, 128, 256}) {
            String chemin = "/icones/vigiechiro-" + taille + ".png";
            try (InputStream flux = App.class.getResourceAsStream(chemin)) {
                if (flux == null) {
                    LOG.warning(() -> "Icône absente des ressources : " + chemin);
                    continue;
                }
                fenetre.getIcons().add(new Image(flux));
            } catch (IOException e) {
                LOG.log(Level.WARNING, e, () -> "Icône illisible : " + chemin);
            }
        }
    }

    public static void main(String[] args) {
        // Journalisation dès l'entrée du processus, avant le lancement JavaFX : même un échec de démarrage
        // laisse une trace (#1523). En test, `AppTest` appelle start() directement (jamais main) : aucun
        // fichier de log n'y est donc installé. Idempotente.
        ConfigurationJournalisation.configurer(Workspace.resolu().dossierLogs());
        launch(args);
    }
}

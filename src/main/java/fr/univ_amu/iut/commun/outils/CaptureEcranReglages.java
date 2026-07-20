package fr.univ_amu.iut.commun.outils;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'écran « Réglages » du socle (`EcranReglages.fxml`, #927) en PNG. Comme l'accueil
/// (cf. [CaptureAccueil]), l'écran appartient au socle `commun` et **agrège** les
// [fr.univ_amu.iut.commun.view.OngletReglages]
/// publiés par les features : on utilise donc l'injecteur applicatif complet
/// ([RacineInjecteur#creer()]). Tant qu'aucune feature ne contribue d'onglet (P1.2), l'aperçu
/// illustre l'état vide (« Aucun réglage disponible ») ; il se remplira quand les features en
/// déclareront (P1.3), sans toucher cet outil.
///
/// Rendu hors-écran par [ApercuFx] dans `.github/assets/`. Lancement headless :
/// `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureEcranReglages {

    private static final String ECRAN = "/fr/univ_amu/iut/commun/view/EcranReglages.fxml";

    private CaptureEcranReglages() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException | IOException probleme) {
                erreur.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        Platform.exit();
        Throwable probleme = erreur.get();
        if (probleme != null) {
            probleme.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    /// Sélectionne l'onglet portant ce titre, pour qu'un aperçu montre autre chose que le premier.
    private static void selectionnerOnglet(Parent ecran, String titre) {
        TabPane onglets = (TabPane) ecran.lookup("#onglets");
        onglets.getTabs().stream()
                .filter(onglet -> titre.equals(onglet.getText()))
                .findFirst()
                .ifPresent(onglet -> onglets.getSelectionModel().select(onglet));
    }

    private static void capturer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-reglages");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = RacineInjecteur.creer();
        // Les contrôles de réglages lisent/écrivent la table app_setting : on migre le schéma pour que
        // ReglagesReactifs trouve ses tables (base neuve = tous les défauts).
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        Parent ecran = chargerFxml(injecteur, ECRAN);
        ApercuFx.enregistrerPng(new Scene(ecran, 760, 520), sortie.resolve("apercu-reglages.png"));

        // Un onglet par domaine, et l'aperçu n'en montrait qu'un : le premier. Les réglages des autres
        // onglets n'étaient donc **documentés nulle part** (#2061). On rend aussi l'onglet « Import »,
        // dont l'option de conservation des originaux porte une conséquence que l'utilisateur doit
        // pouvoir lire avant de l'activer.
        Parent ecranImport = chargerFxml(injecteur, ECRAN);
        Scene sceneImport = new Scene(ecranImport, 760, 520);
        selectionnerOnglet(ecranImport, "Import");
        ApercuFx.enregistrerPng(sceneImport, sortie.resolve("apercu-reglages-import.png"));

        System.out.println("Apercu des reglages ecrit dans " + sortie.toAbsolutePath());
    }

    private static Parent chargerFxml(Injector injecteur, String ressource) {
        FXMLLoader loader = new FXMLLoader(CaptureEcranReglages.class.getResource(ressource));
        loader.setControllerFactory(injecteur::getInstance);
        try {
            return loader.load();
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + ressource, echec);
        }
    }
}

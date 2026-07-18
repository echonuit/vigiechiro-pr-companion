package fr.univ_amu.iut.audio.outils;

import com.google.inject.Injector;
import fr.univ_amu.iut.audio.view.SonsValidationController;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture le **menu de ligne** d'une table (`apercu-menu-ligne.png`, EPIC #1792) : l'artefact visible
/// principal du chantier qui a harmonisé les gestes des tables. Sons & validation sert de modèle parce
/// que son menu est le plus complet et montre la **grammaire entière** : action principale, fiche de
/// l'espèce, `Validation ▸`, `Copier ▸`, puis « Colonnes… » toujours en dernier.
///
/// **Le menu photographié est celui que le contrôleur construit**, récupéré par
/// `table.getContextMenu()` après chargement réel de l'écran - il n'est **pas** reconstruit ici. Une
/// capture reconstruite dérive du produit sans que rien ne l'signale (#1468) : le jour où un item change
/// d'ordre ou de libellé, cette capture le montre.
///
/// Le popup n'appartenant pas à la scène, le `snapshot` de scène ne le saisit pas : on **affiche** le
/// menu puis on photographie la racine de sa propre scène, comme [fr.univ_amu.iut.commun.outils.CaptureFicheEspece].
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureMenuLigne {

    private CaptureMenuLigne() {}

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
        if (erreur.get() != null) {
            erreur.get().printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void capturer() throws IOException {
        Injector injecteur = GraineSonsValidation.preparer();
        FXMLLoader loader = new FXMLLoader(SonsValidationController.class.getResource("SonsValidation.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = charger(loader);
        SonsValidationController controleur = loader.getController();
        controleur.ouvrirSur(new SourceObservations.References(GraineSonsValidation.ID_UTILISATEUR));

        Stage stage = new Stage();
        stage.setScene(new Scene(vue, 1280, 720));
        stage.show();

        if (!(vue.lookup("#tableObservations") instanceof TableView<?> table)) {
            System.out.println("[capture-menu-ligne] table introuvable : capture ignorée.");
            return;
        }
        // Une ligne sélectionnée : les items de ligne s'activent et « Fiche de l'espèce » se nomme.
        table.getSelectionModel().clearAndSelect(0);
        ContextMenu menu = table.getContextMenu();
        if (menu == null) {
            System.out.println("[capture-menu-ligne] aucun menu contextuel : capture ignorée.");
            return;
        }
        ecrire(menu, "apercu-menu-ligne.png");
    }

    private static void ecrire(ContextMenu menu, String fichier) throws IOException {
        Stage hote = new Stage();
        hote.setScene(new Scene(new javafx.scene.layout.StackPane(), 500, 300));
        hote.show();
        menu.show(hote);

        Scene scenePopup = menu.getScene();
        if (scenePopup == null || scenePopup.getRoot() == null) {
            System.out.println("[capture-menu-ligne] popup non rendu (headless) : " + fichier + " ignoré.");
            return;
        }
        scenePopup.getStylesheets().addAll(styles());
        Parent racine = scenePopup.getRoot();
        racine.applyCss();
        racine.layout();

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.WHITE);
        WritableImage image = racine.snapshot(params, null);

        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"), fichier);
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", sortie.toFile());
        System.out.println("[capture-menu-ligne] écrit " + sortie.toAbsolutePath() + " (" + (int) image.getWidth() + "x"
                + (int) image.getHeight() + ")");
        menu.hide();
    }

    /// Feuilles de style partagées (palette indigo + base), comme les autres captures de menu.
    private static List<String> styles() {
        List<String> feuilles = new ArrayList<>();
        for (String nom : List.of("palette.css", "base.css")) {
            var url = Navigateur.class.getResource(nom);
            if (url != null) {
                feuilles.add(url.toExternalForm());
            }
        }
        return feuilles;
    }

    private static Parent charger(FXMLLoader loader) {
        try {
            return loader.load();
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement de SonsValidation.fxml impossible", echec);
        }
    }
}

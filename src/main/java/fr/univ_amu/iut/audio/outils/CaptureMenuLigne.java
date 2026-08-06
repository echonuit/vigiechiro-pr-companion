package fr.univ_amu.iut.audio.outils;

import com.google.inject.Injector;
import fr.univ_amu.iut.audio.view.SonsValidationController;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
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
        stage.setScene(Habillage.scene(vue, 1280, 720));
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
        hote.setScene(Habillage.scene(new javafx.scene.layout.StackPane(), 500, 300));
        hote.show();
        menu.show(hote);

        Scene scenePopup = menu.getScene();
        if (scenePopup == null || scenePopup.getRoot() == null) {
            System.out.println("[capture-menu-ligne] popup non rendu (headless) : " + fichier + " ignoré.");
            return;
        }
        // Habillage commun (#3374). L'ancien helper `styles()` posait bien palette+base, mais
        // n'installait PAS la police embarquee : `base.css` demandait alors une famille non
        // enregistree, et le popup rendait avec celle du systeme.
        // ⚠️ Trop tard pour la TAILLE. Le popup se dimensionne a `show()`, **avant** qu'aucune feuille
        // ne lui soit attachee ; `applyCss` + `layout` repeignent ensuite dans la bonne police sans
        // redimensionner la fenetre. Le menu se mesure donc dans une police et se peint dans une autre.
        //
        // Invisible sur un poste ou le repli systeme EST Noto Sans - retirer la police embarquee ne
        // change pas la taille d'un pixel - et bien reel en CI, ou le meme code rend 309x134 quand un
        // poste rend 289x144. Cf. #3417 : la scene hote n'y peut rien non plus, sonde faite.
        Habillage.poser(scenePopup);
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

    private static Parent charger(FXMLLoader loader) {
        try {
            return loader.load();
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement de SonsValidation.fxml impossible", echec);
        }
    }
}

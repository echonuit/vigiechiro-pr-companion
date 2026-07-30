package fr.univ_amu.iut.audio.outils;

import com.google.inject.Injector;
import fr.univ_amu.iut.audio.view.SonsValidationController;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.MenuButton;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture le **menu ☰ sur le corpus de référence** (`apercu-menu-references.png`) : c'est le seul
/// état où l'entrée « **Exporter la bibliothèque de sons (ZIP)…** » est visible, les entrées propres à
/// un passage (import CSV, publication, export `_Vu`) restant masquées. Aucune capture ne montrait cet
/// état du menu, alors que la bibliothèque y a son unique porte d'entrée - et que le geste est passé de
/// « copie dans un dossier » à « archive annulable » à la clôture de l'EPIC #2790.
///
/// Le seed et le rendu sont factorisés dans [GraineSonsValidation], qui ouvre déjà la vue sur la source
/// `References`. Un `main` distinct (donc une JVM par PNG) car un seul état « audio chargé » est
/// possible par processus.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureMenuReferences {

    private CaptureMenuReferences() {}

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
        Path fichier = GraineSonsValidation.dossierSortie().resolve("apercu-menu-references.png");
        FXMLLoader loader = new FXMLLoader(SonsValidationController.class.getResource("SonsValidation.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        GraineSonsValidation.ouvrirSurReferences(loader.getController());

        if (!(vue.lookup("#menuActions") instanceof MenuButton menuActions)) {
            System.out.println("[capture-menu-references] menu ☰ introuvable : capture ignorée.");
            return;
        }
        if (!ApercuFx.enregistrerMenuOuvert(menuActions, fichier)) {
            System.out.println("[capture-menu-references] popup non rendu (headless) : " + fichier + " ignoré.");
            return;
        }
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage (test).
    public static Injector creerInjecteur() {
        return GraineSonsValidation.creerInjecteur();
    }
}

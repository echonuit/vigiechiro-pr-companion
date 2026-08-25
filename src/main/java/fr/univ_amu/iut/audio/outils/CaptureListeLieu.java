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
import javafx.scene.control.MenuItem;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture la **liste ouverte de la puce « Lieu »** (`apercu-liste-lieu.png`, #2992) : ses en-têtes de
/// groupe et ses points qualifiés par leur carré.
///
/// C'est le seul endroit où ces deux choses se voient. L'aperçu voisin
/// (`apercu-sons-validation-lieu.png`) montre la puce **fermée**, donc son résultat ; la liste, elle,
/// n'était montrée nulle part, alors que c'est là que se juge la lisibilité du critère.
///
/// ## Ce que la capture doit établir
///
/// Que les valeurs sont **groupées et nommées** (Communes, Carrés, Points), là où une liste plate
/// ne disait pas si « Ahetze » était une commune ou un site ; et que le point paraît sous la forme
/// **« carré · point »**, le schéma posant `UNIQUE(site_id, code)` : un code seul désigne autant de lieux
/// qu'il y a de carrés, et la puce les confondait silencieusement.

///
/// Le seed est celui de [GraineSonsValidation]. Un `main` distinct (donc une JVM par PNG) car un seul
/// état « audio chargé » est possible par processus.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureListeLieu {

    private CaptureListeLieu() {}

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
        Path fichier = GraineSonsValidation.dossierSortie().resolve("apercu-liste-lieu.png");
        FXMLLoader loader = new FXMLLoader(SonsValidationController.class.getResource("SonsValidation.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        GraineSonsValidation.ouvrirSurReferences(loader.getController());

        if (!(vue.lookup("#menuAjoutFiltre") instanceof MenuButton menuAjout)) {
            throw new IllegalStateException("Menu « + Filtre » introuvable : la puce ne peut pas être posée.");
        }
        ApercuFx.exigerParLibelle("le menu « + Filtre »", menuAjout.getItems(), MenuItem::getText, "Lieu")
                .fire();

        MenuButton puce = puceLieu(vue);
        if (puce == null) {
            // Un « capture ignorée » en code 0 laissait la galerie porter l'image PRÉCÉDENTE, sans que
            // rien ne distingue « rien à refaire » de « le geste n'a pas eu lieu ».
            throw new IllegalStateException("Puce « Lieu » absente après son ajout : rien à photographier.");
        }
        if (!ApercuFx.enregistrerMenuOuvert(puce, fichier)) {
            System.out.println("[capture-liste-lieu] popup non rendu (headless) : " + fichier + " ignoré.");
            return;
        }
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Le `MenuButton` de la puce posée, reconnu à la classe que lui donne le socle.
    private static MenuButton puceLieu(Parent vue) {
        return vue.lookupAll(".critere-multiple").stream()
                .filter(MenuButton.class::isInstance)
                .map(MenuButton.class::cast)
                .findFirst()
                .orElse(null);
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage (test).
    public static Injector creerInjecteur() {
        return GraineSonsValidation.creerInjecteur();
    }
}

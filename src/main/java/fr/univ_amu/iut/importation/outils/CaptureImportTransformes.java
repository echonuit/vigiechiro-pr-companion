package fr.univ_amu.iut.importation.outils;

import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.importation.view.ActionImportTransformes;
import fr.univ_amu.iut.importation.view.ActionImportTransformes.ModeImport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture la question **« copier ou référencer »** posée par le geste « importer des transformés déjà
/// présents » (#2433) : `apercu-import-transformes-question.png`, rattachée dans le manifeste à la vue
/// d'import parente. C'est la conséquence visible du choix laissé à l'utilisateur (référencer en place vs
/// copier), que ni l'écran d'import ni ses autres dialogues ne montrent.
///
/// La capture rend le **dialogue à boutons** réel (un bouton par mode + « Annuler »), avec les libellés
/// ([ActionImportTransformes.ModeImport#libelle]) et le texte ([ActionImportTransformes#questionMode])
/// exposés par l'action : elle ne recompose pas son sujet, qui finirait par diverger (ADR 0025). On illustre
/// la variante « dossier hors de l'espace de travail » (le cas courant : un NAS, un disque externe), celle
/// où l'application recommande de référencer.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureImportTransformes {

    private CaptureImportTransformes() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException probleme) {
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

    private static void capturer() {
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));
        Path fichier = sortie.resolve("apercu-import-transformes-question.png");

        // Reconstruit le dialogue de ChoixParBoutons (un bouton par mode + « Annuler » = renoncer), à partir
        // des libellés et du texte RÉELS exposés par l'action : ni les boutons ni le message ne sont
        // recomposés ici (ADR 0025). On illustre la variante « dossier hors espace de travail ».
        Alert alerte = new Alert(Alert.AlertType.CONFIRMATION);
        alerte.setHeaderText(ActionImportTransformes.ENTETE_MODE);
        alerte.setContentText(ApercuFx.enrouler(ActionImportTransformes.questionMode(true)));
        ButtonType referencer = new ButtonType(ModeImport.REFERENCER.libelle(), ButtonBar.ButtonData.OTHER);
        ButtonType copier = new ButtonType(ModeImport.COPIER.libelle(), ButtonBar.ButtonData.OTHER);
        ButtonType annuler = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        alerte.getButtonTypes().setAll(referencer, copier, annuler);
        alerte.getDialogPane().setPrefWidth(560);

        ApercuFx.enregistrerDialogPane(alerte.getDialogPane(), styles(), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Feuilles de style partagées (palette + base) pour que le dialogue porte le thème indigo de
    /// l'application, comme [fr.univ_amu.iut.commun.outils.CaptureDialogues].
    private static List<String> styles() {
        List<String> feuilles = new ArrayList<>();
        for (String nom : List.of("palette.css", "base.css")) {
            var url = ConfirmationNavigation.class.getResource(nom);
            if (url != null) {
                feuilles.add(url.toExternalForm());
            }
        }
        return feuilles;
    }
}

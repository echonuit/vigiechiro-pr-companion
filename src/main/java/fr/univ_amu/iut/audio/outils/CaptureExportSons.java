package fr.univ_amu.iut.audio.outils;

import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.commun.view.DialogueProgression;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture la **modale de progression de l'export « observations + sons »** (#2793, EPIC #2790) :
/// `apercu-export-sons-progression.png` - la barre déterminée qui avance fichier par fichier, le nom du
/// son en cours et le bouton « Annuler », seuls signes visibles qu'une archive de plusieurs centaines de
/// Mo peut prendre quelques minutes.
///
/// **Aucune base, aucun injecteur, aucun réseau** (patron [CapturePublicationCorrections]) : la modale ne
/// dépend que de la [Progression] qu'on lui donne. L'étape montrée est celle qu'émet réellement
/// `EcrivainZip` (« Archive : X / N · nom du fichier »), avec les ordres de grandeur du cas réel de la
/// recette (721 sons, 658 Mo) - le contenu vient de [DialogueProgression#apercu], du code de production.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureExportSons {

    private CaptureExportSons() {}

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

    /// La progression **en cours de copie**, 213 sons sur 721 : le libellé est exactement celui
    /// qu'émet `EcrivainZip` pendant l'écriture de l'archive.
    private static void capturer() throws IOException {
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));
        Path fichier = sortie.resolve("apercu-export-sons-progression.png");
        VBox contenu = DialogueProgression.apercu(
                "Export des observations et des sons",
                new Progression("Archive : 213 / 721 · Car130711-2026-Pass1-Z41_223114_000.wav", 213 / 721.0));
        Scene scene = new Scene(contenu);
        scene.getStylesheets().addAll(styles());
        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Feuilles de style partagées (palette indigo + base), comme les autres captures de dialogue : sans
    /// elles, l'image montrerait le thème par défaut de JavaFX et non celui de l'application.
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

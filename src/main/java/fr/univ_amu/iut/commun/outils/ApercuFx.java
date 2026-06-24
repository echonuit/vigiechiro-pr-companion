package fr.univ_amu.iut.commun.outils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Brique reutilisable qui rend une [Scene] JavaFX hors-ecran et l'ecrit en PNG, sans laisser de
/// fenetre a l'ecran. Le patron : placer un ecran (ou le chrome complet) dans une `Scene`, forcer
/// sa mise en page, capturer via [javafx.scene.Scene#snapshot] puis convertir en image AWT par
/// [SwingFXUtils] (d'ou la dependance `javafx.swing`). A appeler sur le thread JavaFX.
///
/// Toute feature peut s'en servir pour produire les apercus de ses ecrans (cf.
/// `fr.univ_amu.iut.sites.outils.CaptureEcrans`).
public final class ApercuFx {

    private ApercuFx() {}

    /// Capture `scene` hors-ecran et l'ecrit en PNG dans `fichier` (cree les dossiers parents).
    ///
    /// La scene est attachee a un [Stage] transitoire que l'on montre brievement : cela garantit une
    /// passe de layout/CSS complete (les controles virtualises comme `TableView` peuplent leurs
    /// lignes) avant le `snapshot`, qui reste deterministe. Le stage est referme aussitot.
    public static void enregistrerPng(Scene scene, Path fichier) {
        Stage stageTransitoire = new Stage();
        stageTransitoire.setScene(scene);
        stageTransitoire.show();
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        WritableImage image = scene.snapshot(null);
        stageTransitoire.hide();
        ecrire(image, fichier);
    }

    private static void ecrire(WritableImage image, Path fichier) {
        try {
            Path parent = fichier.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", fichier.toFile());
        } catch (IOException echec) {
            throw new UncheckedIOException("Ecriture PNG impossible : " + fichier, echec);
        }
    }
}

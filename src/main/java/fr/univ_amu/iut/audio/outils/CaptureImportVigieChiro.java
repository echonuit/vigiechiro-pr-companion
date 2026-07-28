package fr.univ_amu.iut.audio.outils;

import fr.univ_amu.iut.audio.viewmodel.CompteRenduChiffreImportVigieChiro;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.commun.view.PanneauCompteRendu;
import fr.univ_amu.iut.validation.model.BilanImport;
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
/// Capture la **fin d'un import de résultats depuis Vigie-Chiro** (#2651), que rien n'illustrait : la
/// restitution n'était qu'une phrase (« Résultats importés depuis Vigie-Chiro : 128 observation(s). »),
/// et six des sept nombres du bilan y étaient jetés - dont les **validations perdues**.
///
/// **Aucune base, aucun injecteur, aucun réseau.** La bande ne dépend que du [BilanImport] qu'on lui
/// donne : la seule donnée de démonstration est donc ce bilan. Le rendu, lui, passe par le composant de
/// production et ses feuilles réelles (ADR 0025) - un fac-similé assemblé ici n'engagerait personne.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureImportVigieChiro {

    /// Largeur de rendu : celle de la zone de restitution sous le menu de la vue audio.
    private static final int LARGEUR = 900;

    private static final String APERCU_ECRIT = "Apercu ecrit dans ";

    private CaptureImportVigieChiro() {}

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
        rendreCompteRendu(sortie.resolve("apercu-import-vigiechiro-compte-rendu.png"));
    }

    /// Le cas qui a le plus à dire : un **réimport**, seul moment où les validations de l'observateur
    /// peuvent être préservées ou perdues, et où la mention qui manquait prend tout son sens.
    ///
    /// Les quatre registres y paraissent ensemble, et c'est le point : un triangle pour les validations
    /// perdues (du travail disparu), un autre pour les lignes écartées (elles appellent un geste), et deux
    /// mentions d'information pour ce qui n'en appelle aucun - les taxons auto-enregistrés en souches et
    /// les échanges du validateur, qu'il fallait cesser de laisser découvrir par hasard (#1867).
    private static void rendreCompteRendu(Path fichier) {
        BilanImport bilan = new BilanImport(null, 128, 12, 3, 41, 2).avecEchanges(4);
        PanneauCompteRendu bande = new PanneauCompteRendu();
        bande.afficher(CompteRenduChiffreImportVigieChiro.de(bilan, List.of()));
        // Marge autour de la bande : la capture montre le composant tel qu'il s'insère sous le menu.
        VBox cadre = new VBox(bande);
        cadre.setStyle("-fx-padding: 16; -fx-background-color: #f5f6f8;");
        Scene scene = new Scene(cadre, LARGEUR, -1);
        scene.getStylesheets().addAll(styles());
        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
    }

    /// Feuilles de style partagées (palette indigo + base + design de la bande) : sans elles, l'image
    /// montrerait le thème par défaut de JavaFX et non celui de l'application.
    private static List<String> styles() {
        List<String> feuilles = new ArrayList<>();
        for (String nom : List.of("palette.css", "base.css")) {
            var url = ConfirmationNavigation.class.getResource(nom);
            if (url != null) {
                feuilles.add(url.toExternalForm());
            }
        }
        var design = PanneauCompteRendu.class.getResource("design.css");
        if (design != null) {
            feuilles.add(design.toExternalForm());
        }
        return feuilles;
    }
}

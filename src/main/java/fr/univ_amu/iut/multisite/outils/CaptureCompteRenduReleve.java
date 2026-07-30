package fr.univ_amu.iut.multisite.outils;

import fr.univ_amu.iut.commun.model.SuiviTraitement.BilanReleveGroupe;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.view.PanneauCompteRendu;
import fr.univ_amu.iut.multisite.viewmodel.CompteRenduChiffreReleve;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;

/// Aperçus de la bande de compte rendu du **relevé groupé** (#2757).
///
/// ## Pourquoi le composant seul, et non l'écran
///
/// Le harnais de `CaptureMultisite` assemble l'écran **hors connexion** : son injecteur ne fournit aucun
/// `SuiviTraitement`, et le relevé ne peut donc pas y être déclenché. C'est la même limite que la recette
/// consigne pour l'écran de lot (S4-C09) : la moitié d'un écran documenté n'est jamais rendue parce que
/// le harnais n'a pas de réseau.
///
/// D'où le composant seul, comme le font déjà `CaptureCompteRendu` (import) et `CaptureCompteRenduDepot`.
/// Ce qu'on veut voir ici est **la barre**, pas l'écran autour : le défaut corrigé était qu'une proportion
/// se lisait en phrase au lieu de se voir.
///
/// ## Les deux états
///
/// - `apercu-multisite-releve-complet.png` : tout relevé, une seule part. Le cas courant.
/// - `apercu-multisite-releve-partiel.png` : trois nuits injoignables sur douze. La part ocre se voit
///   d'un coup d'œil, là où « 9 sur 12 » demandait de lire - et l'avertissement dit ce que la barre ne
///   peut pas dire, que rien n'est perdu.
///
/// Lancement : `./mvnw exec:exec` avec cette classe en `mainClass` (headless, cf. capture-screenshots.sh).
public final class CaptureCompteRenduReleve {

    /// Largeur d'insertion dans l'écran multisite, sous le tableau.
    private static final int LARGEUR = 900;

    private CaptureCompteRenduReleve() {}

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
        rendre(new BilanReleveGroupe(12, 0), sortie.resolve("apercu-multisite-releve-complet.png"));
        rendre(new BilanReleveGroupe(9, 3), sortie.resolve("apercu-multisite-releve-partiel.png"));
    }

    private static void rendre(BilanReleveGroupe bilan, Path fichier) {
        PanneauCompteRendu bande = new PanneauCompteRendu();
        bande.afficher(CompteRenduChiffreReleve.de(bilan, List.of()));
        // Marge autour du panneau : la capture montre le composant tel qu'il s'insère, pas collé au bord.
        VBox cadre = new VBox(bande);
        cadre.setStyle("-fx-padding: 16; -fx-background-color: #f5f6f8;");
        Scene scene = new Scene(cadre, LARGEUR, -1);
        for (String feuille : List.of("palette.css", "design.css")) {
            var url = PanneauCompteRendu.class.getResource(feuille);
            if (url != null) {
                scene.getStylesheets().add(url.toExternalForm());
            }
        }
        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }
}

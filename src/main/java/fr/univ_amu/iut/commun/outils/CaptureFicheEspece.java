package fr.univ_amu.iut.commun.outils;

import fr.univ_amu.iut.commun.view.Navigateur;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.kordamp.ikonli.javafx.FontIcon;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture les **menus** portant la fonctionnalité « Fiche de l'espèce » (#844), que la capture de scène
/// classique ne saisit pas (ce sont des popups) : on **affiche** le menu sur un `Stage` transitoire
/// (Headless Platform), on l'habille du thème indigo (`palette.css` + `base.css`) puis on **snapshot** le
/// contenu du popup.
///
/// - `apercu-fiche-espece.png` : le menu ☰ de **Sons & validation**, où « Fiche de l'espèce » ouvre la
///   fiche de la proposition Tadarida sélectionnée ;
/// - `apercu-fiche-espece-source.png` : le menu ☰ du **bandeau**, avec la préférence « Fiches espèces sur
///   Wikipédia (sinon GBIF) » qui choisit la source des fiches hors chiroptères (#849).
///
/// Lancement headless : `.github/assets/capture-screenshots.sh`.
public final class CaptureFicheEspece {

    private CaptureFicheEspece() {}

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
        capturerMenu(menuSonsValidation(), "apercu-fiche-espece.png");
        capturerMenu(menuBandeau(), "apercu-fiche-espece-source.png");
    }

    /// Le menu ☰ de Sons & validation (extrait) : « Fiche de l'espèce » y voisine « Voir sur la carte » et
    /// les actions d'import/export.
    private static ContextMenu menuSonsValidation() {
        ContextMenu menu = new ContextMenu();
        menu.getItems()
                .addAll(
                        itemIcone("fas-map", "Voir sur la carte"),
                        new MenuItem("Fiche de l'espèce (Pipistrelle commune)"),
                        new SeparatorMenuItem(),
                        itemIcone("fas-file-import", "Importer un CSV Tadarida…"),
                        itemIcone("fas-file-export", "Exporter les observations (CSV)…"));
        return menu;
    }

    /// Item de menu avec son icône, comme le socle en construit.
    ///
    /// ⚠️ Ces deux menus sont **reconstruits** ici, ce que l'ADR 0025 proscrit : ils dérivent
    /// du produit sans que rien ne rougisse.
    /// C'est exactement ce qui vient de se produire : le passage des pictogrammes aux
    /// icônes (#1933) a laissé ici des libellés qui n'existent plus. Remis d'aplomb faute de
    /// mieux ; le vrai remède est de bâtir ces menus depuis les `ActionMenu` du socle.
    private static MenuItem itemIcone(String iconeLiteral, String libelle) {
        MenuItem item = new MenuItem(libelle);
        item.setGraphic(new FontIcon(iconeLiteral));
        return item;
    }

    /// Le menu ☰ du bandeau (extrait) : la préférence de source des fiches, parmi les outils.
    private static ContextMenu menuBandeau() {
        ContextMenu menu = new ContextMenu();
        menu.getItems()
                .addAll(
                        itemIcone("fas-save", "Sauvegarder la base…"),
                        itemIcone("fas-broom", "Purger les originaux importés…"),
                        new SeparatorMenuItem(),
                        new CheckMenuItem("Fiches espèces sur Wikipédia (sinon GBIF)"),
                        new SeparatorMenuItem(),
                        itemIcone("fas-plug", "Se connecter à Vigie-Chiro…"));
        return menu;
    }

    private static void capturerMenu(ContextMenu menu, String fichier) throws IOException {
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(), 500, 300));
        stage.show();
        menu.show(stage);

        Scene scenePopup = menu.getScene();
        if (scenePopup == null || scenePopup.getRoot() == null) {
            System.out.println("[capture-fiche] popup non rendu (headless) : " + fichier + " ignoré.");
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
        System.out.println("[capture-fiche] écrit " + sortie.toAbsolutePath() + " (" + (int) image.getWidth() + "x"
                + (int) image.getHeight() + ")");
        menu.hide();
    }

    /// Feuilles de style partagées (palette indigo + base, dans `commun/view`), comme les dialogues.
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
}

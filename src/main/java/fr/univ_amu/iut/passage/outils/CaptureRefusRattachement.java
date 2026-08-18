package fr.univ_amu.iut.passage.outils;

import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.view.BandeauRetour;
import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.passage.model.ConseilSiteNonRattache;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/// Aperçu du **refus qui bloque le téléversement** quand le site n'est pas rattaché (#3872).
///
/// ## Pourquoi cet aperçu manquait
///
/// C'est un texte que l'utilisateur lit **au pire moment** - son dépôt vient d'échouer - et c'est le
/// seul endroit qui lui dise quoi faire. Aucun aperçu ne le montrait : les tests vérifient ce que le
/// message **dit**, jamais qu'on peut le **lire**.
///
/// #3854 l'a réécrit pour qu'il conseille un geste **vérifié applicable**, ce qui l'a rendu long, avec
/// un nom de bouton entre guillemets. C'est exactement le contenu où la revue visuelle trouve ses
/// défauts - libellé coupé, ellipse, enroulement qui pousse l'écran vers le bas.
///
/// ## Ce que l'aperçu rend, et ce qu'il ne recopie pas
///
/// Le message vient de [ConseilSiteNonRattache], **le code de production**. L'écrire ici aurait produit
/// une image qui dérive au premier changement de phrase - c'est le mode de panne de #1468, où une
/// capture reconstruite a fini par montrer un protocole qui n'existe pas.
///
/// Le bandeau est celui du socle ([BandeauRetour]), avec du contenu **sous** lui : sans ce contenu, un
/// bandeau de trois lignes ressemble à un choix de mise en page plutôt qu'à un écran dont le tableau a
/// reculé.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureRefusRattachement {

    private static final String CARRE = "130711";
    private static final double LARGEUR = 900;

    private CaptureRefusRattachement() {}

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
        // Le cas courant : le carré EXISTE là-bas en Point Fixe, donc il y a un geste qui aboutit.
        rendre(
                ConseilSiteNonRattache.selonCeQuiExiste(
                        CARRE, List.of(new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-" + CARRE, true))),
                sortie.resolve("apercu-lot-refus-rattachement.png"));
    }

    private static void rendre(String message, Path fichier) {
        Label texte = new Label();
        texte.setWrapText(true);
        texte.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(texte, Priority.ALWAYS);

        Button fermer = new Button("✕");
        fermer.getStyleClass().add("bandeau-retour-fermer");

        HBox bandeau = new HBox(10, texte, fermer);
        bandeau.getStyleClass().add("bandeau-retour");
        BandeauRetour.installer(
                bandeau, texte, fermer, new SimpleObjectProperty<>(RetourOperation.erreur(message)), () -> {});

        VBox cadre = new VBox(12, bandeau, contenuFactice());
        cadre.setStyle("-fx-padding: 16; -fx-background-color: #f5f6f8;");

        Scene scene = new Scene(cadre, LARGEUR, -1);
        for (String feuille : List.of("palette.css", "base.css", "design.css")) {
            var url = ConfirmationNavigation.class.getResource(feuille);
            if (url != null) {
                scene.getStylesheets().add(url.toExternalForm());
            }
        }
        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// De quoi voir que le bandeau **pousse** l'écran, et non qu'il flotte seul.
    private static VBox contenuFactice() {
        Label titre = new Label("Archives prêtes à déposer");
        titre.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Label ligne = new Label("640380-2026-Pass01-A1-20260422.zip · 1,4 Go · générée");
        return new VBox(6, titre, ligne);
    }
}

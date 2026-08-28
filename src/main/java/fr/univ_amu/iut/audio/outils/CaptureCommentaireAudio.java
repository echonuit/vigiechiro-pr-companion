package fr.univ_amu.iut.audio.outils;

import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.view.Habillage;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'**éditeur de commentaire** de la vue « Sons & validation » (`apercu-sons-validation-commentaire.png`,
/// #477) : au clic sur la case commentaire d'une observation, un petit **popup** propose une zone de texte
/// pré-remplie et un bouton « Enregistrer ».
///
/// L'éditeur réel est un [javafx.stage.Popup] (fenêtre séparée) que le `snapshot` d'une scène ne capture
/// pas. On **reconstruit** donc son contenu à l'identique (mêmes libellés, mêmes classes de style que
/// [`EditeurCommentaire`]) et on l'écrit hors-écran, sur un fond assombri qui évoque la case survolée. Le
/// style vient de `sons-validation.css` (classes `popup-commentaire` / `bouton-enregistrer-commentaire`) +
/// palette et base partagées.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureCommentaireAudio {

    private CaptureCommentaireAudio() {}

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
        Path fichier = GraineSonsValidation.dossierSortie().resolve("apercu-sons-validation-commentaire.png");

        TextArea zone = new TextArea("Cri social typique, capté en fin de nuit.");
        zone.setPrefRowCount(3);
        zone.setPrefColumnCount(28);
        zone.setWrapText(true);

        Button enregistrer = new Button("Enregistrer");
        enregistrer.getStyleClass().add("bouton-enregistrer-commentaire");
        Button annuler = new Button("Annuler");
        HBox actions = new HBox(8.0, enregistrer, annuler);

        VBox contenu = new VBox(8.0, new Label("Commentaire de l'observation"), zone, actions);
        contenu.getStyleClass().add("popup-commentaire");

        StackPane conteneur = new StackPane(contenu);
        conteneur.setStyle("-fx-background-color: rgba(0, 0, 0, 0.4); -fx-padding: 30;");
        Scene scene = new Scene(conteneur);
        // Habillage commun (#3374) : la paire palette+base, posée au niveau où la palette vit.
        Habillage.poser(scene);

        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }
}

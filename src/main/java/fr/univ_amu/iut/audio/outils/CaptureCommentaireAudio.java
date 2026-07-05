package fr.univ_amu.iut.audio.outils;

import fr.univ_amu.iut.audio.view.SonsValidationController;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
        scene.getStylesheets().addAll(styles());

        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Feuilles de style : palette + base partagées (thème indigo) et `sons-validation.css` (style propre du
    /// popup de commentaire). Les ressources absentes sont ignorées.
    private static List<String> styles() {
        List<String> feuilles = new ArrayList<>();
        ajouter(feuilles, ancreCssPartagee(), "palette.css");
        ajouter(feuilles, ancreCssPartagee(), "base.css");
        ajouter(feuilles, SonsValidationController.class, "sons-validation.css");
        return feuilles;
    }

    private static void ajouter(List<String> feuilles, Class<?> ancre, String nom) {
        URL url = ancre.getResource(nom);
        if (url != null) {
            feuilles.add(url.toExternalForm());
        }
    }

    /// Classe d'ancrage pour les CSS **partagées** (`commun/view`), sans dépendre d'un type de feature.
    private static Class<?> ancreCssPartagee() {
        return fr.univ_amu.iut.commun.view.ConfirmationNavigation.class;
    }
}

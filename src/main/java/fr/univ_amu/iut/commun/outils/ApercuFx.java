package fr.univ_amu.iut.commun.outils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    /// Variante de [#enregistrerPng] pour les scenes dont le contenu se prepare de facon
    /// **asynchrone** (p. ex. une `AudioView` qui charge un WAV en fond et peint un spectrogramme).
    ///
    /// Le [Stage] transitoire est montre **avant** d'executer `preparation`, qui peut attendre la fin
    /// du chargement via une boucle d'evenements imbriquee. On `snapshot` ensuite la scene **sans
    /// recreer de Stage** : c'est essentiel car la Headless Platform JavaFX 26 refuse un `new Stage()`
    /// apres `enterNestedEventLoop` (le toolkit est laisse dans un etat ou son controle de thread
    /// echoue). En montrant l'unique Stage avant la boucle, on contourne ce defaut. A appeler sur le
    /// thread JavaFX.
    public static void capturerApresPreparation(Scene scene, Runnable preparation, Path fichier) {
        Stage stageTransitoire = new Stage();
        stageTransitoire.setScene(scene);
        stageTransitoire.show();
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        preparation.run();
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

    /// Capture un [javafx.scene.control.DialogPane] hors-ecran en l'enveloppant dans une scene transitoire
    /// et en y appliquant des feuilles de styles (comme palette.css). A appeler sur le thread JavaFX.
    /// Largeur d'enroulement des messages de dialogue, en caractères.
    private static final int LARGEUR_LIGNE = 70;

    /// Insère des retours à la ligne dans un message de dialogue, **sans en changer un mot**.
    ///
    /// Hors `showAndWait`, un `DialogPane` ne contraint pas sa largeur : son libellé reste sur une ligne
    /// unique, que le snapshot coupe par une ellipse. L'enroulement automatique de JavaFX n'opère pas dans
    /// ce contexte - c'est la raison pour laquelle les anciennes captures **réécrivaient** leurs messages,
    /// retours à la ligne compris. Ici, on part du **vrai** message et on se contente de le **couper aux
    /// espaces** : aucun mot n'est ajouté, retiré ni modifié.
    ///
    /// Les retours à la ligne **déjà présents** sont préservés : chaque paragraphe est enroulé pour lui
    /// même. Sans cela, un message en plusieurs paragraphes (celui de la publication, #1865) verrait ses
    /// coupures comptées comme des mots et son découpage partir de travers.
    ///
    /// Vit ici plutôt que dans un outil : c'est une contrainte du **harnais de capture**, pas d'un écran,
    /// et deux outils en ont désormais besoin.
    public static String enrouler(String message) {
        List<String> paragraphes = new ArrayList<>();
        for (String paragraphe : message.split("\n", -1)) {
            paragraphes.add(enroulerParagraphe(paragraphe));
        }
        return String.join("\n", paragraphes);
    }

    private static String enroulerParagraphe(String paragraphe) {
        StringBuilder enroule = new StringBuilder();
        int longueurLigne = 0;
        for (String mot : paragraphe.split(" ")) {
            if (longueurLigne > 0 && longueurLigne + mot.length() > LARGEUR_LIGNE) {
                enroule.append('\n');
                longueurLigne = 0;
            } else if (longueurLigne > 0) {
                enroule.append(' ');
                longueurLigne++;
            }
            enroule.append(mot);
            longueurLigne += mot.length();
        }
        return enroule.toString();
    }

    public static void enregistrerDialogPane(
            javafx.scene.control.DialogPane pane, java.util.List<String> feuillesStyle, Path fichier) {
        javafx.scene.layout.StackPane conteneur = new javafx.scene.layout.StackPane(pane);
        // Fond sombre translucide pour simuler le background de l'application modale
        conteneur.setStyle("-fx-background-color: rgba(0, 0, 0, 0.4); -fx-padding: 30;");
        Scene scene = new Scene(conteneur);
        if (feuillesStyle != null) {
            scene.getStylesheets().addAll(feuillesStyle);
        }
        // applyCss() AVANT layout() (#1468) : sans passe CSS, les libellés n'ont pas encore leurs métriques
        // de police, et un texte à enrouler reste sur une ligne unique - que le snapshot coupe par une
        // ellipse. C'est ce qui obligeait les captures de dialogue à pré-découper leurs messages à la main.
        pane.applyCss();
        pane.layout();
        enregistrerPng(scene, fichier);
    }

    /// Capture un [javafx.scene.control.Dialog] hors-ecran en extrayant son [DialogPane].
    /// A appeler sur le thread JavaFX.
    public static void enregistrerDialog(
            javafx.scene.control.Dialog<?> dialog, java.util.List<String> feuillesStyle, Path fichier) {
        enregistrerDialogPane(dialog.getDialogPane(), feuillesStyle, fichier);
    }
}

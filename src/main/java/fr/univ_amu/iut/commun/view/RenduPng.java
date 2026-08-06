package fr.univ_amu.iut.commun.view;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/// Écrit une [Scene] JavaFX en PNG, hors écran (#2746).
///
/// ## Pourquoi cette classe existe, et pourquoi ici
///
/// Le geste vivait dans `commun.outils.ApercuFx`, c'est-à-dire dans l'**outillage** de capture de la
/// documentation. Or la production s'en sert : l'export d'image de la courbe d'activité (#2352) et
/// celui de la courbe climatique du diagnostic (#2618) passent par [ExportGraphe], qui appelait donc
/// une classe d'outillage.
///
/// C'était le seul franchissement de cette frontière, et il empêchait à la fois de poser la règle
/// ArchUnit qui l'interdit et de retirer l'outillage du binaire distribué.
///
/// ## Ce que cette classe ne fait PAS, et c'est délibéré
///
/// `ApercuFx.enregistrerPng` refuse une image dont un libellé est tronqué
/// ([LisibiliteCapture#refuserToutTexteIllisible]). C'est juste pour une capture de documentation :
/// une image fausse ne doit pas partir dans la doc.
///
/// Ce garde-fou **ne s'applique pas ici**, et pas par oubli. [ExportGraphe] **redessine** le graphe
/// dans une scène transitoire que l'utilisateur ne voit jamais (ADR 2348) : une troncature s'y
/// produirait dans une mise en page qu'il ne peut ni observer ni corriger. Faire échouer son export
/// là-dessus le laisserait sans recours, pour un défaut dont il n'est pas l'auteur.
///
/// Le souci que l'ADR 2348 nomme - « un export qui échoue en silence tout en produisant un fichier
/// d'apparence normale » - est réglé par le redessin lui-même, pas par ce garde-fou.
public final class RenduPng {

    private RenduPng() {}

    /// Rend `scene` hors écran et l'écrit en PNG dans `fichier` (les dossiers parents sont créés).
    ///
    /// La scène est attachée à un [Stage] transitoire que l'on montre brièvement : cela garantit une
    /// passe de layout et de CSS complète avant le `snapshot`, qui reste déterministe. Le stage est
    /// refermé aussitôt. À appeler sur le fil JavaFX.
    public static void enregistrer(Scene scene, Path fichier) {
        // Habillage complet, et non la seule police (#3374) : ce point d'entrée gardait le raisonnement
        // de #3361 - « installer la police suffit » - que #3374 a démenti. Enregistrer une famille ne la
        // **sélectionne** pas : c'est `base.css` qui la demande. Une scène montée hors du chrome rendait
        // donc encore avec celle du système, la police enregistrée en pure perte.
        //
        // Sans effet sur l'unique appelant d'aujourd'hui (`ExportGraphe` passe déjà par `Habillage`),
        // mais le prochain qui ne le ferait pas retomberait dans le défaut sans que rien ne le dise.
        Habillage.poser(scene);
        Stage stageTransitoire = new Stage();
        stageTransitoire.setScene(scene);
        stageTransitoire.show();
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        WritableImage image = scene.snapshot(null);
        stageTransitoire.hide();
        ecrire(image, fichier);
    }

    /// Écrit une image déjà rendue. Exposé pour les captures, qui produisent leur `WritableImage`
    /// autrement (boucle d'événements imbriquée, `DialogPane` enveloppé).
    public static void ecrire(WritableImage image, Path fichier) {
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

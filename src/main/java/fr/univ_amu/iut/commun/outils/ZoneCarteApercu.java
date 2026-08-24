package fr.univ_amu.iut.commun.outils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;

/// Où se trouve la **carte** dans un aperçu, mesuré sur la scène qui vient d'être rendue (#3439).
/// `filtrer-bruit-cartes.sh` compare chaque aperçu à sa version committée **hors** de ce rectangle,
/// à tolérance zéro : le fond OpenStreetMap change presque à chaque exécution, jusqu'à 23,8 % de
/// l'image. Le rectangle est **dérivé de la scène**, pas recopié : l'un des rectangles recopiés
/// était faux des deux côtés à la fois - de la carte laissée dehors, où le bruit repassait, et du
/// texte d'aide effacé, où une régression n'aurait fait rougir personne. Une carte se reconnaît par
/// la classe `carte-sites`, que `CarteSites` se pose ; un composant qui peindrait des tuiles sans y
/// passer échapperait à la détection.
public final class ZoneCarteApercu {

    /// Classe de style que `CarteSites` se pose, et seul point d'accroche de cette mesure.
    private static final String SELECTEUR = ".carte-sites";

    /// Suffixe du fichier déposé à côté du PNG. **Jamais committé** : il est produit par le rendu et
    /// consommé par le filtre, dans la même exécution.
    static final String SUFFIXE = ".carte";

    private ZoneCarteApercu() {}

    /// Le rectangle `x1,y1,x2,y2` couvrant **toutes** les cartes de cette scène, en pixels de l'image
    /// produite - ou vide si la scène n'en porte aucune.
    ///
    /// Les bornes sont **élargies** à l'entier (plancher en haut à gauche, plafond en bas à droite) :
    /// couvrir un pixel de trop est sans conséquence, en manquer un laisse repasser du bruit.
    public static Optional<String> rectangleDe(Scene scene) {
        Set<Node> cartes = scene.getRoot().lookupAll(SELECTEUR);
        if (cartes.isEmpty()) {
            return Optional.empty();
        }
        double x1 = Double.MAX_VALUE;
        double y1 = Double.MAX_VALUE;
        double x2 = -Double.MAX_VALUE;
        double y2 = -Double.MAX_VALUE;
        for (Node carte : cartes) {
            Bounds surLaScene = carte.localToScene(carte.getBoundsInLocal());
            x1 = Math.min(x1, surLaScene.getMinX());
            y1 = Math.min(y1, surLaScene.getMinY());
            x2 = Math.max(x2, surLaScene.getMaxX());
            y2 = Math.max(y2, surLaScene.getMaxY());
        }
        // Une carte peut déborder de la scène (défilement, modale plus haute que sa fenêtre) : le
        // rectangle est ramené dans l'image, sans quoi le masque viserait des pixels qui n'existent pas.
        long gauche = Math.max(0, (long) Math.floor(x1));
        long haut = Math.max(0, (long) Math.floor(y1));
        long droite = Math.min((long) Math.ceil(scene.getWidth()), (long) Math.ceil(x2));
        long bas = Math.min((long) Math.ceil(scene.getHeight()), (long) Math.ceil(y2));
        if (droite <= gauche || bas <= haut) {
            return Optional.empty();
        }
        return Optional.of(gauche + "," + haut + "," + droite + "," + bas);
    }

    /// Dépose `rectangle` à côté de `png`, ou **retire** un fichier devenu obsolète quand `rectangle`
    /// est `null`. Ce second cas est le plus important : sans lui, un écran dont on retire la carte
    /// garderait son masque, et une régression dans cette zone se présenterait sous forme de succès.
    ///
    /// **Le rectangle est passé, et non la scène**, parce que toucher au disque entre le `snapshot` et
    /// la fermeture du stage change les captures **suivantes** du même outil : mesuré, une première
    /// version sortait `apercu-passage-rattachement.png` différent de la CI sur 40 543 pixels. La
    /// mesure se fait donc pendant que la scène est montée, l'écriture après `RenduPng.ecrire`.
    public static void deposer(String rectangle, Path png) {
        Path fichier = png.resolveSibling(png.getFileName() + SUFFIXE);
        try {
            if (rectangle == null) {
                Files.deleteIfExists(fichier);
            } else {
                Files.writeString(fichier, rectangle + System.lineSeparator(), StandardCharsets.UTF_8);
            }
        } catch (IOException echec) {
            throw new UncheckedIOException("Impossible d'écrire la zone de carte de " + png, echec);
        }
    }
}

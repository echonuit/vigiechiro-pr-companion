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
///
/// ## À quoi ça sert
///
/// `filtrer-bruit-cartes.sh` compare chaque aperçu à sa version committée **hors** du rectangle de sa
/// carte, à tolérance zéro : le fond OpenStreetMap change à presque chaque exécution sans qu'aucun code
/// n'ait bougé, et aucun seuil global ne sépare ce bruit du signal - il vaut jusqu'à 23,8 % de l'image.
///
/// ## Pourquoi la scène, et non une liste
///
/// Ces rectangles étaient **recopiés à la main** dans le script. Un rectangle recopié se démode en
/// silence, et c'est arrivé : celui de `apercu-sites-modale-point` déclarait `18,331,464,457` pour une
/// carte réellement en `24,362,535,601`. Il était faux **des deux côtés à la fois** - 144 lignes de
/// carte laissées dehors, où le bruit repassait (8 régénérations sur 20, contre 1 sur 20 pour un masque
/// juste), et 31 lignes de **texte d'aide** effacées, où une régression n'aurait fait rougir personne.
///
/// La scène, elle, sait. Un rectangle **dérivé** ne peut pas se démoder : une modale qui grandit, une
/// carte qu'on allonge, et la zone suit sans que personne n'ait à y penser.
///
/// ## Comment une carte se reconnaît
///
/// Par la classe de style `carte-sites`, que `CarteSites` se pose à elle-même. Les **quatre** surfaces
/// cartographiques du produit passent par ce composant : Multisite, la modale de point, le rattachement
/// à l'import et la répartition de l'écran Analyse. Aucun marqueur n'a donc été ajouté pour l'occasion,
/// et l'outillage ne dépend pas de Gluon Maps.
///
/// ⚠️ Un futur composant qui peindrait des tuiles **sans** passer par `CarteSites` échapperait à cette
/// détection. C'est la limite assumée : elle porte sur le composant, pas sur la présence de tuiles.
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
    /// est `null` - l'aperçu ne porte alors pas de carte.
    ///
    /// Ce second cas est le plus important : sans lui, un écran dont on retire la carte garderait son
    /// masque, et une régression dans cette zone cesserait d'être vue - le défaut se présenterait sous
    /// la forme d'un succès.
    ///
    /// ## ⚠️ Pourquoi le rectangle est passé, et non la scène
    ///
    /// Parce que **toucher au disque entre le `snapshot` et la fermeture du stage change l'image**, et
    /// pas celle qu'on est en train d'écrire : celle des captures **suivantes** du même outil.
    ///
    /// Mesuré, et c'est la seule raison pour laquelle on le sait : une première version appelait cette
    /// méthode avec la scène, juste après le `snapshot`. `apercu-passage-rattachement.png` sortait alors
    /// **différent de la version d'intégration continue**, sur 40 543 pixels - un champ marqué invalide
    /// et un autre porteur du focus. Sans mon changement, le même rendu retombait au bit près sur celui
    /// de la CI. Le délai de l'écriture suffisait à laisser passer une validation de formulaire avant la
    /// capture d'après.
    ///
    /// La mesure se fait donc **pendant** que la scène est montée (des bornes n'ont de sens qu'après
    /// layout), et l'écriture **après** `RenduPng.ecrire`, hors du cycle de vie du stage.
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

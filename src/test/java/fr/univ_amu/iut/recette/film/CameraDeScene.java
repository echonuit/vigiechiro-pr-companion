package fr.univ_amu.iut.recette.film;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.animation.AnimationTimer;
import javafx.scene.Scene;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.Window;

/// Photographie le GRAPHE DE SCÈNE, et non le bureau.
///
/// C'est le renversement dont tout le reste découle. Filmé du dehors, « une fenêtre est-elle à
/// l'écran » se devine par la luminance moyenne de l'image, avec un seuil à recalibrer chaque fois
/// que le banc change de gestionnaire de fenêtres. Filmé du dedans, la question ne se pose plus :
/// [Window#getWindows()] la répond, et [#uneFenetreAParu()] la reporte.
///
/// Corollaire : plus de Xvfb, plus d'openbox, plus de xdotool, plus de retrait de
/// `WAYLAND_DISPLAY`, plus de robot AWT. On filme dans le mode Monocle headless où les tests
/// tournent déjà, donc les cinq préconditions du script n'ont plus d'objet, et le vert qui ne
/// pilote rien n'a plus où se produire.
final class CameraDeScene extends AnimationTimer {

    private static final Color FOND = new Color(0x10, 0x10, 0x14);

    private final int largeur;
    private final int hauteur;
    private final long periodeNs;
    private final BlockingQueue<BufferedImage> file;
    private final AtomicInteger perdues = new AtomicInteger();

    private long dernierDeclenchement;
    private volatile boolean fenetreVue;

    CameraDeScene(int largeur, int hauteur, int imagesParSeconde, BlockingQueue<BufferedImage> file) {
        this.largeur = largeur;
        this.hauteur = hauteur;
        this.periodeNs = 1_000_000_000L / imagesParSeconde;
        this.file = file;
    }

    @Override
    public void handle(long maintenant) {
        if (maintenant - dernierDeclenchement < periodeNs) {
            return;
        }
        dernierDeclenchement = maintenant;
        // Une image qui n'entre pas dans la file est COMPTÉE, jamais tue : un film écourté sans
        // un mot ressemble trait pour trait à un film juste.
        if (!file.offer(composer())) {
            perdues.incrementAndGet();
        }
    }

    /// Le nombre d'images que l'encodeur n'a pas suivies.
    int imagesPerdues() {
        return perdues.get();
    }

    /// Vrai si une fenêtre a été visible au moins une fois pendant l'enregistrement.
    boolean uneFenetreAParu() {
        return fenetreVue;
    }

    /// Compose toutes les fenêtres visibles sur une toile de taille fixe, chacune à sa position.
    /// Une modale par-dessus sa fenêtre parente donne donc la même image que la capture du bureau,
    /// sans le noir autour ni le décor du gestionnaire de fenêtres.
    // ⚠️ Ne pas réutiliser une toile d'une image à l'autre pour économiser l'allocation. La file
    // a une profondeur de 60 et le scribe écrit en différé : écrire par-dessus une toile encore en
    // attente remplacerait une image passée par une image présente, et le film serait faux sans
    // être plus court, donc sans signe. Un bassin de tampons rendus par le scribe après écriture
    // est la seule forme juste, et le producteur y compterait l'absence de tampon libre comme une
    // image perdue, comme il compte déjà le refus de la file.
    private BufferedImage composer() {
        BufferedImage toile = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = toile.createGraphics();
        g.setColor(FOND);
        g.fillRect(0, 0, largeur, hauteur);

        for (Window fenetre : Window.getWindows()) {
            Scene scene = fenetre.getScene();
            if (!fenetre.isShowing() || scene == null || scene.getWidth() <= 0 || scene.getHeight() <= 0) {
                continue;
            }
            fenetreVue = true;
            WritableImage prise = scene.snapshot(null);
            g.drawImage(
                    versAwt(prise),
                    decalage(largeur, (int) prise.getWidth()),
                    decalage(hauteur, (int) prise.getHeight()),
                    null);
        }
        g.dispose();
        return toile;
    }

    /// Le décalage qui CENTRE une fenêtre sur la toile.
    ///
    /// On ne lit pas les coordonnées de la fenêtre, et c'est le résultat d'une mesure. Sous
    /// Monocle, `Window.getX()` situe la fenêtre sur un écran virtuel dont les dimensions ne sont
    /// pas celles de la toile : une scène de 1100×720 était dessinée à **x = -51**, perdant ses
    /// 51 premiers pixels tandis que 231 pixels de toile restaient vides à droite. Le clip montrait
    /// alors « ieChiro Companion » au lieu de « VigieChiro Companion », et « gende » au lieu de
    /// « Légende ».
    ///
    /// Le décalage est modeste, et c'est ce qui le rendait dangereux : un bord amputé de cinquante
    /// pixels se lit comme une mise en page, pas comme un défaut.
    ///
    /// Centrer reproduit fidèlement l'arrangement visuel : une modale se pose au milieu de sa
    /// fenêtre parente, ce qui est exactement là où l'utilisateur la voit.
    ///
    /// Une fenêtre PLUS LARGE que la toile déborde alors des deux côtés à parts égales, plutôt que
    /// de perdre un bord entier. C'est délibéré : un débordement symétrique se remarque, un bord
    /// unique manquant se lit comme une mise en page.
    static int decalage(int toile, int fenetre) {
        return (toile - fenetre) / 2;
    }

    /// Copie les pixels sans passer par `javafx.swing` : le format entier ARGB de JavaFX est
    /// exactement celui du tampon d'un `TYPE_INT_ARGB`, la copie est donc directe.
    private static BufferedImage versAwt(WritableImage source) {
        int l = (int) source.getWidth();
        int h = (int) source.getHeight();
        BufferedImage cible = new BufferedImage(l, h, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = ((DataBufferInt) cible.getRaster().getDataBuffer()).getData();
        source.getPixelReader().getPixels(0, 0, l, h, PixelFormat.getIntArgbInstance(), pixels, 0, l);
        return cible;
    }
}

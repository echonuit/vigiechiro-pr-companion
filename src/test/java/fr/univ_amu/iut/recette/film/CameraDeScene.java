package fr.univ_amu.iut.recette.film;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.animation.AnimationTimer;
import javafx.scene.Scene;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.PopupWindow;
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

    /// La marque qui dit qu'une scène est déjà suivie, posée dans ses propriétés.
    private static final String SUIVI_POSE = "recette.film.suivi";

    /// ⚠️ Trois images à dix par seconde : assez pour qu'un appui se voie, trop court pour
    /// qu'un clip entier paraisse cliqué.
    private static final long HALO_MS = 300;

    /// Assez pour lire un raccourci sans le chercher, et pour qu'il ne survive pas au geste
    /// suivant.
    private static final long BADGE_MS = 800;

    private final int largeur;
    private final int hauteur;
    private final long periodeNs;
    private final BlockingQueue<BufferedImage> file;
    private final AtomicInteger perdues = new AtomicInteger();

    private final Gestes gestes = new Gestes();

    /// La dernière position du pointeur RÉSOLUE sur la toile, gardée pour le cas où sa fenêtre
    /// disparaît.
    private int[] dernierPoint;

    private long dernierDeclenchement;
    private volatile boolean fenetreVue;

    CameraDeScene(int largeur, int hauteur, int imagesParSeconde, BlockingQueue<BufferedImage> file) {
        this.largeur = largeur;
        this.hauteur = hauteur;
        this.periodeNs = 1_000_000_000L / imagesParSeconde;
        this.file = file;
    }

    /// ⚠️ Les filtres se posent AVANT la première image, et non à la première image.
    ///
    /// La version précédente les posait dans `composer()`, donc à la première image composée. Entre
    /// le démarrage de l'enregistrement et cette image-là, il s'écoule une pulsation de JavaFX - et
    /// c'est assez pour perdre un geste, parce que les scénarios commencent souvent par un clic.
    ///
    /// Constaté sur le clip de `S1-27`, dont la première instruction ouvre la modale par le menu :
    /// les deux clics partaient avant que le filtre existe, et le film montrait un menu qui
    /// s'ouvrait **tout seul**, sans pointeur ni halo. Retour d'un relecteur, pas d'un test : c'est
    /// exactement le genre de manque qu'aucune assertion ne voit.
    @Override
    public void start() {
        Window.getWindows().stream()
                .map(Window::getScene)
                .filter(java.util.Objects::nonNull)
                .forEach(this::observerUneFois);
        super.start();
    }

    /// Pose les filtres sur une scène, une seule fois, quel que soit le nombre d'appels.
    private void observerUneFois(Scene scene) {
        if (scene.getProperties().putIfAbsent(SUIVI_POSE, Boolean.TRUE) == null) {
            gestes.observer(scene);
        }
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
        Map<Window, int[]> decalages = new HashMap<>();
        Graphics2D g = toile.createGraphics();
        g.setColor(FOND);
        g.fillRect(0, 0, largeur, hauteur);

        for (Window fenetre : Window.getWindows()) {
            Scene scene = fenetre.getScene();
            if (!fenetre.isShowing() || scene == null || scene.getWidth() <= 0 || scene.getHeight() <= 0) {
                continue;
            }
            fenetreVue = true;
            // Pour les fenêtres qui PARAISSENT en cours de séance : un menu, une modale.
            observerUneFois(scene);
            WritableImage prise = scene.snapshot(null);
            int x = decalage(largeur, (int) prise.getWidth());
            int y = decalage(hauteur, (int) prise.getHeight());
            Window proprietaire = proprietaireDe(fenetre);
            if (proprietaire != null && proprietaire.getScene() != null && positionnee(fenetre, proprietaire)) {
                x = decalageRelatif(
                        largeur, (int) proprietaire.getScene().getWidth(), fenetre.getX() - proprietaire.getX());
                y = decalageRelatif(
                        hauteur, (int) proprietaire.getScene().getHeight(), fenetre.getY() - proprietaire.getY());
            }
            decalages.put(fenetre, new int[] {x, y});
            g.drawImage(versAwt(prise), x, y, null);
        }
        dessinerLesGestes(g, decalages);
        g.dispose();
        return toile;
    }

    /// Pose par-dessus le produit ce que le graphe de scène ne contient pas : le pointeur, son halo,
    /// et le raccourci frappé.
    ///
    /// ⚠️ La position du pointeur est une coordonnée de SCÈNE, donc elle s'ajoute au décalage de la
    /// fenêtre où le geste a eu lieu - celui-là même qui vient d'être calculé. C'est ce qui fait
    /// suivre le pointeur jusque sur un menu, et c'est encore la même leçon : l'absolu ment, le
    /// relatif non.
    private void dessinerLesGestes(Graphics2D g, Map<Window, int[]> decalages) {
        long maintenant = System.currentTimeMillis();
        gestes.pointeur().ifPresent(vu -> {
            dernierPoint = pointSurLaToile(decalages.get(vu.fenetre()), vu.x(), vu.y(), dernierPoint);
            if (dernierPoint == null) {
                return;
            }
            CalqueDesGestes.halo(g, dernierPoint[0], dernierPoint[1], gestes.halo(maintenant, HALO_MS));
            CalqueDesGestes.fleche(g, dernierPoint[0], dernierPoint[1]);
        });
        gestes.badge(maintenant, BADGE_MS).ifPresent(libelle -> CalqueDesGestes.badge(g, largeur, hauteur, libelle));
    }

    /// Où poser le pointeur sur la toile, et que faire quand sa fenêtre n'y est plus.
    ///
    /// ⚠️ Le cas de la fenêtre disparue n'a rien d'exotique : cliquer une entrée de menu **referme
    /// le menu**. À l'image suivante, la fenêtre où le clic a eu lieu n'existe plus.
    ///
    /// La première version rendait la main dans ce cas, par prudence - poser le pointeur dans un
    /// repère qu'on ne connaît plus reviendrait à le poser n'importe où. La prudence coûtait le
    /// geste : relevé sur le clip de `S1-27`, la modale paraissait **par magie**, sans que rien ne
    /// montre le clic qui l'ouvrait. Or le clic qui ferme une fenêtre est justement celui qu'il faut
    /// voir, puisque c'est lui qui explique ce qui suit.
    ///
    /// On garde donc la dernière position **résolue**, qui n'est pas une approximation : c'est
    /// l'endroit exact où le pointeur était à la dernière image où sa fenêtre existait, et il n'a
    /// pas bougé depuis - sans quoi un nouvel événement l'aurait déplacé.
    ///
    /// @param decalage le décalage de la fenêtre du geste, ou `null` si elle n'est plus à l'écran
    /// @param dernierConnu la dernière position résolue, ou `null` si aucun geste n'a encore eu lieu
    static int[] pointSurLaToile(int[] decalage, double x, double y, int[] dernierConnu) {
        if (decalage == null) {
            return dernierConnu;
        }
        return new int[] {decalage[0] + (int) Math.round(x), decalage[1] + (int) Math.round(y)};
    }

    /// La fenêtre dont celle-ci dépend, quand elle en dépend.
    ///
    /// Un menu, une infobulle, la liste d'un `ComboBox` ne sont pas des nœuds de la scène : ce sont
    /// des [PopupWindow] à part entière, que [Window#getWindows()] rend au même titre que la fenêtre
    /// principale. Les CENTRER revient à les détacher du bouton qui les ouvre.
    static Window proprietaireDe(Window fenetre) {
        return fenetre instanceof PopupWindow popup ? popup.getOwnerWindow() : null;
    }

    /// Le décalage d'une fenêtre PORTÉE par une autre : celui de son propriétaire, plus l'écart qui
    /// les sépare.
    ///
    /// Mesuré sur le clip de `S6-27`, avec la scène propriétaire de 1100 de large et le bouton
    /// « + Filtre » à 402 dans cette scène : le menu se pose à 492, bord gauche aligné sur celui de
    /// son bouton. Centré, il se posait à 582, soit le centre exact de la toile, à 90 pixels de là.
    ///
    /// @param toile la largeur (ou la hauteur) du film
    /// @param proprietaire la largeur (ou la hauteur) de la SCÈNE du propriétaire
    /// @param ecart la distance qui sépare les deux fenêtres, dans le repère du système
    static int decalageRelatif(int toile, int proprietaire, double ecart) {
        return decalage(toile, proprietaire) + (int) Math.round(ecart);
    }

    /// Vrai si les deux fenêtres ont une position exploitable.
    ///
    /// ⚠️ On ne lit pas ces coordonnées pour ce qu'elles VALENT - sous Monocle elles situent la
    /// fenêtre sur un écran virtuel étranger à la toile, et c'est ce qui a coûté le bord amputé de
    /// cinquante pixels. On lit leur DIFFÉRENCE, qui est le même vecteur dans n'importe quel
    /// repère. L'absolu ment, le relatif non.
    private static boolean positionnee(Window fenetre, Window proprietaire) {
        return !Double.isNaN(fenetre.getX())
                && !Double.isNaN(fenetre.getY())
                && !Double.isNaN(proprietaire.getX())
                && !Double.isNaN(proprietaire.getY());
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

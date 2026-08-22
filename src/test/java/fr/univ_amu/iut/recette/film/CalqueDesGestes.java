package fr.univ_amu.iut.recette.film;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;

/// Ce que le banc AJOUTE à l'image, par-dessus le produit.
///
/// ⚠️ Tout ce qui est dessiné ici n'existe pas dans l'application. C'est assumé, et c'est la raison
/// d'être de cette classe séparée : un lecteur qui se demande « d'où sort ce halo ? » doit trouver
/// la réponse en un seul fichier, et la revue doit pouvoir dire d'un coup d'oeil ce que le banc
/// pose sur l'écran qu'il filme.
///
/// Le décor se tient à deux règles :
///
/// - le pointeur et son halo se posent **là où le geste a eu lieu**, donc sur l'application ;
/// - le badge se pose dans la **bande basse de la toile**, toujours au même endroit, pour qu'on
///   sache où le chercher sans le chercher.
///
/// ⚠️ Cette bande est de la MARGE quand la fenêtre en laisse. Les scénarios perceptifs ouvrent des
/// fenêtres de 1000 × 700 sur une toile de 1280 × 900 : le badge y flotte dans le noir sans rien
/// masquer. Mais une fenêtre peut remplir la toile - `CarteSitesTest` a été mesuré à 1280 × 900 -
/// et le badge recouvre alors le bas de l'application pendant les 800 ms où il vit.
///
/// C'est assumé, et l'inverse coûterait plus cher : un badge qui se déplacerait selon la place
/// disponible cesserait d'être au même endroit d'un clip à l'autre, et un raccourci qu'on doit
/// chercher est un raccourci qu'on rate. Huit dixièmes de seconde sur le bord bas se paient moins
/// qu'une frappe invisible.
final class CalqueDesGestes {

    /// La flèche, en dixièmes de sa hauteur, pointe à l'origine. Dessinée plutôt qu'embarquée : une
    /// image de curseur serait un fichier de plus à charger, et à ne pas trouver le jour d'un
    /// tournage.
    private static final int[] FLECHE_X = {0, 0, 3, 5, 7, 5, 8};
    private static final int[] FLECHE_Y = {0, 16, 12, 18, 17, 11, 11};

    private static final int TAILLE_FLECHE = 2;
    private static final Color CORPS_FLECHE = new Color(0x11, 0x11, 0x16);
    private static final Color LISERE_FLECHE = Color.WHITE;

    private static final int RAYON_MIN = 10;
    private static final int RAYON_MAX = 34;
    private static final Color COULEUR_HALO = new Color(0x4F, 0xC3, 0xF7);

    private static final int CORPS_BADGE = 22;
    private static final int MARGE_BADGE = 14;
    private static final int BAS_BADGE = 34;
    private static final Color FOND_BADGE = new Color(0x1E, 0x1E, 0x2A);
    private static final Color BORD_BADGE = new Color(0x4F, 0xC3, 0xF7);
    private static final Color TEXTE_BADGE = new Color(0xF2, 0xF2, 0xF8);

    private CalqueDesGestes() {}

    /// Le halo de l'appui, sous la flèche.
    ///
    /// Il GROSSIT en s'effaçant, parce qu'un cercle qui rétrécit se lit comme un point qui s'en va,
    /// là où un cercle qui s'ouvre se lit comme un choc. À trois images, c'est la différence entre
    /// « quelque chose a été touché » et « quelque chose bougeait ».
    static void halo(Graphics2D g, int x, int y, double reste) {
        if (reste <= 0) {
            return;
        }
        int rayon = (int) Math.round(RAYON_MIN + (RAYON_MAX - RAYON_MIN) * (1 - reste));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // ⚠️ DEUX traits, pour la même raison que la flèche a un corps sombre et un liseré clair.
        // Relu sur un vrai clip, un anneau cyan seul se noie dans une table blanche : il était là,
        // et il ne se voyait pas. Le trait sombre au-dessous le détache de tout fond.
        g.setColor(new Color(0x11, 0x11, 0x16, (int) Math.round(150 * reste)));
        g.setStroke(new BasicStroke(6f));
        g.drawOval(x - rayon, y - rayon, rayon * 2, rayon * 2);
        g.setColor(new Color(
                COULEUR_HALO.getRed(), COULEUR_HALO.getGreen(), COULEUR_HALO.getBlue(), (int) Math.round(235 * reste)));
        g.setStroke(new BasicStroke(3f));
        g.drawOval(x - rayon, y - rayon, rayon * 2, rayon * 2);
    }

    /// La flèche, pointe exactement sur la position rapportée.
    ///
    /// ⚠️ Corps sombre et liseré clair, et non l'inverse : un clip traverse des fonds blancs (une
    /// table) et des fonds noirs (un spectrogramme). Une flèche d'une seule couleur disparaît dans
    /// l'un des deux, et c'est toujours celui qu'on regarde.
    static void fleche(Graphics2D g, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Polygon forme = new Polygon();
        for (int sommet = 0; sommet < FLECHE_X.length; sommet++) {
            forme.addPoint(x + FLECHE_X[sommet] * TAILLE_FLECHE, y + FLECHE_Y[sommet] * TAILLE_FLECHE);
        }
        g.setColor(CORPS_FLECHE);
        g.fillPolygon(forme);
        g.setColor(LISERE_FLECHE);
        g.setStroke(new BasicStroke(2.5f));
        g.drawPolygon(forme);
    }

    /// Le badge du raccourci, centré dans la marge basse de la toile.
    static void badge(Graphics2D g, int largeurDeLaToile, int hauteurDeLaToile, String libelle) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Police LOGIQUE, comme le carton : toujours résolue, sur tout poste et sans fontconfig.
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, CORPS_BADGE));
        FontMetrics mesure = g.getFontMetrics();

        int largeur = mesure.stringWidth(libelle) + MARGE_BADGE * 2;
        int hauteur = mesure.getHeight() + MARGE_BADGE;
        int x = (largeurDeLaToile - largeur) / 2;
        int y = hauteurDeLaToile - BAS_BADGE - hauteur;

        g.setColor(FOND_BADGE);
        g.fillRoundRect(x, y, largeur, hauteur, 10, 10);
        g.setColor(BORD_BADGE);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, largeur, hauteur, 10, 10);
        g.setColor(TEXTE_BADGE);
        g.drawString(libelle, x + MARGE_BADGE, y + hauteur - MARGE_BADGE / 2 - mesure.getDescent() + 2);
    }
}

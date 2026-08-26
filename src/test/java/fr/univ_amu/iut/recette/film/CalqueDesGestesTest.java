package fr.univ_amu.iut.recette.film;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Ce que le calque pose sur l'image, éprouvé en le regardant.
///
/// ## Pourquoi des pixels et non des appels
///
/// Un `Graphics2D` simulé dirait que `fillPolygon` a été appelé. Il ne dirait pas que quelque chose
/// se voit, ce qui est la seule question qui vaille pour un décor de clip : un dessin de la bonne
/// couleur sur un fond de la même couleur passe tous les tests d'appel du monde.
///
/// On dessine donc sur une vraie toile et on relit les pixels, en AWT pur - ni JavaFX, ni `ffmpeg`,
/// ni scène montée.
class CalqueDesGestesTest {

    private static final int LARGEUR = 1280;
    private static final int HAUTEUR = 900;

    /// La toile du film, remplie du fond que la caméra emploie.
    private static BufferedImage toile() {
        BufferedImage image = new BufferedImage(LARGEUR, HAUTEUR, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x10, 0x10, 0x14));
        g.fillRect(0, 0, LARGEUR, HAUTEUR);
        g.dispose();
        return image;
    }

    private static boolean peint(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) != new Color(0x10, 0x10, 0x14).getRGB();
    }

    /// Le rectangle des pixels peints, ou rien si la toile est intacte.
    private static int[] etendue(BufferedImage image) {
        int minX = LARGEUR;
        int minY = HAUTEUR;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < HAUTEUR; y++) {
            for (int x = 0; x < LARGEUR; x++) {
                if (peint(image, x, y)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new int[] {minX, minY, maxX, maxY};
    }

    @Test
    @DisplayName("la flèche se voit, et sa pointe tombe sur la position rapportée")
    void la_fleche_se_voit_et_sa_pointe_tombe_sur_la_position() {
        BufferedImage image = toile();
        Graphics2D g = image.createGraphics();

        CalqueDesGestes.fleche(g, 400, 300);
        g.dispose();

        // La tolérance n'est pas un arrondi commode : le liseré fait 2,5 px et se dessine À CHEVAL
        // sur le contour, donc il déborde de la pointe d'un peu plus d'un pixel, antialiasage
        // compris. Ce qui est éprouvé ici reste entier : la flèche est ANCRÉE par sa pointe et
        // s'étend vers le bas à droite. Une flèche centrée sur le point commencerait seize pixels
        // plus haut, et ce cas rougirait.
        int lisere = 4;
        int[] cadre = etendue(image);
        assertThat(cadre[2]).as("quelque chose a été peint").isNotEqualTo(-1);
        assertThat(cadre[0]).as("rien à gauche de la pointe, au liseré près").isGreaterThanOrEqualTo(400 - lisere);
        assertThat(cadre[1]).as("rien au-dessus de la pointe, au liseré près").isGreaterThanOrEqualTo(300 - lisere);
        assertThat(cadre[3]).as("et elle descend, comme une flèche").isGreaterThan(300 + 20);
        assertThat(cadre[2] - cadre[0]).as("une flèche a une largeur lisible").isBetween(10, 40);
    }

    /// Le cas qui empêche un halo perpétuel. Sans lui, un halo dessiné à `reste = 0` marquerait
    /// toutes les images qui suivent un clic, et un clip entier paraîtrait cliqué de bout en bout.
    @Test
    @DisplayName("un halo éteint ne peint rien du tout")
    void un_halo_eteint_ne_peint_rien() {
        BufferedImage image = toile();
        Graphics2D g = image.createGraphics();

        CalqueDesGestes.halo(g, 400, 300, 0);
        g.dispose();

        assertThat(etendue(image)[2]).as("aucun pixel peint").isEqualTo(-1);
    }

    @Test
    @DisplayName("un halo vif se voit, et il grossit en s'effaçant")
    void un_halo_vif_se_voit_et_grossit_en_s_effacant() {
        BufferedImage vif = toile();
        Graphics2D gv = vif.createGraphics();
        CalqueDesGestes.halo(gv, 400, 300, 1.0);
        gv.dispose();

        BufferedImage pale = toile();
        Graphics2D gp = pale.createGraphics();
        CalqueDesGestes.halo(gp, 400, 300, 0.2);
        gp.dispose();

        int largeurVif = etendue(vif)[2] - etendue(vif)[0];
        int largeurPale = etendue(pale)[2] - etendue(pale)[0];
        assertThat(largeurVif).as("le halo se voit à l'appui").isPositive();
        assertThat(largeurPale)
                .as("un cercle qui s'ouvre se lit comme un choc ; un cercle qui rétrécit, comme un départ")
                .isGreaterThan(largeurVif);
    }

    /// Le badge tient dans la bande basse de la toile, sous la ligne des 800 pixels.
    ///
    /// Cette page a d'abord affirmé que le badge « ne couvre jamais l'application », et c'était
    /// FAUX. Ce n'est vrai que d'une fenêtre plus petite que la toile - les scénarios perceptifs
    /// ouvrent du 1000 × 700 sur du 1280 × 900. `CarteSitesTest`, mesuré sur un vrai clip, ouvre du
    /// **1280 × 900** : le badge y recouvre le bas de l'application pendant ses 800 ms.
    ///
    /// Ce que ce cas garde est donc la seule chose qui soit vraie et qui compte : le badge est
    /// TOUJOURS au même endroit. Un badge qui se déplacerait selon la place disponible cesserait
    /// d'être là où l'oeil le cherche.
    @Test
    @DisplayName("le badge tient dans la bande basse, toujours au même endroit")
    void le_badge_tient_dans_la_bande_basse() {
        BufferedImage image = toile();
        Graphics2D g = image.createGraphics();

        CalqueDesGestes.badge(g, LARGEUR, HAUTEUR, "Ctrl + Maj + Alt + S");
        g.dispose();

        int[] cadre = etendue(image);
        assertThat(cadre[2]).as("le badge se voit").isNotEqualTo(-1);
        assertThat(cadre[1])
                .as("le badge reste dans la bande basse, sous la ligne des 800 pixels")
                .isGreaterThan(800);
        assertThat(cadre[3]).as("et le bas doit tenir dans la toile").isLessThan(HAUTEUR);
    }

    @Test
    @DisplayName("le badge est centré, quelle que soit la longueur du libellé")
    void le_badge_est_centre() {
        for (String libelle : new String[] {"R", "Ctrl + Maj + Alt + S"}) {
            BufferedImage image = toile();
            Graphics2D g = image.createGraphics();
            CalqueDesGestes.badge(g, LARGEUR, HAUTEUR, libelle);
            g.dispose();

            int[] cadre = etendue(image);
            int centre = (cadre[0] + cadre[2]) / 2;
            assertThat(centre)
                    .as("« %s » doit être centré sur la toile", libelle)
                    .isBetween(LARGEUR / 2 - 3, LARGEUR / 2 + 3);
        }
    }
}

package fr.univ_amu.iut.recette.film;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Ce que ces cas gardent, et pourquoi ils regardent des PIXELS alors que le voisin s'en méfie.
///
/// [CartonDeTitreTest] explique qu'il a cessé de compter des pixels, parce que compter de l'encre dit
/// qu'il y a quelque chose d'écrit, pas ce qui est écrit. Ici c'est l'inverse : ce qui est en jeu
/// **est** un aspect. La consigne du chantier était la ressemblance avec la décoration du banc bash,
/// mesurée sur `.github/assets/parcours-declarer-un-carre.mp4`, et une ressemblance ne se garde pas
/// autrement qu'en regardant ce qui est peint.
///
/// La ligne de partage est donc : on n'affirme jamais « il y a de l'encre », on affirme **quelle
/// couleur à quel endroit**, et chaque nombre attendu vient de la mesure, pas du code.
class DecorationDeFenetreTest {

    private static final int LARGEUR = 300;
    private static final int HAUTEUR = 120;

    /// Une toile où la scène occupe (x=10, y=30), avec de la place autour pour le cadre.
    private static BufferedImage toileDecoree(String titre, boolean focalisee) {
        BufferedImage toile = new BufferedImage(LARGEUR + 40, HAUTEUR + 80, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = toile.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, toile.getWidth(), toile.getHeight());
        DecorationDeFenetre.dessiner(g, 10, 30, LARGEUR, HAUTEUR, titre, focalisee);
        g.dispose();
        return toile;
    }

    @Test
    @DisplayName("le cadre mesure 21 px au-dessus de la scène : 20 de barre et 1 de bord")
    void la_hauteur_du_cadre_est_celle_mesuree() {
        // 20 px est relevé sur la barre de la MODALE du film bash, la seule qui soit entière : celle
        // de la fenêtre principale y est coupée par le bord de l'écran, et paraît de 12.
        assertEquals(20, DecorationDeFenetre.BARRE);
        assertEquals(1, DecorationDeFenetre.BORD);
        assertEquals(21, DecorationDeFenetre.hauteurDuCadre());
    }

    @Test
    @DisplayName("la décoration ne peint RIEN sur la scène : elle l'entoure")
    void la_decoration_n_empiete_pas_sur_la_scene() {
        BufferedImage toile = toileDecoree("Site de suivi", true);
        // La zone de la scène est restée noire : c'est `composer()` qui y posera l'image.
        for (int y = 30; y < 30 + HAUTEUR; y += 7) {
            for (int x = 10; x < 10 + LARGEUR; x += 11) {
                assertEquals(
                        Color.BLACK.getRGB(),
                        toile.getRGB(x, y),
                        "la décoration a peint en (" + x + ", " + y + "), donc sur la scène");
            }
        }
    }

    @Test
    @DisplayName("la barre est BLEUE quand la fenêtre a le focus, GRISE quand elle ne l'a pas")
    void le_focus_change_la_couleur_de_la_barre() {
        // Ce cas garde le signal qui dit OÙ VA LA FRAPPE. Sur les films du banc bash, la barre de la
        // fenêtre principale passe du bleu au gris quand la modale s'ouvre, et redevient bleue quand
        // elle se ferme : un spectateur qui suit une saisie lit cela sans y penser.
        int y = 30 - DecorationDeFenetre.hauteurDuCadre() + 10;
        Color focalisee = new Color(toileDecoree("", true).getRGB(30, y));
        Color endormie = new Color(toileDecoree("", false).getRGB(30, y));

        assertTrue(
                focalisee.getBlue() > focalisee.getRed() + 25,
                "la barre focalisée devrait tirer sur le bleu, elle vaut " + focalisee);
        assertTrue(
                Math.abs(endormie.getBlue() - endormie.getRed()) < 15,
                "la barre endormie devrait être grise, elle vaut " + endormie);
        assertNotEquals(focalisee, endormie);
    }

    @Test
    @DisplayName("un titre vide laisse la barre NUE, sans nom inventé")
    void un_titre_vide_ne_peint_aucun_texte() {
        // ADR 2748 : un dispositif qui ne sait pas dit qu'il ne sait pas. Une fenêtre sans titre est un
        // fait du scénario ; lui en poser un ferait passer un oubli pour une intention.
        BufferedImage nue = toileDecoree("", true);
        BufferedImage titree = toileDecoree("Site de suivi", true);
        assertEquals(0, encreDuTitre(nue), "une barre sans titre ne devrait porter aucun texte");
        assertTrue(encreDuTitre(titree) > 0, "une barre titrée devrait porter du texte");
    }

    /// Compte les pixels CLAIRS au centre de la barre, là où le titre se pose - et nulle part ailleurs,
    /// pour ne pas compter les boutons, qui sont à droite.
    private static int encreDuTitre(BufferedImage toile) {
        int haut = 30 - DecorationDeFenetre.hauteurDuCadre() + 1;
        int compte = 0;
        for (int y = haut; y < haut + DecorationDeFenetre.BARRE; y++) {
            for (int x = 10 + LARGEUR / 3; x < 10 + 2 * LARGEUR / 3; x++) {
                if (new Color(toile.getRGB(x, y)).getRed() > 230) {
                    compte++;
                }
            }
        }
        return compte;
    }

    @Test
    @DisplayName("les trois boutons sont dessinés, collés au bord droit")
    void les_trois_boutons_sont_a_droite() {
        BufferedImage toile = toileDecoree("", true);
        int haut = 30 - DecorationDeFenetre.hauteurDuCadre() + 1;
        int aDroite = 0;
        int aGauche = 0;
        for (int y = haut; y < haut + DecorationDeFenetre.BARRE; y++) {
            for (int x = 10; x < 10 + LARGEUR; x++) {
                if (new Color(toile.getRGB(x, y)).getRed() > 230) {
                    if (x > 10 + LARGEUR - 70) {
                        aDroite++;
                    } else {
                        aGauche++;
                    }
                }
            }
        }
        assertTrue(aDroite > 0, "les boutons devraient être peints dans les 70 px de droite");
        assertEquals(0, aGauche, "sans titre, rien ne devrait être peint ailleurs que sur les boutons");
    }
}

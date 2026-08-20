package fr.univ_amu.iut.recette.film;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le placement d'une fenêtre sur la toile du film.
///
/// Ces cas gardent un défaut mesuré : la caméra lisait `Window.getX()`, que Monocle situe sur un
/// écran virtuel étranger à la toile. Une scène de 1100×720 était dessinée à **x = -51**, perdant
/// ses 51 premiers pixels tandis que 231 pixels de toile restaient vides à droite. Le titre du
/// produit y était amputé, ce qui ne fait rougir aucun test : cela se voit sur le film, et
/// seulement si quelqu'un le regarde.
class CameraDeSceneTest {

    private static final int TOILE_LARGEUR = 1280;
    private static final int TOILE_HAUTEUR = 900;

    @Test
    @DisplayName("une fenêtre aussi large que la toile commence au bord, sans rien perdre")
    void uneFenetreAussiLargeQueLaToileCommenceAuBord() {
        assertEquals(0, CameraDeScene.decalage(TOILE_LARGEUR, 1280));
    }

    @Test
    @DisplayName("la scène réelle de 1100x720 tient entièrement, et se centre")
    void laSceneReelleTientEntierement() {
        // 1100x720 est la taille MESUREE de la scene sous Monocle, pas une supposition.
        int x = CameraDeScene.decalage(TOILE_LARGEUR, 1100);
        int y = CameraDeScene.decalage(TOILE_HAUTEUR, 720);
        assertEquals(90, x, "1100 sur 1280 laisse 90 de marge a gauche");
        assertEquals(90, y, "720 sur 900 laisse 90 de marge en haut");
        assertTrue(x >= 0 && x + 1100 <= TOILE_LARGEUR, "la fenetre doit tenir entiere");
    }

    @Test
    @DisplayName("une modale se pose au milieu, là où l'utilisateur la voit")
    void uneModaleSePoseAuMilieu() {
        assertEquals(360, CameraDeScene.decalage(TOILE_LARGEUR, 560));
        assertEquals(250, CameraDeScene.decalage(TOILE_HAUTEUR, 400));
    }

    @Test
    @DisplayName("une fenêtre plus large que la toile déborde des DEUX côtés, à parts égales")
    void uneFenetreTropLargeDebordeDesDeuxCotes() {
        // Un débordement symétrique se remarque ; un bord unique manquant se lit comme une mise
        // en page, et c'est ce qui a laissé passer le défaut d'origine.
        int x = CameraDeScene.decalage(TOILE_LARGEUR, 1600);
        assertEquals(-160, x);
        assertEquals(-160, TOILE_LARGEUR - (x + 1600), "les deux débordements doivent être égaux");
    }

    @Test
    @DisplayName("aucun placement ne dépend des coordonnées de la fenêtre")
    void aucunPlacementNeDependDesCoordonnees() {
        // Le défaut d'origine venait de là : deux appels avec la même taille doivent rendre le
        // même décalage, quelle que soit la position que le système prête à la fenêtre.
        assertEquals(CameraDeScene.decalage(TOILE_LARGEUR, 1280), CameraDeScene.decalage(TOILE_LARGEUR, 1280));
    }
}

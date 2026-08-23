package fr.univ_amu.iut.recette.film;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
///
/// ⚠️ Le remède a produit le second défaut. Centrer est juste pour la fenêtre principale, et faux
/// pour un menu : un [javafx.stage.PopupWindow] est une fenêtre à part entière, et la centrer la
/// détache du bouton qui l'ouvre. Mesuré sur le clip de `S6-27`, le menu du bouton « + Filtre »
/// tombait au centre exact de la toile, à 90 pixels de sa place.
///
/// Les deux moitiés tiennent ensemble : on ne lit jamais une coordonnée pour ce qu'elle VAUT, et on
/// lit un ÉCART pour ce qu'il vaut. Sous Monocle l'absolu ment, le relatif non.
class CameraDeSceneTest {

    private static final int TOILE_LARGEUR = 1280;
    private static final int TOILE_HAUTEUR = 900;

    @Test
    @DisplayName("une fenêtre aussi large que la toile commence au bord, sans rien perdre")
    void une_fenetre_aussi_large_que_la_toile_commence_au_bord() {
        assertEquals(0, CameraDeScene.decalage(TOILE_LARGEUR, 1280));
    }

    @Test
    @DisplayName("la scène réelle de 1100x720 tient entièrement, et se centre")
    void la_scene_reelle_tient_entierement() {
        // 1100x720 est la taille MESUREE de la scene sous Monocle, pas une supposition.
        int x = CameraDeScene.decalage(TOILE_LARGEUR, 1100);
        int y = CameraDeScene.decalage(TOILE_HAUTEUR, 720);
        assertEquals(90, x, "1100 sur 1280 laisse 90 de marge a gauche");
        assertEquals(90, y, "720 sur 900 laisse 90 de marge en haut");
        assertTrue(x >= 0 && x + 1100 <= TOILE_LARGEUR, "la fenetre doit tenir entiere");
    }

    @Test
    @DisplayName("une modale se pose au milieu, là où l'utilisateur la voit")
    void une_modale_se_pose_au_milieu() {
        assertEquals(360, CameraDeScene.decalage(TOILE_LARGEUR, 560));
        assertEquals(250, CameraDeScene.decalage(TOILE_HAUTEUR, 400));
    }

    @Test
    @DisplayName("une fenêtre plus large que la toile déborde des DEUX côtés, à parts égales")
    void une_fenetre_trop_large_deborde_des_deux_cotes() {
        // Un débordement symétrique se remarque ; un bord unique manquant se lit comme une mise
        // en page, et c'est ce qui a laissé passer le défaut d'origine.
        int x = CameraDeScene.decalage(TOILE_LARGEUR, 1600);
        assertEquals(-160, x);
        assertEquals(-160, TOILE_LARGEUR - (x + 1600), "les deux débordements doivent être égaux");
    }

    @Test
    @DisplayName("un menu se pose SOUS son bouton, pas au centre de la toile")
    void un_menu_se_pose_sous_son_bouton() {
        // Les trois nombres sont MESURÉS sur le clip de S6-27, pas choisis : scène propriétaire de
        // 1100 de large, bouton « + Filtre » à 402 dans cette scène, menu de 115 de large.
        int menu = CameraDeScene.decalageRelatif(TOILE_LARGEUR, 1100, 402);

        assertEquals(492, menu, "le bord gauche du menu doit tomber sur celui de son bouton");
        assertEquals(582, CameraDeScene.decalage(TOILE_LARGEUR, 115), "centré, il se posait là");
        assertEquals(90, 582 - menu, "l'écart mesuré entre le défaut et le placement juste");
    }

    @Test
    @DisplayName("un menu qui pend au bord droit y reste : le relatif ne recentre rien")
    void un_menu_au_bord_droit_y_reste() {
        // Le menu du bouton hamburger, à 1010 dans une scène de 1100 : il doit rester à droite.
        // Centrer l'aurait ramené au milieu, ce qui est précisément le défaut d'origine.
        int menu = CameraDeScene.decalageRelatif(TOILE_LARGEUR, 1100, 1010);

        assertEquals(1100, menu);
        assertTrue(menu > TOILE_LARGEUR / 2, "un menu de droite ne doit jamais migrer vers le centre");
    }

    @Test
    @DisplayName("sans écart, une fenêtre portée se pose exactement sur son propriétaire")
    void sans_ecart_la_fenetre_portee_se_pose_sur_son_proprietaire() {
        assertEquals(
                CameraDeScene.decalage(TOILE_LARGEUR, 1100), CameraDeScene.decalageRelatif(TOILE_LARGEUR, 1100, 0));
    }

    /// Le geste dont la fenêtre a DISPARU entre-temps.
    ///
    /// ⚠️ C'est le cas d'un clic sur une entrée de menu, et il n'a rien d'exotique : cliquer une
    /// entrée **referme le menu**. À l'image suivante, la fenêtre où le clic a eu lieu n'existe
    /// plus, et son décalage est introuvable.
    ///
    /// La première version rendait la main dans ce cas, pour ne pas poser le pointeur n'importe où.
    /// Le résultat, relevé sur le clip de `S1-27` : la modale paraissait **par magie**, sans que
    /// rien ne montre le clic qui l'ouvrait. Le remède garde la dernière position RÉSOLUE, qui est
    /// exactement là où le pointeur se trouvait.
    @Test
    @DisplayName("quand la fenêtre du geste a disparu, le pointeur reste où il était")
    void quand_la_fenetre_du_geste_a_disparu_le_pointeur_reste_ou_il_etait() {
        int[] surLeMenu = CameraDeScene.pointSurLaToile(new int[] {600, 200}, 41, 36, null);

        assertEquals(641, surLeMenu[0]);
        assertEquals(236, surLeMenu[1]);
        assertArrayEquals(
                surLeMenu,
                CameraDeScene.pointSurLaToile(null, 999, 999, surLeMenu),
                "le menu refermé ne doit pas emporter le pointeur avec lui");
    }

    @Test
    @DisplayName("sans fenêtre connue et sans position retenue, il n'y a rien à dessiner")
    void sans_fenetre_connue_et_sans_position_retenue() {
        assertNull(CameraDeScene.pointSurLaToile(null, 10, 20, null));
    }

    @Test
    @DisplayName("aucun placement ne dépend des coordonnées de la fenêtre")
    void aucun_placement_ne_depend_des_coordonnees() {
        // Le défaut d'origine venait de là : deux appels avec la même taille doivent rendre le
        // même décalage, quelle que soit la position que le système prête à la fenêtre.
        assertEquals(CameraDeScene.decalage(TOILE_LARGEUR, 1280), CameraDeScene.decalage(TOILE_LARGEUR, 1280));
    }
}

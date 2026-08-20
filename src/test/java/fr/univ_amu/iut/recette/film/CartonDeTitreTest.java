package fr.univ_amu.iut.recette.film;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Ce que ces cas éprouvent, et pourquoi ils ne ressemblent pas à ceux du script.
///
/// Le script tenait trois cas sur le carton : il dure deux secondes, il porte des pixels
/// clairs, et tesseract y relit une bribe. Les deux premiers ont laissé passer TROIS cartons faux,
/// parce que compter des pixels dit qu'il y a de l'encre, pas ce qui est écrit ; le troisième
/// demandait un tesseract, une locale française et une image extraite.
///
/// Ici, l'assertion porte sur des CHAÎNES. L'invariant qui compte (aucun mot n'est perdu au
/// repli) se vérifie sans rien rendre, et les trois défauts historiques deviennent trois cas qui
/// tournent en une milliseconde.
class CartonDeTitreTest {

    private static final String LIBELLE =
            "La modale de connexion s'ouvre sans saut visuel et rend la saisie au premier champ";

    private static FontMetrics metrique() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)
                .createGraphics()
                .getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 30));
    }

    @Test
    @DisplayName("le repli ne perd AUCUN mot, y compris le dernier")
    void leRepliNePerdAucunMot() {
        List<String> lignes = CartonDeTitre.replier(LIBELLE, metrique(), 400);
        assertTrue(lignes.size() > 1, "le libellé devrait se replier à cette largeur");
        assertEquals(LIBELLE, String.join(" ", lignes));
    }

    @Test
    @DisplayName("un mot plus large que la ligne occupe la sienne, il n'est pas tronqué")
    void unMotTropLargeNestPasTronque() {
        String mot = "anticonstitutionnellement".repeat(3);
        List<String> lignes = CartonDeTitre.replier(mot, metrique(), 100);
        assertEquals(List.of(mot), lignes);
    }

    @Test
    @DisplayName("un libellé absent ne fait pas échouer le carton")
    void unLibelleAbsentNeCassePas() {
        assertEquals(List.of(), CartonDeTitre.replier("", metrique(), 400));
        assertEquals(List.of(), CartonDeTitre.replier(null, metrique(), 400));
    }

    @Test
    @DisplayName("le carton est aux dimensions du film, en bgr24")
    void leCartonEstAuFormatDuFilm() {
        BufferedImage carton = CartonDeTitre.dessiner(1280, 900, "S1-26", LIBELLE, "Exemple");
        assertEquals(1280, carton.getWidth());
        assertEquals(900, carton.getHeight());
        // Le format importe : l'encodeur pousse le tampon tel quel dans le rawvideo de ffmpeg.
        assertEquals(BufferedImage.TYPE_3BYTE_BGR, carton.getType());
    }

    @Test
    @DisplayName("du texte y paraît, et un carton vide se distingue d'un carton plein")
    void leCartonPorteDeLEncre() {
        assertTrue(CartonDeTitre.porteDeLEncre(CartonDeTitre.dessiner(640, 360, "S1-26", LIBELLE, "Exemple")));
        // Le témoin : sans ce cas, « porte de l'encre » pourrait être vrai partout, et le cas
        // ci-dessus resterait vert sur un carton qui n'affiche rien.
        assertFalse(
                CartonDeTitre.porteDeLEncre(new BufferedImage(64, 36, BufferedImage.TYPE_3BYTE_BGR)),
                "une image uniforme ne porte rien");
    }
}

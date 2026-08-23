package fr.univ_amu.iut.recette.film;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;

/// La typographie du produit, rendue disponible à AWT.
///
/// `commun.view.Typographie` embarque Noto Sans dans le jar et l'enregistre auprès de **JavaFX**, par
/// `Font.loadFont`. AWT n'en sait rien : tout ce que le banc dessine par-dessus l'image rendue (le
/// carton-titre, le badge des raccourcis) se rendait donc dans une police que la **machine** choisissait.
/// Sur le runner de la CI, la police logique `SANS_SERIF` tombait sur une serif (#4241).
///
/// ## Pourquoi charger, et pas enregistrer
///
/// `GraphicsEnvironment.registerFont` rendrait la police visible à tout AWT, y compris à du code qui ne
/// l'a pas demandée, et son effet dépendrait de l'ordre d'appel. Ici on **tient** l'objet `Font` et on en
/// dérive les corps : le dessin ne peut pas se tromper de police, et rien d'autre n'est affecté.
///
/// ## La propriété qu'on garde
///
/// La police logique avait été choisie pour une raison écrite dans le code : « toujours résolue, sur
/// tout poste et sans fontconfig ». Le fichier embarqué la garde, puisqu'il voyage dans le jar. On ne
/// troque donc pas une garantie contre une autre : on ajoute la bonne typographie à celle qu'on avait.
final class PoliceDuBanc {

    /// ⚠️ Chargées une fois. `Font.createFont` lit le flux entier à chaque appel, et le banc dessine
    /// jusqu'à dix images par seconde de tournage.
    private static final Font GRASSE = charger("/fonts/NotoSans-Bold.ttf");

    private static final Font NORMALE = charger("/fonts/NotoSans-Regular.ttf");

    private PoliceDuBanc() {}

    /// La graisse forte, au corps demandé.
    ///
    /// ⚠️ Sans `Font.BOLD` : la fonte **est** la graisse forte, et redemander le style ferait épaissir
    /// AWT par-dessus une fonte déjà grasse.
    static Font grasse(int corps) {
        return GRASSE.deriveFont((float) corps);
    }

    /// La graisse normale, au corps demandé.
    static Font normale(int corps) {
        return NORMALE.deriveFont((float) corps);
    }

    /// ⚠️ Échoue fort. Une police absente rendrait un clip lisible mais faux, et
    /// [ADR 0008](../../../../../../../../dev-docs/decisions/0008-echec-silencieux.md) refuse qu'un
    /// dispositif se rabatte en silence sur autre chose que ce qu'on lui a demandé.
    private static Font charger(String ressource) {
        try (InputStream flux = PoliceDuBanc.class.getResourceAsStream(ressource)) {
            if (flux == null) {
                throw new IllegalStateException(
                        "La police %s n'est pas sur le classpath : le banc dessinerait dans une police du poste."
                                .formatted(ressource));
            }
            return Font.createFont(Font.TRUETYPE_FONT, flux);
        } catch (IOException | FontFormatException echec) {
            throw new IllegalStateException("Police du banc illisible : " + ressource, echec);
        }
    }
}

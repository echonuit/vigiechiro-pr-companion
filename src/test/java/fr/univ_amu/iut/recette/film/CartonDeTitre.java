package fr.univ_amu.iut.recette.film;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/// Le carton d'ouverture d'un clip : son identifiant, ce qu'il montre, et la classe qui le filme.
///
/// Il est dessiné dans le MÊME format que le reste du film et poussé dans le MÊME encodeur.
/// Il n'y a donc plus de recollage : la famille de défauts liés à `concat -c copy` (un
/// carton d'une autre taille accepté sans un mot, un film qui se déclare aux dimensions du carton)
/// n'a plus où se produire, et le cas d'auto-test qui relisait les dimensions du montage devient
/// sans objet.
///
/// La police est une police LOGIQUE de la JVM. Elle est toujours résolue, sur tout poste et sur
/// tout runner : ni chemin DejaVu en dur, ni recours à `fc-match`, ni tofu.
public final class CartonDeTitre {

    private static final Color FOND = new Color(0x1A, 0x1A, 0x2E);
    private static final Color COULEUR_CAS = new Color(0xFF, 0xFF, 0xFF);
    private static final Color COULEUR_LIBELLE = new Color(0xD6, 0xD6, 0xE6);
    private static final Color COULEUR_TEST = new Color(0x94, 0x94, 0xAC);

    private static final int CORPS_CAS = 46;
    private static final int CORPS_LIBELLE = 30;
    private static final int CORPS_TEST = 22;

    /// Le libellé se replie sur cette part de la largeur, mesurée en PIXELS et non en caractères.
    private static final double PART_DE_LARGEUR = 0.75;

    private CartonDeTitre() {}

    private record Ligne(String texte, Font police, Color couleur) {
        int hauteur() {
            return police.getSize() * 3 / 2;
        }
    }

    public static BufferedImage dessiner(int largeur, int hauteur, String cas, String libelle, String test) {
        BufferedImage carton = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = carton.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(FOND);
        g.fillRect(0, 0, largeur, hauteur);

        Font policeLibelle = PoliceDuBanc.normale(CORPS_LIBELLE);
        List<Ligne> lignes = new ArrayList<>();
        lignes.add(new Ligne(cas, PoliceDuBanc.grasse(CORPS_CAS), COULEUR_CAS));
        for (String morceau : replier(libelle, g.getFontMetrics(policeLibelle), (int) (largeur * PART_DE_LARGEUR))) {
            lignes.add(new Ligne(morceau, policeLibelle, COULEUR_LIBELLE));
        }
        lignes.add(new Ligne(test, PoliceDuBanc.normale(CORPS_TEST), COULEUR_TEST));

        int total = lignes.stream().mapToInt(Ligne::hauteur).sum();
        int y = Math.max(0, (hauteur - total) / 2);
        for (Ligne ligne : lignes) {
            g.setFont(ligne.police());
            g.setColor(ligne.couleur());
            FontMetrics metrique = g.getFontMetrics();
            int x = (largeur - metrique.stringWidth(ligne.texte())) / 2;
            y += ligne.hauteur();
            g.drawString(ligne.texte(), Math.max(0, x), y - metrique.getDescent());
        }
        g.dispose();
        return carton;
    }

    /// Replie un texte à la largeur donnée. AUCUN mot n'est perdu ni tronqué : un mot plus large
    /// que la limite occupe sa ligne entière plutôt que d'être coupé. C'est l'invariant que
    /// `CartonDeTitreTest` éprouve, et il se vérifie sur des chaînes, sans lire de pixels.
    static List<String> replier(String texte, FontMetrics metrique, int largeurMax) {
        List<String> lignes = new ArrayList<>();
        if (texte == null || texte.isBlank()) {
            return lignes;
        }
        StringBuilder courante = new StringBuilder();
        for (String mot : texte.strip().split("\\s+")) {
            if (courante.isEmpty()) {
                courante.append(mot);
                continue;
            }
            if (metrique.stringWidth(courante + " " + mot) <= largeurMax) {
                courante.append(' ').append(mot);
            } else {
                lignes.add(courante.toString());
                courante = new StringBuilder(mot);
            }
        }
        lignes.add(courante.toString());
        return lignes;
    }

    /// Vrai si quelque chose a été dessiné par-dessus le fond. Compter des pixels ne dit pas CE
    /// qui est écrit : c'est pourquoi l'assertion sur le texte porte sur [#replier], qui rend
    /// des chaînes, et non sur l'image.
    public static boolean porteDeLEncre(BufferedImage image) {
        // Le fond se LIT dans le coin plutôt que de se comparer à la constante : une image
        // uniforme de n'importe quelle teinte rend alors faux, ce qui permet un témoin.
        int fond = image.getRGB(0, 0);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != fond) {
                    return true;
                }
            }
        }
        return false;
    }
}

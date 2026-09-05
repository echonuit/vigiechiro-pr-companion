package fr.univ_amu.iut.recette.film;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/// Dessine autour d'une fenêtre filmée la décoration que le banc **bash** obtenait d'openbox.
///
/// Le banc Java photographie le graphe de scène : plus de serveur X, donc plus personne pour dessiner
/// une fenêtre ([CameraDeScene] le dit dans son en-tête). C'était l'une des deux raisons d'être du
/// banc de documentation (#5282).
///
/// **Tous les nombres ci-dessous sont relevés au pixel** sur `.github/assets/parcours-declarer-un-carre.mp4`,
/// image 8 - la seule qui montre une fenêtre **et** sa modale. La consigne était de ressembler au
/// banc en place, pas d'inventer un cadre.
///
/// **La barre fait 20 px, pas 12.** Sur ces films la fenêtre principale est posée à `y = -8`, collée
/// au bord de l'écran : son titre y est coupé en deux sur les sept parcours. Une décoration dessinée
/// n'a pas de bord d'écran, donc la barre est entière - ressembler exactement reviendrait ici à
/// recopier un défaut. Les 20 px viennent de la modale, dont la barre est complète.
///
/// **Le focus est reproduit**, parce que la barre de la principale vire au gris dès qu'une modale
/// paraît : c'est le signal qui dit où va la frappe. Le détail des mesures vit dans #5285.
final class DecorationDeFenetre {

    /// Hauteur de la barre de titre, mesurée sur la modale « Site de suivi » (y 137 à 156).
    static final int BARRE = 20;

    /// Épaisseur du bord, sur les trois autres côtés comme au-dessus de la barre.
    static final int BORD = 1;

    private static final Color BORD_FOCALISE = new Color(75, 95, 119);
    private static final Color BORD_ENDORMI = new Color(82, 85, 92);

    /// La ligne claire sous le bord haut : openbox la pose pour donner du relief à la barre.
    private static final Color LISERE_FOCALISE = new Color(189, 209, 233);
    private static final Color LISERE_ENDORMI = new Color(238, 236, 240);

    private static final Color BARRE_FOCALISEE_HAUT = new Color(150, 184, 228);
    private static final Color BARRE_FOCALISEE_BAS = new Color(122, 158, 203);
    private static final Color BARRE_ENDORMIE_HAUT = new Color(223, 220, 226);
    private static final Color BARRE_ENDORMIE_BAS = new Color(218, 210, 218);

    private static final Color TITRE_FOCALISE = new Color(255, 255, 255);
    private static final Color TITRE_ENDORMI = new Color(60, 60, 66);

    /// Les trois boutons, à droite : réduire, agrandir, fermer.
    private static final int BOUTON_LARGEUR = 15;
    private static final int BOUTON_HAUTEUR = 13;
    private static final int BOUTON_ECART = 3;
    private static final int MARGE_DROITE = 5;

    private DecorationDeFenetre() {}

    /// Le décalage vertical qu'une fenêtre décorée subit : sa scène descend de la hauteur du cadre.
    static int hauteurDuCadre() {
        return BORD + BARRE;
    }

    /// Dessine le cadre AUTOUR de la scène posée en `(x, y)`.
    ///
    /// `x` et `y` sont ceux de la SCÈNE, pas ceux du cadre : le cadre déborde donc au-dessus et sur
    /// les côtés. C'est l'ordre qui convient à [CameraDeScene], qui calcule d'abord où la scène va.
    ///
    /// @param titre le titre de la fenêtre ; vide, la barre reste nue plutôt que d'inventer un nom
    /// @param focalisee la fenêtre a-t-elle le focus - c'est ce qui décide du bleu ou du gris
    static void dessiner(Graphics2D g, int x, int y, int largeur, int hauteur, String titre, boolean focalisee) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cadreX = x - BORD;
        int cadreY = y - hauteurDuCadre();
        int cadreLargeur = largeur + 2 * BORD;
        int cadreHauteur = hauteur + hauteurDuCadre() + BORD;

        // 1. Le bord, en ANNEAU et non en rectangle plein.
        //
        // La première version remplissait tout le cadre, scène comprise, en comptant sur `composer()`
        // pour repasser l'image par-dessus. Cela marchait, mais par accident d'ORDRE : la décoration
        // était juste parce qu'on la dessinait avant, pas parce qu'elle se limitait à son office.
        // `DecorationDeFenetreTest` a refusé ce contrat-là, et il a bien fait - une décoration qui
        // peint sous la scène n'est plus une décoration, c'est un fond.
        g.setColor(focalisee ? BORD_FOCALISE : BORD_ENDORMI);
        g.fillRect(cadreX, cadreY, cadreLargeur, BORD);
        g.fillRect(cadreX, cadreY + cadreHauteur - BORD, cadreLargeur, BORD);
        g.fillRect(cadreX, cadreY, BORD, cadreHauteur);
        g.fillRect(cadreX + cadreLargeur - BORD, cadreY, BORD, cadreHauteur);

        // 2. La barre de titre, en dégradé vertical, entre le bord et la scène.
        int barreY = cadreY + BORD;
        Color haut = focalisee ? BARRE_FOCALISEE_HAUT : BARRE_ENDORMIE_HAUT;
        Color bas = focalisee ? BARRE_FOCALISEE_BAS : BARRE_ENDORMIE_BAS;
        g.setColor(focalisee ? LISERE_FOCALISE : LISERE_ENDORMI);
        g.drawLine(cadreX + BORD, barreY, cadreX + cadreLargeur - BORD - 1, barreY);
        for (int ligne = 1; ligne < BARRE; ligne++) {
            double t = (double) (ligne - 1) / (BARRE - 2);
            g.setColor(new Color(
                    (int) Math.round(haut.getRed() + (bas.getRed() - haut.getRed()) * t),
                    (int) Math.round(haut.getGreen() + (bas.getGreen() - haut.getGreen()) * t),
                    (int) Math.round(haut.getBlue() + (bas.getBlue() - haut.getBlue()) * t)));
            g.drawLine(cadreX + BORD, barreY + ligne, cadreX + cadreLargeur - BORD - 1, barreY + ligne);
        }

        dessinerLeTitre(g, cadreX, barreY, cadreLargeur, titre, focalisee);
        dessinerLesBoutons(g, cadreX + cadreLargeur, barreY, focalisee);
    }

    /// Le titre, centré dans la barre comme openbox le centre.
    ///
    /// Un titre vide laisse la barre nue. C'est voulu : une fenêtre sans titre est un fait du
    /// scénario, et lui en inventer un ferait passer un oubli pour une intention (ADR 2748).
    private static void dessinerLeTitre(
            Graphics2D g, int cadreX, int barreY, int cadreLargeur, String titre, boolean focalisee) {
        if (titre == null || titre.isBlank()) {
            return;
        }
        g.setFont(PoliceDuBanc.grasse(13));
        g.setColor(focalisee ? TITRE_FOCALISE : TITRE_ENDORMI);
        int largeurDuTexte = g.getFontMetrics().stringWidth(titre);
        int base = barreY + (BARRE + g.getFontMetrics().getAscent()) / 2 - 1;
        g.drawString(titre, cadreX + (cadreLargeur - largeurDuTexte) / 2, base);
    }

    /// Les trois boutons, collés au bord droit, dans l'ordre d'openbox.
    private static void dessinerLesBoutons(Graphics2D g, int cadreDroite, int barreY, boolean focalisee) {
        Color trait = focalisee ? TITRE_FOCALISE : TITRE_ENDORMI;
        int y = barreY + (BARRE - BOUTON_HAUTEUR) / 2;
        for (int rang = 0; rang < 3; rang++) {
            int x = cadreDroite - MARGE_DROITE - (3 - rang) * (BOUTON_LARGEUR + BOUTON_ECART) + BOUTON_ECART;
            g.setColor(new Color(255, 255, 255, focalisee ? 60 : 90));
            g.fillRect(x, y, BOUTON_LARGEUR, BOUTON_HAUTEUR);
            g.setColor(trait);
            g.setStroke(new BasicStroke(1f));
            g.drawRect(x, y, BOUTON_LARGEUR, BOUTON_HAUTEUR);
            dessinerLeGlyphe(g, rang, x, y);
        }
    }

    private static void dessinerLeGlyphe(Graphics2D g, int rang, int x, int y) {
        int cx = x + BOUTON_LARGEUR / 2;
        int cy = y + BOUTON_HAUTEUR / 2;
        switch (rang) {
            case 0 -> g.drawLine(cx - 3, cy + 3, cx + 3, cy + 3);
            case 1 -> g.drawRect(cx - 3, cy - 3, 6, 6);
            default -> {
                g.drawLine(cx - 3, cy - 3, cx + 3, cy + 3);
                g.drawLine(cx + 3, cy - 3, cx - 3, cy + 3);
            }
        }
    }
}

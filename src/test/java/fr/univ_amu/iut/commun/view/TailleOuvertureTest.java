package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La fenêtre s'ouvre assez grande pour l'accueil, sans dépasser l'écran (#3452).
class TailleOuvertureTest {

    @Test
    @DisplayName("#3452 : sur un grand écran, la fenêtre s'ouvre à la taille voulue")
    void grand_ecran_donne_la_taille_voulue() {
        TailleOuverture taille = TailleOuverture.bornee(2560, 1440);

        assertThat(taille.largeur()).isEqualTo(TailleOuverture.LARGEUR_VOULUE);
        assertThat(taille.hauteur()).isEqualTo(TailleOuverture.HAUTEUR_VOULUE);
    }

    @Test
    @DisplayName("#3452 : sur un portable de 1366x768, la fenêtre ne déborde pas de l'écran")
    void petit_ecran_borne_la_taille() {
        TailleOuverture taille = TailleOuverture.bornee(1366, 768);

        // L'accueil réclame plus que 768 : mieux vaut un accueil qui défile qu'une fenêtre dont le bas
        // passe sous la barre des tâches, hors d'atteinte.
        assertThat(taille.largeur()).isEqualTo(1100);
        assertThat(taille.hauteur()).isEqualTo(768);
    }

    @Test
    @DisplayName("#3452 : un écran non mesurable ne borne rien")
    void ecran_non_mesurable_laisse_passer() {
        // Un environnement sans affichage rend des bornes nulles. Borner par zéro ouvrirait une fenêtre
        // invisible - le remède serait pire que le défaut.
        TailleOuverture taille = TailleOuverture.bornee(0, 0);

        assertThat(taille.largeur()).isEqualTo(TailleOuverture.LARGEUR_VOULUE);
        assertThat(taille.hauteur()).isEqualTo(TailleOuverture.HAUTEUR_VOULUE);
    }
}

package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.CarroyageNational;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le contrôle du carré **sans réseau** (#4682) : même règle que [ControleCarreStoc], autre source.
///
/// Les positions sont celles mesurées le 2026-08-27 contre le serveur réel, et servent d'oracle ici comme
/// dans les tests de la chaîne en ligne.
class ControleCarreLocalTest {

    /// Position à 374,9 m du centre de `040110` : loin de tout bord.
    private static final double LAT = 44.44674980384396;

    private static final double LON = 6.298116860416506;

    private final ControleCarreLocal controle = new ControleCarreLocal(CarroyageNational.embarque());

    @Test
    @DisplayName("le carré déclaré est celui de la position : concorde, sans rien demander à personne")
    void concorde_hors_ligne() {
        assertThat(controle.confronter("040110", LAT, LON)).containsInstanceOf(VerdictCarre.Concorde.class);
    }

    @Test
    @DisplayName("un autre carré : diverge, et nomme le plus proche")
    void diverge_et_nomme_le_plus_proche() {
        assertThat(controle.confronter("130711", LAT, LON))
                .get()
                .isEqualTo(new VerdictCarre.Diverge("040110", "130711"));
    }

    @Test
    @DisplayName("sur une frontière, le carré déclaré concorde quel qu'il soit des deux (#4610)")
    void sur_une_frontiere_les_deux_concordent() {
        // Milieu du côté commun : deux centres à 997,7 m chacun. L'observateur a raison dans les deux cas,
        // et la règle est la même que celle de l'écran - elle vit dans ConfrontationCarre, une seule fois.
        assertThat(controle.confronter("040110", 44.444990, 6.306335)).containsInstanceOf(VerdictCarre.Concorde.class);
        assertThat(controle.confronter("040111", 44.444990, 6.306335)).containsInstanceOf(VerdictCarre.Concorde.class);
    }

    @Test
    @DisplayName("hors métropole : aucun carré ne couvre la position, et ça se dit")
    void hors_grille() {
        assertThat(controle.confronter("040110", 48.8566, -20.0)).containsInstanceOf(VerdictCarre.HorsGrille.class);
    }

    @Test
    @DisplayName("rien à confronter - pas de carré, pas de coordonnées - rend VIDE et ne se plaint pas")
    void rien_a_confronter_rend_vide() {
        // Un point sans position est normal, un site sans carré aussi tant qu'il n'est pas déposé. Rendre
        // un verdict ici obligerait chaque appelant à distinguer « tout va bien » de « il n'y avait rien
        // à regarder », et c'est exactement la confusion que l'issue #2159 a coûté cher à démêler.
        assertThat(controle.confronter(null, LAT, LON)).isEmpty();
        assertThat(controle.confronter("  ", LAT, LON)).isEmpty();
        assertThat(controle.confronter("040110", null, LON)).isEmpty();
        assertThat(controle.confronter("040110", LAT, null)).isEmpty();
    }
}

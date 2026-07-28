package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/// Vérifie la lecture de la **nature d'une nuit** (#2614) à partir de l'ensemble des passages marqués
/// opportunistes (#2525) : c'est la règle que partagent les deux écrans agrégés, et la seule part de la
/// dimension qui se teste sans IHM.
class NatureNuitTest {

    private static final Set<Long> MARQUEES = Set.of(7L, 12L);

    @Test
    void un_passage_marque_est_une_participation_opportuniste() {
        assertThat(NatureNuit.de(7L, MARQUEES)).isEqualTo(NatureNuit.OPPORTUNISTE);
    }

    @Test
    void un_passage_non_marque_releve_du_protocole() {
        // La table `passage_opportuniste` (V34) est une table de PRÉSENCE : seule l'exception y coûte une
        // ligne. L'absence de marquage n'est donc pas une inconnue, c'est le cas courant.
        assertThat(NatureNuit.de(42L, MARQUEES)).isEqualTo(NatureNuit.PROTOCOLE);
    }

    @Test
    void une_ligne_sans_passage_rattache_reste_visible_du_cote_protocole() {
        // Une ligne que le filtre ne sait pas classer ne doit disparaître d'AUCUNE des deux lectures :
        // un filtre qui escamote en silence ment sur ce qu'il montre.
        assertThat(NatureNuit.de(null, MARQUEES)).isEqualTo(NatureNuit.PROTOCOLE);
    }

    @Test
    void sans_aucune_nuit_marquee_tout_releve_du_protocole() {
        // Le cas de très loin le plus fréquent : une saison menée entièrement sur ses propres carrés.
        assertThat(NatureNuit.de(7L, Set.of())).isEqualTo(NatureNuit.PROTOCOLE);
    }
}

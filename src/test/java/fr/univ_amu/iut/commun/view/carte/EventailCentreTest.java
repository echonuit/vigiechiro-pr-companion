package fr.univ_amu.iut.commun.view.carte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests purs de [EventailCentre] : désempilement des points **sans GPS** posés au centre de leur carré
/// (#153/#154). Un seul → pile au centre ; plusieurs → positions distinctes, toutes dans la maille.
class EventailCentreTest {

    private static final EmpriseCarre CARRE = new EmpriseCarre(43.40, -1.58, 43.42, -1.56);

    @Test
    @DisplayName("Un seul élément est placé pile au centre du carré")
    void un_seul_element_au_centre() {
        List<double[]> positions = EventailCentre.positions(CARRE, 1);

        assertThat(positions).hasSize(1);
        assertThat(positions.get(0)[0]).isCloseTo(CARRE.latCentre(), within(1e-9));
        assertThat(positions.get(0)[1]).isCloseTo(CARRE.lonCentre(), within(1e-9));
    }

    @Test
    @DisplayName("Plusieurs éléments sont désempilés (positions distinctes) et restent dans la maille")
    void plusieurs_elements_desempiles_dans_la_maille() {
        List<double[]> positions = EventailCentre.positions(CARRE, 4);

        assertThat(positions).hasSize(4);
        assertThat(positions.stream().map(c -> c[0] + "," + c[1]).distinct().count())
                .as("quatre positions distinctes (pas de superposition)")
                .isEqualTo(4);
        assertThat(positions)
                .as("toutes à l'intérieur du carré")
                .allSatisfy(c -> assertThat(CARRE.contient(c[0], c[1])).isTrue());
    }

    @Test
    @DisplayName("Zéro élément donne une liste vide")
    void zero_element_liste_vide() {
        assertThat(EventailCentre.positions(CARRE, 0)).isEmpty();
    }
}

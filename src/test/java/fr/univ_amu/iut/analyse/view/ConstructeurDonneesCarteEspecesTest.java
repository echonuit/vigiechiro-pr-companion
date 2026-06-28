package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.carte.CarreGeo;
import fr.univ_amu.iut.commun.view.carte.DonneesCarte;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests **sans IHM** du [ConstructeurDonneesCarteEspeces] : traduction de l'inventaire par carré en
/// [DonneesCarte] pour la carte de répartition. `640380` est une maille du carroyage officiel embarqué
/// (`carrenat.csv.gz`) → tracée ; un numéro hors carroyage (`999999`) ne l'est pas.
class ConstructeurDonneesCarteEspecesTest {

    private static CarreEspeces carre(String numero, int richesse) {
        return new CarreEspeces(numero, "Étang de la Tuilière", richesse, richesse * 3, 2025, 2026);
    }

    @Test
    @DisplayName("Choroplèthe : seuls les carrés du carroyage sont tracés, avec une info-bulle de richesse")
    void trace_les_carres_du_carroyage_avec_infobulle() {
        DonneesCarte donnees =
                ConstructeurDonneesCarteEspeces.depuis(List.of(carre("640380", 4), carre("999999", 1)), Set.of());

        assertThat(donnees.points()).isEmpty();
        assertThat(donnees.carres()).singleElement().satisfies(carreGeo -> {
            assertThat(carreGeo.numeroCarre()).isEqualTo("640380");
            assertThat(carreGeo.infobulle())
                    .contains("Carré 640380")
                    .contains("4 espèces")
                    .contains("12 détections");
        });
    }

    @Test
    @DisplayName("Répartition : un carré présent garde sa couleur de richesse, l'absent est atténué")
    void present_garde_la_richesse_absent_attenue() {
        CarreGeo richesse = unique(ConstructeurDonneesCarteEspeces.depuis(List.of(carre("640380", 4)), Set.of()));
        CarreGeo present =
                unique(ConstructeurDonneesCarteEspeces.depuis(List.of(carre("640380", 4)), Set.of("640380")));
        CarreGeo absent = unique(ConstructeurDonneesCarteEspeces.depuis(List.of(carre("640380", 4)), Set.of("640381")));

        // Le carré présent conserve exactement sa couleur de richesse ; l'absent est atténué (distinct).
        assertThat(present.remplissage()).isEqualTo(richesse.remplissage());
        assertThat(absent.remplissage()).isNotEqualTo(present.remplissage());
    }

    @Test
    @DisplayName("Répartition : deux carrés présents de richesse différente ont des couleurs différentes")
    void carres_presents_de_richesse_differente_se_distinguent() {
        DonneesCarte donnees = ConstructeurDonneesCarteEspeces.depuis(
                List.of(carre("640380", 4), carre("640315", 1)), Set.of("640380", "640315"));

        assertThat(donnees.carres()).hasSize(2);
        assertThat(donnees.carres().get(0).remplissage())
                .as("le carré le plus riche n'a pas la même teinte que le moins riche")
                .isNotEqualTo(donnees.carres().get(1).remplissage());
    }

    private static CarreGeo unique(DonneesCarte donnees) {
        assertThat(donnees.carres()).hasSize(1);
        return donnees.carres().get(0);
    }
}

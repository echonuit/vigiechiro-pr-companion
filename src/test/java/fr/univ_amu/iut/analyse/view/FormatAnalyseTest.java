package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.Formats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Formatage des libellés de l'écran « Espèces & observations ».
///
/// La probabilité est le seul de ces libellés qui porte un **séparateur décimal**, donc le seul qui puisse
/// changer selon la machine (#2896). Le test l'épingle sur la valeur française, celle que la Javadoc de
/// [FormatAnalyse#taxonEtProb] documente.
class FormatAnalyseTest {

    @Test
    @DisplayName("#2896 : la probabilité s'écrit avec une VIRGULE, quelle que soit la locale de la machine")
    void la_probabilite_s_ecrit_avec_une_virgule() {
        // La galerie d'aperçus, générée par un runner anglais, publiait « Pippip (0.90) » : l'application
        // montrait un séparateur à ses utilisateurs et un autre à sa propre documentation visuelle.
        assertThat(FormatAnalyse.taxonEtProb("Pippip", 0.92)).isEqualTo("Pippip (0,92)");
    }

    @Test
    @DisplayName("l'arrondi est celui de deux décimales, pas une troncature")
    void l_arrondi_est_a_deux_decimales() {
        assertThat(FormatAnalyse.taxonEtProb("Nyclei", 0.9567)).isEqualTo("Nyclei (0,96)");
    }

    @Test
    @DisplayName("sans probabilité, le taxon se suffit ; sans taxon, rien à afficher")
    void les_cas_sans_valeur() {
        assertThat(FormatAnalyse.taxonEtProb("Tadten", null)).isEqualTo("Tadten");
        assertThat(FormatAnalyse.taxonEtProb(null, 0.5)).isEqualTo(Formats.VALEUR_ABSENTE);
        assertThat(FormatAnalyse.taxonEtProb("   ", 0.5)).isEqualTo(Formats.VALEUR_ABSENTE);
    }
}

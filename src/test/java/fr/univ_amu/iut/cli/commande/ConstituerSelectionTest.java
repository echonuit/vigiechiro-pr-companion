package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Traduction du mot-clé de méthode de `constituer-selection` (#1512). `methodeDepuis` est une fonction
/// pure : `reparti` / `aleatoire` / `manuel` (toute casse) donnent la bonne [MethodeSelection], `null`
/// retombe sur la répartition temporelle (défaut), l'inconnu lève une erreur d'usage.
class ConstituerSelectionTest {

    @Test
    @DisplayName("reparti / aleatoire / manuel (+ null par défaut) donnent la bonne méthode")
    void methode_depuis_mots_cles() {
        assertThat(ConstituerSelection.methodeDepuis(null)).isEqualTo(MethodeSelection.REPARTITION_TEMPORELLE);
        assertThat(ConstituerSelection.methodeDepuis("reparti")).isEqualTo(MethodeSelection.REPARTITION_TEMPORELLE);
        assertThat(ConstituerSelection.methodeDepuis("ALEATOIRE")).isEqualTo(MethodeSelection.ALEATOIRE);
        assertThat(ConstituerSelection.methodeDepuis("Manuel")).isEqualTo(MethodeSelection.MANUEL);
    }

    @Test
    @DisplayName("Une méthode inconnue lève une erreur d'usage listant les valeurs acceptées")
    void methode_inconnue_leve_erreur_usage() {
        assertThatThrownBy(() -> ConstituerSelection.methodeDepuis("magique"))
                .isInstanceOf(ErreurUsage.class)
                .hasMessageContaining("reparti, aleatoire, manuel");
    }
}

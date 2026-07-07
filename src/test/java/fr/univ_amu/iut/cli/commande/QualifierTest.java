package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.model.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Traduction du mot-clé de verdict de `qualifier` (#617). `verdictDepuis` est une fonction pure : les
/// mots-clés `ok` / `douteux` / `a-jeter` (toute casse) donnent le bon [Verdict], l'inconnu lève une
/// erreur d'usage.
class QualifierTest {

    @Test
    @DisplayName("ok / douteux / a-jeter (toute casse) donnent le bon verdict")
    void verdict_depuis_mots_cles() {
        assertThat(Qualifier.verdictDepuis("ok")).isEqualTo(Verdict.OK);
        assertThat(Qualifier.verdictDepuis("DOUTEUX")).isEqualTo(Verdict.DOUTEUX);
        assertThat(Qualifier.verdictDepuis("a-jeter")).isEqualTo(Verdict.A_JETER);
        assertThat(Qualifier.verdictDepuis("Ajeter")).isEqualTo(Verdict.A_JETER);
    }

    @Test
    @DisplayName("Un mot-clé inconnu lève une erreur d'usage listant les valeurs acceptées")
    void verdict_inconnu_leve_erreur_usage() {
        assertThatThrownBy(() -> Qualifier.verdictDepuis("peut-etre"))
                .isInstanceOf(ErreurUsage.class)
                .hasMessageContaining("ok, douteux, a-jeter");
    }
}

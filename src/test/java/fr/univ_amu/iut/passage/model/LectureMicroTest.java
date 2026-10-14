package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/// La lecture du matériel micro d'une participation (#6211, #6228) : ce qui se relit, et ce qui se nomme.
class LectureMicroTest {

    @Test
    @DisplayName("depuis : mappe micro0_* vers MaterielMicro ; valeurs absentes → null")
    void micro_depuis_config() {
        MaterielMicro micro = LectureMicro.depuis(
                        42L, Map.of("micro0_type", "ICS", "micro0_position", "CANOPEE", "micro0_hauteur", "4"))
                .materiel();

        assertThat(micro.typeMicro()).isEqualTo("ICS");
        assertThat(micro.positionMicro()).isEqualTo(PositionMicro.CANOPEE);
        assertThat(micro.hauteurMetres()).isEqualTo(4.0);

        MaterielMicro vide = LectureMicro.depuis(42L, Map.of()).materiel();
        assertThat(vide.typeMicro()).isNull();
        assertThat(vide.positionMicro()).isNull();
        assertThat(vide.hauteurMetres()).isNull();
    }

    @ParameterizedTest(name = "« {0} » → {1} m")
    @CsvSource(
            delimiter = '|',
            value = {"2,5 | 2.5", "2,50 | 2.5", "2m50 | 2.5", "2,5m | 2.5", "250 | 2.5", "50 | 0.5", "250cm | 2.5"})
    @DisplayName("#6211 : une hauteur saisie avec une virgule, une unité ou en centimètres se relit en mètres")
    void micro_depuis_lit_la_hauteur_avec_tolerance(String brut, double metres) {
        assertThat(LectureMicro.depuis(42L, Map.of("micro0_hauteur", brut))
                        .materiel()
                        .hauteurMetres())
                .isEqualTo(metres);
    }

    @ParameterizedTest(name = "« {0} »")
    @ValueSource(strings = {"sol", "Sol", "  sol "})
    @DisplayName("#6228 : une position écrite en minuscules ou entourée de blancs se relit")
    void micro_depuis_lit_la_position_sans_tenir_compte_de_la_casse(String brut) {
        assertThat(LectureMicro.depuis(42L, Map.of("micro0_position", brut))
                        .materiel()
                        .positionMicro())
                .isEqualTo(PositionMicro.SOL);
    }

    @Test
    @DisplayName("#6211, #6228 : une hauteur ou une position que la lecture ne prend pas reste vide, et se nomme")
    void micro_depuis_nomme_ce_qu_il_ne_relit_pas() {
        MaterielRelu relu = LectureMicro.depuis(
                42L, Map.of("micro0_position", "hauteur", "micro0_hauteur", "2-3", "micro0_type", "ICS"));

        assertThat(relu.materiel().positionMicro()).isNull();
        assertThat(relu.materiel().hauteurMetres()).isNull();
        assertThat(relu.materiel().typeMicro())
                .as("le reste du matériel se relit")
                .isEqualTo("ICS");
        assertThat(relu.nonRelus()).containsExactly("position du micro « hauteur »", "hauteur du micro « 2-3 »");
    }

    @Test
    @DisplayName("#6211 : une valeur absente ou blanche n'est pas une valeur illisible, et ne se signale pas")
    void micro_depuis_ne_signale_pas_un_champ_vide() {
        MaterielRelu relu = LectureMicro.depuis(42L, Map.of("micro0_position", " ", "micro0_hauteur", ""));

        assertThat(relu.nonRelus()).isEmpty();
    }
}

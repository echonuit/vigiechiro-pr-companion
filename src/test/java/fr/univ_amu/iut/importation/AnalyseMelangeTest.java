package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.importation.model.AnalyseMelange;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests unitaires de la détection du cas limite **« mélange »** (#33) à partir des noms de fichiers
/// originaux (`PaRecPR<série>_<yyyyMMdd>_<HHmmss>.wav`). Règle : une nuit s'étale sur deux dates
/// consécutives au plus ; au-delà (ou plusieurs séries) = mélange.
class AnalyseMelangeTest {

    @Test
    @DisplayName("Un enregistreur, une nuit (soir → matin sur 2 dates consécutives) : pas de mélange")
    void une_nuit_un_enregistreur_pas_de_melange() {
        AnalyseMelange analyse = AnalyseMelange.depuis(List.of(
                Path.of("PaRecPR1925492_20260422_203000.wav"),
                Path.of("PaRecPR1925492_20260422_233000.wav"),
                Path.of("PaRecPR1925492_20260423_050000.wav"))); // matin J+1

        assertThat(analyse.melange()).isFalse();
        assertThat(analyse.series()).containsExactly("1925492");
        assertThat(analyse.nuits()).hasSize(2);
    }

    @Test
    @DisplayName("Plusieurs numéros de série → mélange d'enregistreurs")
    void plusieurs_enregistreurs() {
        AnalyseMelange analyse = AnalyseMelange.depuis(
                List.of(Path.of("PaRecPR1925492_20260422_203000.wav"), Path.of("PaRecPR1648011_20260422_203000.wav")));

        assertThat(analyse.plusieursEnregistreurs()).isTrue();
        assertThat(analyse.melange()).isTrue();
        assertThat(analyse.series()).containsExactly("1648011", "1925492"); // triés
    }

    @Test
    @DisplayName("Trois dates d'acquisition → mélange de nuits")
    void plusieurs_nuits_trois_dates() {
        AnalyseMelange analyse = AnalyseMelange.depuis(List.of(
                Path.of("PaRecPR1925492_20260422_203000.wav"),
                Path.of("PaRecPR1925492_20260423_203000.wav"),
                Path.of("PaRecPR1925492_20260424_203000.wav")));

        assertThat(analyse.plusieursNuits()).isTrue();
        assertThat(analyse.melange()).isTrue();
    }

    @Test
    @DisplayName("Deux dates non consécutives → mélange de nuits")
    void plusieurs_nuits_dates_non_consecutives() {
        AnalyseMelange analyse = AnalyseMelange.depuis(
                List.of(Path.of("PaRecPR1925492_20260422_203000.wav"), Path.of("PaRecPR1925492_20260430_203000.wav")));

        assertThat(analyse.plusieursNuits()).isTrue();
        assertThat(analyse.melange()).isTrue();
    }

    @Test
    @DisplayName("Les noms illisibles (hors motif PaRecPR) sont ignorés sans erreur")
    void noms_illisibles_ignores() {
        AnalyseMelange analyse =
                AnalyseMelange.depuis(List.of(Path.of("notes.txt"), Path.of("PaRecPR1925492_20260422_203000.wav")));

        assertThat(analyse.series()).containsExactly("1925492");
        assertThat(analyse.nuits()).hasSize(1);
        assertThat(analyse.melange()).isFalse();
    }

    @Test
    @DisplayName("Un nom au motif correct mais à date impossible est ignoré (série non comptée)")
    void date_impossible_serie_non_comptee() {
        AnalyseMelange analyse = AnalyseMelange.depuis(List.of(
                Path.of("PaRecPR1925492_20260422_203000.wav"),
                Path.of("PaRecPR1648011_20269999_120000.wav"))); // date impossible : à ignorer en entier

        assertThat(analyse.series()).containsExactly("1925492"); // pas 1648011
        assertThat(analyse.plusieursEnregistreurs()).isFalse();
        assertThat(analyse.melange()).isFalse();
    }
}

package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.importation.model.AnalyseMelange;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de la mise en phrase de l'avertissement **« mélange »** (#33). La détection elle-même est
/// couverte par `AnalyseMelangeTest` ; ici on vérifie que seul le mélange de **plusieurs
/// enregistreurs** déclenche un message (le multi-nuits d'un seul enregistreur est un cas géré, donc
/// silencieux), et le libellé selon qu'il couvre une ou plusieurs nuits.
class AvertissementMelangeTest {

    @Test
    @DisplayName("Dossier homogène (une nuit, un enregistreur) : aucun avertissement")
    void homogene_pas_d_avertissement() {
        AnalyseMelange analyse = AnalyseMelange.depuis(
                List.of(Path.of("PaRecPR1925492_20260422_203000.wav"), Path.of("PaRecPR1925492_20260422_233000.wav")));

        assertThat(AvertissementMelange.rediger(analyse)).isEmpty();
    }

    @Test
    @DisplayName("Plusieurs enregistreurs seuls : le message cite les séries, pas les nuits")
    void enregistreurs_seuls() {
        AnalyseMelange analyse = AnalyseMelange.depuis(
                List.of(Path.of("PaRecPR1925492_20260422_203000.wav"), Path.of("PaRecPR1648011_20260422_203000.wav")));

        assertThat(AvertissementMelange.rediger(analyse))
                .contains("plusieurs enregistreurs (séries 1648011, 1925492)")
                .contains("un import correspond à un seul enregistreur")
                .doesNotContain("plusieurs nuits");
    }

    @Test
    @DisplayName("Plusieurs nuits d'un seul enregistreur : cas géré (découpage par nuit), aucun avertissement")
    void nuits_seules_pas_d_avertissement() {
        AnalyseMelange analyse = AnalyseMelange.depuis(List.of(
                Path.of("PaRecPR1925492_20260422_203000.wav"),
                Path.of("PaRecPR1925492_20260423_203000.wav"),
                Path.of("PaRecPR1925492_20260424_203000.wav")));

        assertThat(AvertissementMelange.rediger(analyse)).isEmpty();
    }

    @Test
    @DisplayName("Plusieurs enregistreurs ET plusieurs nuits : message centré sur les enregistreurs")
    void enregistreurs_et_nuits() {
        AnalyseMelange analyse = AnalyseMelange.depuis(List.of(
                Path.of("PaRecPR1925492_20260422_203000.wav"),
                Path.of("PaRecPR1648011_20260423_203000.wav"),
                Path.of("PaRecPR1648011_20260424_203000.wav")));

        assertThat(AvertissementMelange.rediger(analyse))
                .contains("plusieurs enregistreurs (séries 1648011, 1925492)")
                .contains("sur plusieurs nuits");
    }
}

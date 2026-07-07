package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.importation.model.CycleAcquisition;
import fr.univ_amu.iut.importation.model.NuitDetectee;
import fr.univ_amu.iut.importation.model.PartitionNuits;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Partition d'un dossier plat en nuits ([PartitionNuits]) : groupage soir→matin (une nuit à cheval sur
/// deux dates) et complétude déduite des cycles du journal.
class PartitionNuitsTest {

    private static Path wav(String horodatage) {
        return Path.of("PaRecPR1997632_" + horodatage + ".wav");
    }

    private static CycleAcquisition cycle(int n, LocalDate soir, boolean complet, String raison) {
        LocalDateTime reveil = soir.atTime(21, 0);
        LocalDateTime fin = complet ? soir.plusDays(1).atTime(6, 31) : soir.atTime(23, 7);
        return new CycleAcquisition(n, reveil, fin, complet, raison);
    }

    @Test
    @DisplayName("Groupe les WAV en 3 nuits (soir→matin), une nuit à cheval sur deux dates")
    void groupe_trois_nuits() {
        List<Path> originaux = List.of(
                wav("20260703_213000"), // nuit du 03 (soir)
                wav("20260703_233000"), // nuit du 03 (soir)
                wav("20260704_050000"), // nuit du 03 (petit matin) — même nuit malgré la date du 04
                wav("20260704_213000"), // nuit du 04 (soir)
                wav("20260705_060000"), // nuit du 04 (petit matin)
                wav("20260705_213000"), // nuit du 05 (soir)
                wav("20260705_230000")); // nuit du 05 (soir)

        List<NuitDetectee> nuits = PartitionNuits.partitionner(originaux, List.of());

        assertThat(nuits)
                .extracting(NuitDetectee::dateNuit)
                .containsExactly(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 5));
        assertThat(nuits).extracting(NuitDetectee::nombreFichiers).containsExactly(3, 2, 2);
        // La 1re nuit s'étend du soir du 03 au petit matin du 04.
        assertThat(nuits.get(0).debut()).isEqualTo(LocalDateTime.of(2026, 7, 3, 21, 30, 0));
        assertThat(nuits.get(0).fin()).isEqualTo(LocalDateTime.of(2026, 7, 4, 5, 0, 0));
    }

    @Test
    @DisplayName("La complétude vient des cycles : la nuit tronquée (carte pleine) est signalée")
    void completude_depuis_les_cycles() {
        List<Path> originaux =
                List.of(wav("20260703_213000"), wav("20260704_213000"), wav("20260705_213000"), wav("20260705_230000"));
        List<CycleAcquisition> cycles = List.of(
                cycle(1, LocalDate.of(2026, 7, 3), true, null),
                cycle(2, LocalDate.of(2026, 7, 4), true, null),
                cycle(3, LocalDate.of(2026, 7, 5), false, "carte SD pleine"));

        List<NuitDetectee> nuits = PartitionNuits.partitionner(originaux, cycles);

        assertThat(nuits).extracting(NuitDetectee::complete).containsExactly(true, true, false);
        assertThat(nuits.get(2).motifIncompletude()).isEqualTo("carte SD pleine");
        assertThat(nuits.get(0).motifIncompletude()).isNull();
    }

    @Test
    @DisplayName("Sans cycle correspondant (journal absent), une nuit est supposée complète")
    void sans_cycle_supposee_complete() {
        List<NuitDetectee> nuits =
                PartitionNuits.partitionner(List.of(wav("20260703_213000"), wav("20260704_060000")), List.of());

        assertThat(nuits).singleElement().satisfies(nuit -> {
            assertThat(nuit.dateNuit()).isEqualTo(LocalDate.of(2026, 7, 3));
            assertThat(nuit.complete()).isTrue();
            assertThat(nuit.nombreFichiers()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("Un nom non horodaté est ignoré ; un dossier sans WAV donne zéro nuit")
    void noms_non_horodates_et_dossier_vide() {
        assertThat(PartitionNuits.partitionner(List.of(Path.of("notes.txt")), List.of()))
                .isEmpty();
        assertThat(PartitionNuits.partitionner(List.of(), List.of())).isEmpty();
    }
}

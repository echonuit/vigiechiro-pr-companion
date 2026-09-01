package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Completude;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le câblage du journal jusqu'aux [NuitDetectee#indicesDuJournal()] (#4990).
///
/// Dans ce paquet, et pas dans `fr.univ_amu.iut.importation` avec le reste de la partition, parce
/// qu'il monte des `LigneJournal` : le type est de portée paquet, et l'élargir pour un banc reviendrait
/// à déplacer la contrainte au lieu de s'y ranger.
///
/// Ce banc existe parce que la mutation l'a réclamé : `journal != null` survivait, et le chemin qui
/// va d'une ligne de log à l'infobulle n'était traversé par aucun test. Les deux bancs voisins
/// couvraient chacun un bout - la partition sans journal, le libellé sans partition - et le fil entre
/// les deux ne l'était pas.
class PartitionNuitsIndicesTest {

    private static final LocalDate SOIR = LocalDate.of(2026, 7, 3);

    private static Path wav(String horodatage) {
        return Path.of("PaRecPR1925492_" + horodatage + ".wav");
    }

    /// Un journal dont les anomalies portent leur horodatage, seul moyen de les ranger dans leur nuit.
    private static JournalParse journalAvecAnomalies(LigneJournal... anomalies) {
        return new JournalParse(
                "1925492",
                null,
                SOIR,
                null,
                null,
                null,
                null,
                null,
                true,
                null,
                List.of(),
                List.of(anomalies),
                List.of());
    }

    private static CycleAcquisition cycleTronque() {
        return new CycleAcquisition(1, SOIR.atTime(21, 0), SOIR.plusDays(1).atTime(3, 14), false, "journal interrompu");
    }

    @Test
    @DisplayName("#4990 : une nuit tronquée porte les anomalies de SA nuit, et rien d'autre")
    void la_nuit_tronquee_porte_ses_indices() {
        JournalParse journal = journalAvecAnomalies(
                new LigneJournal(SOIR.plusDays(1).atTime(3, 14), "Batteries internes 11 %"),
                new LigneJournal(SOIR.plusDays(1).atTime(3, 14), "Erreur d'ecriture SD"),
                // Une anomalie d'une AUTRE nuit : elle ne doit pas remonter ici, sans quoi l'infobulle
                // ferait porter à cette nuit ce qui s'est passé la veille.
                new LigneJournal(SOIR.minusDays(2).atTime(23, 0), "Redemarrage inattendu"));

        List<NuitDetectee> nuits = PartitionNuits.partitionner(
                List.of(wav("20260703_213000"), wav("20260704_031000")), List.of(cycleTronque()), journal);

        assertThat(nuits).singleElement().satisfies(nuit -> {
            assertThat(nuit.completude()).isEqualTo(Completude.TRONQUEE);
            assertThat(nuit.indicesDuJournal()).containsExactly("Batteries internes 11 %", "Erreur d'ecriture SD");
        });
    }

    @Test
    @DisplayName("#4990 : sans journal, une nuit tronquée n'invente aucun indice")
    void sans_journal_aucun_indice() {
        // Le cycle vient d'ailleurs que du journal parsé dans ce banc : la partition doit donc tenir
        // le cas « je sais que la nuit est tronquée, je n'ai rien à montrer avant l'arrêt ».
        List<NuitDetectee> nuits = PartitionNuits.partitionner(
                List.of(wav("20260703_213000"), wav("20260704_031000")), List.of(cycleTronque()), null);

        assertThat(nuits).singleElement().satisfies(nuit -> {
            assertThat(nuit.completude()).isEqualTo(Completude.TRONQUEE);
            assertThat(nuit.indicesDuJournal()).isEmpty();
        });
    }

    @Test
    @DisplayName("#4990 : une nuit COMPLÈTE ne porte aucun indice, même quand le journal en a")
    void une_nuit_complete_ne_porte_aucun_indice() {
        // Sinon des faits ordinaires - un réveil, une mesure de batterie - passeraient pour un signal
        // sur une nuit qui n'a rien eu.
        JournalParse journal =
                journalAvecAnomalies(new LigneJournal(SOIR.plusDays(1).atTime(2, 0), "Batteries internes 88 %"));
        CycleAcquisition complet =
                new CycleAcquisition(1, SOIR.atTime(21, 0), SOIR.plusDays(1).atTime(6, 31), true, null);

        List<NuitDetectee> nuits = PartitionNuits.partitionner(
                List.of(wav("20260703_213000"), wav("20260704_031000")), List.of(complet), journal);

        assertThat(nuits).singleElement().satisfies(nuit -> {
            assertThat(nuit.completude()).isEqualTo(Completude.COMPLETE);
            assertThat(nuit.indicesDuJournal()).isEmpty();
        });
    }
}

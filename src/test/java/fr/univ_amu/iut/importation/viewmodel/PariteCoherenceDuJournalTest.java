package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.cli.commande.Importer;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.importation.model.AnalyseCoherence;
import fr.univ_amu.iut.importation.model.CycleAcquisition;
import fr.univ_amu.iut.importation.model.JournalParse;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Les deux surfaces disent-elles que le journal ne correspond pas aux enregistrements, et disent-elles
/// la même chose ?
///
/// L'écran l'annonçait seul depuis #33 ; la ligne de commande importait un journal étranger sans un mot
/// (#5670). Chacune écrit dans sa langue, et ce banc confronte leur **contenu**, sur le patron de
/// `PariteSupportEnLectureSeuleTest` : l'état, puis les données que l'écran détaille.
class PariteCoherenceDuJournalTest {

    /// Un journal du même capteur qui raconte deux nuits d'août, sur une carte du 24 : aucune n'est la
    /// sienne.
    private static final AnalyseCoherence DATE_ETRANGERE = AnalyseCoherence.depuis(
            journal("1925492"),
            null,
            List.of(Path.of("PaRecPR1925492_20260824_213000.wav")),
            List.of(cycle(LocalDate.of(2026, 8, 19)), cycle(LocalDate.of(2026, 8, 22))));

    /// Un journal d'un autre capteur, sur la bonne nuit.
    private static final AnalyseCoherence SERIE_ETRANGERE = AnalyseCoherence.depuis(
            journal("1925492"),
            null,
            List.of(Path.of("PaRecPR1648011_20260819_213000.wav")),
            List.of(cycle(LocalDate.of(2026, 8, 19))));

    private static JournalParse journal(String serie) {
        return new JournalParse(
                serie,
                null,
                LocalDate.of(2026, 8, 19),
                null,
                null,
                null,
                null,
                null,
                true,
                null,
                List.of(),
                List.of(),
                List.of());
    }

    private static CycleAcquisition cycle(LocalDate nuit) {
        return new CycleAcquisition(1, nuit.atTime(21, 0), nuit.plusDays(1).atTime(6, 0), true, null);
    }

    /// Ce que l'écran affiche : le constat et ses détails, mis bout à bout.
    private static String ecran(AnalyseCoherence coherence) {
        CompteRendu rendu = AvertissementsInspection.rediger(null, coherence, List.of(), false);
        return rendu.constats().stream()
                .map(constat -> constat.fait() + " "
                        + constat.details().stream()
                                .map(detail -> detail.sujet() + " " + detail.precision())
                                .collect(Collectors.joining(" ")))
                .collect(Collectors.joining(" "));
    }

    @Test
    @DisplayName("#5670 : les deux surfaces nomment l'ÉTAT, un journal qui ne correspond pas aux enregistrements")
    void les_deux_nomment_l_etat() {
        assertThat(Importer.journalIncoherentLisible(DATE_ETRANGERE))
                .as("le terminal dit ce que l'écran dit")
                .contains("ne correspond pas aux enregistrements");
        assertThat(ecran(DATE_ETRANGERE)).contains("ne correspond pas aux enregistrements");
    }

    @Test
    @DisplayName("#5670 : les deux nomment les nuits que le journal raconte, et celles de la carte")
    void les_deux_nomment_les_nuits() {
        for (String nuit : List.of("19/08/2026", "22/08/2026", "24/08/2026")) {
            assertThat(Importer.journalIncoherentLisible(DATE_ETRANGERE)).contains(nuit);
            assertThat(ecran(DATE_ETRANGERE)).contains(nuit);
        }
    }

    @Test
    @DisplayName("#5670 : les deux nomment la série déclarée et celle des enregistrements")
    void les_deux_nomment_les_series() {
        for (String serie : List.of("1925492", "1648011")) {
            assertThat(Importer.journalIncoherentLisible(SERIE_ETRANGERE)).contains(serie);
            assertThat(ecran(SERIE_ETRANGERE)).contains(serie);
        }
        assertThat(Importer.journalIncoherentLisible(SERIE_ETRANGERE))
                .as("un désaccord de série seul ne parle pas de nuits")
                .doesNotContain("nuit");
    }

    @Test
    @DisplayName("#5670 : un journal cohérent ne fait écrire aucune ligne, comme l'écran n'affiche rien")
    void un_journal_coherent_se_tait() {
        AnalyseCoherence coherent = AnalyseCoherence.depuis(
                journal("1925492"),
                null,
                List.of(Path.of("PaRecPR1925492_20260819_213000.wav")),
                List.of(cycle(LocalDate.of(2026, 8, 19))));

        assertThat(ecran(coherent)).isEmpty();
        assertThat(Importer.journalIncoherentLisible(coherent)).isEmpty();
    }

    @Test
    @DisplayName("#5670 : le détail de l'écran et la ligne du terminal portent les mêmes données")
    void memes_donnees_que_les_details() {
        List<String> precisions =
                AvertissementsInspection.rediger(null, DATE_ETRANGERE, List.of(), false)
                        .constats()
                        .getFirst()
                        .details()
                        .stream()
                        .map(Detail::precision)
                        .toList();
        assertThat(precisions).isNotEmpty();
        precisions.forEach(precision ->
                assertThat(Importer.journalIncoherentLisible(DATE_ETRANGERE)).contains(precision));
    }
}

package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.JournalParse;
import fr.univ_amu.iut.importation.model.RapportInspection;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La série de l'enregistreur d'une carte, que [NuitsDeLaCarte#serie] tire du journal ou, à défaut,
/// des noms des enregistrements (#107).
class NuitsDeLaCarteTest {

    private static final List<Path> ENREGISTREMENTS = List.of(Path.of("PaRecPR1648011_20260703_210000.wav"));

    private static RapportInspection rapport(JournalParse journal) {
        return new RapportInspection(
                Path.of("carte"), null, journal, null, ENREGISTREMENTS, EtatNommage.BRUT, List.of());
    }

    private static JournalParse journal(String serie) {
        return new JournalParse(
                serie,
                null,
                LocalDate.of(2026, 7, 3),
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

    @Test
    @DisplayName("La série vient du journal quand il la porte")
    void serie_du_journal() {
        assertThat(NuitsDeLaCarte.serie(rapport(journal("1925492")))).isEqualTo("1925492");
    }

    @Test
    @DisplayName("#5669 : un journal sans numéro de série ne cache pas celui des enregistrements")
    void journal_sans_serie_se_replie_sur_les_enregistrements() {
        // L'inspection refuse aujourd'hui un tel journal ; le repli tient pour le jour où elle ne le
        // refuserait plus, et ce test l'éprouve, au lieu de le laisser survivre à la mutation.
        assertThat(NuitsDeLaCarte.serie(rapport(journal(null)))).isEqualTo("1648011");
    }

    @Test
    @DisplayName("Sans journal, la série vient des enregistrements (#107)")
    void sans_journal() {
        assertThat(NuitsDeLaCarte.serie(rapport(null))).isEqualTo("1648011");
    }
}

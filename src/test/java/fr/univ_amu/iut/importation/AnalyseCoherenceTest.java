package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.importation.model.AnalyseCoherence;
import fr.univ_amu.iut.importation.model.JournalParse;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests unitaires de la détection du cas limite **« incohérence »** (#33) : l'identité déclarée
/// (journal `LogPR` + nom du relevé `THLog`) confrontée aux séries/dates portées par les WAV.
class AnalyseCoherenceTest {

    private static final LocalDate NUIT = LocalDate.of(2026, 4, 22);

    @Test
    @DisplayName("Journal et fichiers concordants (même série, nuit soir → matin) : pas d'incohérence")
    void concordant_pas_d_incoherence() {
        AnalyseCoherence c = AnalyseCoherence.depuis(
                journal("1925492", NUIT),
                Path.of("PaRecPR1925492_THLog.csv"),
                List.of(Path.of("PaRecPR1925492_20260422_203000.wav"), Path.of("PaRecPR1925492_20260423_050000.wav")));

        assertThat(c.incoherent()).isFalse();
        assertThat(c.serieIncoherente()).isFalse();
        assertThat(c.dateIncoherente()).isFalse();
    }

    @Test
    @DisplayName("Série du journal absente des fichiers → incohérence de série")
    void serie_journal_absente() {
        AnalyseCoherence c = AnalyseCoherence.depuis(
                journal("1925492", NUIT), null, List.of(Path.of("PaRecPR1648011_20260422_203000.wav")));

        assertThat(c.serieIncoherente()).isTrue();
        assertThat(c.seriesDeclareesAbsentes()).containsExactly("1925492");
        assertThat(c.incoherent()).isTrue();
    }

    @Test
    @DisplayName("Série du relevé THLog absente des fichiers → incohérence de série")
    void serie_releve_absente() {
        AnalyseCoherence c = AnalyseCoherence.depuis(
                journal("1648011", NUIT),
                Path.of("PaRecPR9999999_THLog.csv"), // relevé d'un autre capteur
                List.of(Path.of("PaRecPR1648011_20260422_203000.wav")));

        assertThat(c.serieIncoherente()).isTrue();
        assertThat(c.seriesDeclareesAbsentes()).containsExactly("9999999");
    }

    @Test
    @DisplayName("Date du journal hors de la nuit des fichiers → incohérence de date")
    void date_hors_nuit() {
        AnalyseCoherence c = AnalyseCoherence.depuis(
                journal("1925492", NUIT), null, List.of(Path.of("PaRecPR1925492_20260430_203000.wav")));

        assertThat(c.dateIncoherente()).isTrue();
        assertThat(c.incoherent()).isTrue();
    }

    @Test
    @DisplayName("Date du journal = veille du matin enregistré (J+1) : tolérée, pas d'incohérence")
    void date_soir_matin_toleree() {
        AnalyseCoherence c = AnalyseCoherence.depuis(
                journal("1925492", NUIT), null, List.of(Path.of("PaRecPR1925492_20260423_050000.wav")));

        assertThat(c.dateIncoherente()).isFalse();
    }

    @Test
    @DisplayName(
            "WAV de la nuit suivante (J+1, J+2) : une seule date recouvre la fenêtre, mais l'autre déborde → incohérence")
    void nuit_suivante_chevauchement_partiel_incoherent() {
        AnalyseCoherence c = AnalyseCoherence.depuis(
                journal("1925492", NUIT), // nuit du 22/04 (fenêtre [22, 23])
                null,
                List.of(
                        Path.of("PaRecPR1925492_20260423_203000.wav"),
                        Path.of("PaRecPR1925492_20260424_050000.wav"))); // 24/04 hors fenêtre

        assertThat(c.dateIncoherente()).isTrue();
    }

    @Test
    @DisplayName("Carte multi-nuits (WAV sur plus d'une nuit) : pas d'incohérence de date (cas géré)")
    void multi_nuits_date_non_incoherente() {
        AnalyseCoherence c = AnalyseCoherence.depuis(
                journal("1925492", LocalDate.of(2026, 7, 3)), // journal daté de la 1re nuit
                null,
                List.of(
                        Path.of("PaRecPR1925492_20260703_203000.wav"),
                        Path.of("PaRecPR1925492_20260704_203000.wav"),
                        Path.of("PaRecPR1925492_20260705_203000.wav")));

        assertThat(c.dateIncoherente()).isFalse();
        assertThat(c.incoherent()).isFalse();
    }

    @Test
    @DisplayName("Aucun WAV exploitable : rien à comparer, pas d'incohérence")
    void sans_fichier_neutre() {
        AnalyseCoherence c = AnalyseCoherence.depuis(journal("1925492", NUIT), null, List.of());

        assertThat(c.serieIncoherente()).isFalse();
        assertThat(c.dateIncoherente()).isFalse();
        assertThat(c.incoherent()).isFalse();
    }

    @Test
    @DisplayName("Ni journal ni relevé : aucune identité déclarée, pas d'incohérence")
    void sans_identite_declaree_neutre() {
        AnalyseCoherence c =
                AnalyseCoherence.depuis(null, null, List.of(Path.of("PaRecPR1925492_20260422_203000.wav")));

        assertThat(c.incoherent()).isFalse();
        assertThat(c.serieJournal()).isEmpty();
        assertThat(c.serieReleve()).isEmpty();
    }

    private static JournalParse journal(String serie, LocalDate date) {
        return new JournalParse(serie, null, date, null, null, null, null, null, true, null, List.of(), List.of());
    }
}

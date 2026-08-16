package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.passage.model.Passage;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le passage créé pour une nuit porte les paramètres d'acquisition **de cette nuit-là** (#3460).
///
/// Là où [fr.univ_amu.iut.importation.AnalyseurLogPRTest] vérifie que le journal **collecte** toutes
/// les configurations et sait les apparier, ce fichier vérifie que la fabrique d'entités s'en **sert**
/// vraiment. La distinction compte : le journal pouvait très bien porter la bonne réponse pendant que
/// la fabrique continuait de lire les champs plats, et rien n'aurait rougi.
class PassageDeLaNuitTest {

    private final FabriqueEntitesImport fabrique = new FabriqueEntitesImport(Horloge.systeme());

    /// Un journal de deux sessions, comme une carte laissée plusieurs nuits au même point : 384 kHz
    /// posés le 22 avril, 256 kHz posés le 25.
    private static JournalParse journalDeuxSessions() {
        return new AnalyseurLogPR()
                .analyser(List.of(
                        "22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492,"
                                + " V1.01, CPU 600000000, T4.1",
                        "22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz FL N FPH"
                                + " 00, S. R. 16dB 1dt. GN0, Bd. Freq. 8-120kHz, Wav 2-30s SD 99%",
                        "25/04/26 - 17:10:00 PR1925492 Paramètres : Acquisi. 21:00-06:30, Fe256kHz FL N FPH"
                                + " 00, S. R. 12dB 1dt. GN0, Bd. Freq. 10-96kHz, Wav 2-30s SD 80%"));
    }

    @Test
    @DisplayName("#3460 : la nuit de la SECONDE session porte les paramètres de la seconde session")
    void la_nuit_de_la_seconde_session_porte_ses_propres_parametres() {
        // Le défaut vécu : la fréquence d'échantillonnage et la bande passante d'une autre session
        // partaient avec la nuit, jusqu'à la plateforme, sans que rien ne le signale. Ce ne sont pas
        // des métadonnées d'agrément : elles conditionnent la transformation des séquences.
        Passage nuitDu25 = fabrique.passage(
                journalDeuxSessions(), 7L, new Prefixe("640380", 2026, 1, "A1"), LocalDate.of(2026, 4, 25));

        assertThat(nuitDu25.parametresAcquisition()).contains("256000").doesNotContain("384000");
        assertThat(nuitDu25.parametresAcquisition()).contains("10-96kHz").doesNotContain("8-120kHz");
        // La fenêtre d'acquisition aussi : elle a changé entre les deux sessions.
        assertThat(nuitDu25.heureDebut()).isEqualTo("21:00:00");
        assertThat(nuitDu25.heureFin()).isEqualTo("06:30:00");
    }

    @Test
    @DisplayName("#3460 : la nuit de la PREMIÈRE session garde les siens, elle ne prend pas la dernière")
    void la_nuit_de_la_premiere_session_garde_les_siens() {
        // Le contre-test qui compte. « Lire la dernière config du fichier » plutôt que la première
        // aurait corrigé la nuit du 25 et cassé celle du 22 : le défaut aurait changé de nuit, pas
        // disparu. C'est pourquoi la règle est « la dernière posée AU PLUS TARD cette nuit-là ».
        Passage nuitDu22 = fabrique.passage(
                journalDeuxSessions(), 7L, new Prefixe("640380", 2026, 1, "A1"), LocalDate.of(2026, 4, 22));

        assertThat(nuitDu22.parametresAcquisition()).contains("384000").doesNotContain("256000");
        assertThat(nuitDu22.heureDebut()).isEqualTo("20:25:00");
        assertThat(nuitDu22.heureFin()).isEqualTo("07:47:00");
    }
}

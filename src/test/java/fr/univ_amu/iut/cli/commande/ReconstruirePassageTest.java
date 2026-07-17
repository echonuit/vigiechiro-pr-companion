package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.Provider;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.RapportReconstruction;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `reconstruire-passage` : liste les orphelines, en reconstruit une (`--participation`) ou **toutes**
/// (`--tout`, parité CLI de l'import groupé #1708). Le service est mocké - l'orchestration est couverte par
/// `ServiceReconstructionPassagesTest` -, on vérifie ici le **câblage CLI** et le **rendu** du lot.
class ReconstruirePassageTest {

    private static final ParticipationOrpheline NUIT_1 =
            new ParticipationOrpheline("p1", "130711", "Z41", "2026-07-03T22:00:00+02:00", true);
    private static final ParticipationOrpheline NUIT_2 =
            new ParticipationOrpheline("p2", "130711", "Z41", "2026-07-04T22:00:00+02:00", true);

    private final ServiceReconstructionPassages service = mock(ServiceReconstructionPassages.class);

    private CommandLine ligne(StringWriter sortie) {
        Provider<Optional<ServiceReconstructionPassages>> provider = () -> Optional.of(service);
        CommandLine ligne = new CommandLine(new ReconstruirePassage(provider));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(new StringWriter(), true));
        return ligne;
    }

    @Test
    @DisplayName("#1711 --tout : reconstruit toutes les orphelines et rend un bilan (parité import groupé)")
    void tout_reconstruit_toutes_les_orphelines() {
        when(service.orphelines()).thenReturn(List.of(NUIT_1, NUIT_2));
        when(service.reconstruire(eq(NUIT_1), any(), any()))
                .thenReturn(new RapportReconstruction(1L, 10, 20, RapportReconstruction.lacunesConnues()));
        when(service.reconstruire(eq(NUIT_2), any(), any()))
                .thenReturn(new RapportReconstruction(2L, 5, 8, RapportReconstruction.lacunesConnues()));
        StringWriter sortie = new StringWriter();

        int code = ligne(sortie).execute("--tout");

        assertThat(code).isZero();
        assertThat(sortie.toString())
                .contains("[1/2]")
                .contains("[2/2]")
                .contains("2 nuit(s) reconstruite(s), 0 ignorée(s)")
                .contains("15 séquence(s)")
                .contains("28 observation(s)");
    }

    @Test
    @DisplayName("#1711 --tout best-effort : une nuit qui échoue est ignorée, le lot continue")
    void tout_best_effort() {
        when(service.orphelines()).thenReturn(List.of(NUIT_1, NUIT_2));
        when(service.reconstruire(eq(NUIT_1), any(), any()))
                .thenReturn(new RapportReconstruction(1L, 10, 20, RapportReconstruction.lacunesConnues()));
        when(service.reconstruire(eq(NUIT_2), any(), any()))
                .thenThrow(new RegleMetierException("Le point d'écoute n'existe pas localement."));
        StringWriter sortie = new StringWriter();

        int code = ligne(sortie).execute("--tout");

        assertThat(code).isZero();
        assertThat(sortie.toString()).contains("ignorée").contains("1 nuit(s) reconstruite(s), 1 ignorée(s)");
    }

    @Test
    @DisplayName("#1711 --tout : rien à reconstruire quand aucune orpheline")
    void tout_rien_a_reconstruire() {
        when(service.orphelines()).thenReturn(List.of());
        StringWriter sortie = new StringWriter();

        int code = ligne(sortie).execute("--tout");

        assertThat(code).isZero();
        assertThat(sortie.toString()).contains("Aucune participation orpheline");
    }
}

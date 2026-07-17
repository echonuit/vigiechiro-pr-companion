package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.Provider;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.RapportReconstruction;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages.BilanReconstructionGroupe;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages.IssueNuit;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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
        // Le service porte la boucle (harmonisation passe 7) : on émule ses rappels d'issue par nuit + bilan.
        when(service.reconstruireTout(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Consumer<IssueNuit> issue = invocation.getArgument(3);
            issue.accept(new IssueNuit.Reconstruite(
                    NUIT_1, new RapportReconstruction(1L, 10, 20, RapportReconstruction.lacunesConnues())));
            issue.accept(new IssueNuit.Reconstruite(
                    NUIT_2, new RapportReconstruction(2L, 5, 8, RapportReconstruction.lacunesConnues())));
            return new BilanReconstructionGroupe(2, 0, 15, 28);
        });
        StringWriter sortie = new StringWriter();

        int code = ligne(sortie).execute("--tout");

        assertThat(code).isZero();
        assertThat(sortie.toString())
                .contains("[1/2]")
                .contains("[2/2]")
                .contains("passage 1")
                .contains("2 nuit(s) reconstruite(s), 0 ignorée(s)")
                .contains("15 séquence(s)")
                .contains("28 observation(s)");
    }

    @Test
    @DisplayName("#1711 --tout best-effort : une nuit qui échoue est ignorée, le lot continue")
    void tout_best_effort() {
        when(service.orphelines()).thenReturn(List.of(NUIT_1, NUIT_2));
        when(service.reconstruireTout(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Consumer<IssueNuit> issue = invocation.getArgument(3);
            issue.accept(new IssueNuit.Reconstruite(
                    NUIT_1, new RapportReconstruction(1L, 10, 20, RapportReconstruction.lacunesConnues())));
            issue.accept(new IssueNuit.Ignoree(NUIT_2, "Le point d'écoute n'existe pas localement."));
            return new BilanReconstructionGroupe(1, 1, 10, 20);
        });
        StringWriter sortie = new StringWriter();

        int code = ligne(sortie).execute("--tout");

        assertThat(code).isZero();
        assertThat(sortie.toString())
                .contains("ignorée : Le point")
                .contains("1 nuit(s) reconstruite(s), 1 ignorée(s)");
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

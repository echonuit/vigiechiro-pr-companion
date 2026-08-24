package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.lot.model.BilanVerification;
import fr.univ_amu.iut.lot.model.VerificationDepot;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `verifier-depot-vigiechiro` (#1132) : délégation à [VerificationDepot] (mocké, l'appariement est
/// couvert par `VerificationDepotTest`), rendu du bilan et **code retour scriptable** (0 = tout
/// retrouvé, 1 sinon), jeton ponctuel `--token`.
class VerifierDepotVigieChiroTest {

    private final VerificationDepot verification = mock(VerificationDepot.class);

    @AfterEach
    void nettoyerJetonPonctuel() {
        System.clearProperty("vigiechiro.token");
    }

    private CommandLine ligne(Optional<VerificationDepot> moteur, StringWriter sortie) {
        CommandLine ligne = new CommandLine(new VerifierDepotVigieChiro(moteur));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(new StringWriter(), true));
        return ligne;
    }

    @Test
    @DisplayName("tout retrouvé : bilan « dépôt vérifié », code retour 0")
    void tout_retrouve_code_zero() {
        when(verification.verifier(42L))
                .thenReturn(new BilanVerification("part-1", true, 4806, List.of("a.wav", "b.wav"), List.of()));
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(verification), sortie).execute("--passage", "42");

        assertThat(code).isZero();
        assertThat(sortie.toString())
                .contains("part-1")
                .contains("journal de traitement disponible")
                .contains("4806 donnée(s)")
                .contains("2/2 fichier(s) du plan local retrouvé(s)")
                .contains("dépôt vérifié");
    }

    @Test
    @DisplayName("fichiers manquants : liste « ! » plafonnée, journal indisponible signalé, code retour 1")
    void manquantes_code_un() {
        List<String> manquantes = IntStream.range(0, 25)
                .mapToObj(numero -> "seq_" + numero + ".wav")
                .toList();
        when(verification.verifier(42L)).thenReturn(new BilanVerification("part-1", false, 0, List.of(), manquantes));
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(verification), sortie).execute("--passage", "42");

        assertThat(code).isEqualTo(1);
        assertThat(sortie.toString())
                .contains("INDISPONIBLE")
                .contains("25/25 fichier(s) NON retrouvé(s)")
                .contains("! seq_0.wav")
                .contains("… et 5 autre(s).")
                .contains("Relancez le dépôt");
    }

    @Test
    @DisplayName("--token : jeton ponctuel posé pour la durée de la commande (propriété système)")
    void token_ponctuel_pose() {
        when(verification.verifier(42L))
                .thenReturn(new BilanVerification("part-1", true, 1, List.of("a.wav"), List.of()));

        ligne(Optional.of(verification), new StringWriter()).execute("--passage", "42", "--token", "jeton-x");

        assertThat(System.getProperty("vigiechiro.token")).isEqualTo("jeton-x");
    }
}

package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.validation.model.BilanPublication;
import fr.univ_amu.iut.validation.model.PublicationCorrections;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `publier-corrections-vigiechiro` (#723) : délégation à [PublicationCorrections] (mocké,
/// l'orchestration est couverte par `PublicationCorrectionsTest`), rendu du bilan, refus sur la
/// sortie d'erreur avec code `1`, jeton ponctuel `--token`.
class PublierCorrectionsVigieChiroTest {

    private final PublicationCorrections moteur = mock(PublicationCorrections.class);

    @AfterEach
    void nettoyerJetonPonctuel() {
        System.clearProperty("vigiechiro.token");
    }

    private CommandLine ligne(Optional<PublicationCorrections> service, StringWriter sortie, StringWriter erreurs) {
        CommandLine ligne = new CommandLine(new PublierCorrectionsVigieChiro(service));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(erreurs, true));
        return ligne;
    }

    @Test
    @DisplayName("publication complète : bilan rendu (envoyées + écartées par cause), code 0")
    void publication_complete_code_zero() {
        when(moteur.publier(42L)).thenReturn(new BilanPublication(3, 2, 1, 0, List.of()));
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(moteur), sortie, new StringWriter()).execute("--passage", "42");

        assertThat(code).isZero();
        assertThat(sortie.toString())
                .contains("3 envoyée(s)")
                .contains("2 à compléter")
                .contains("1 sans ancrage");
    }

    @Test
    @DisplayName("au moins un refus : détail sur la sortie d'erreur, code 1")
    void refus_code_un() {
        when(moteur.publier(42L))
                .thenReturn(
                        new BilanPublication(1, 0, 0, 0, List.of("Observation 7 (donnée d1, indice 2) : HTTP 404")));
        StringWriter sortie = new StringWriter();
        StringWriter erreurs = new StringWriter();

        int code = ligne(Optional.of(moteur), sortie, erreurs).execute("--passage", "42");

        assertThat(code).isEqualTo(1);
        assertThat(sortie.toString()).contains("1 refus");
        assertThat(erreurs.toString()).contains("REFUS : Observation 7");
    }

    @Test
    @DisplayName("service absent (feature publier-corrections désactivée) : refus explicite, code non nul")
    void service_absent_code_non_nul() {
        int code =
                ligne(Optional.empty(), new StringWriter(), new StringWriter()).execute("--passage", "42");

        assertThat(code).isNotZero();
    }

    @Test
    @DisplayName("--token : jeton ponctuel posé pour la durée de la commande (propriété système)")
    void token_ponctuel_pose() {
        when(moteur.publier(42L)).thenReturn(new BilanPublication(0, 0, 0, 0, List.of()));

        ligne(Optional.of(moteur), new StringWriter(), new StringWriter())
                .execute("--passage", "42", "--token", "jeton-x");

        assertThat(System.getProperty("vigiechiro.token")).isEqualTo("jeton-x");
    }
}

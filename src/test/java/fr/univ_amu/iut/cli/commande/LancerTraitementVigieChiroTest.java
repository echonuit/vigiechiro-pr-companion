package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `lancer-traitement-vigiechiro` (#984) : délègue le compute au moteur, **code retour scriptable**
/// (0 = accepté, 1 = refusé), jeton ponctuel `--token`. Moteur mocké.
class LancerTraitementVigieChiroTest {

    private final DepotVigieChiro depot = mock(DepotVigieChiro.class);

    @AfterEach
    void nettoyerJetonPonctuel() {
        System.clearProperty("vigiechiro.token");
    }

    private CommandLine ligne(Optional<DepotVigieChiro> moteur, StringWriter sortie) {
        CommandLine ligne = new CommandLine(new LancerTraitementVigieChiro(moteur));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(new StringWriter(), true));
        return ligne;
    }

    @Test
    @DisplayName("traitement accepté → code retour 0 + message de lancement")
    void traitement_accepte_code_zero() {
        when(depot.lancerTraitement(42L)).thenReturn(true);
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(depot), sortie).execute("--passage", "42");

        assertThat(code).isZero();
        assertThat(sortie.toString()).contains("Traitement lancé");
    }

    @Test
    @DisplayName("traitement refusé par le serveur → code retour 1")
    void traitement_refuse_code_un() {
        when(depot.lancerTraitement(42L)).thenReturn(false);

        int code = ligne(Optional.of(depot), new StringWriter()).execute("--passage", "42");

        assertThat(code).isEqualTo(1);
    }

    @Test
    @DisplayName("--token pose le jeton ponctuel (propriété système)")
    void option_token_pose_le_jeton_ponctuel() {
        when(depot.lancerTraitement(42L)).thenReturn(true);

        ligne(Optional.of(depot), new StringWriter()).execute("--passage", "42", "--token", "jeton-essai");

        assertThat(System.getProperty("vigiechiro.token")).isEqualTo("jeton-essai");
    }

    @Test
    @DisplayName("dépôt indisponible (contexte sans connexion) → échec d'exécution")
    void depot_indisponible_echoue() {
        int code = ligne(Optional.empty(), new StringWriter()).execute("--passage", "42");

        assertThat(code).isNotZero();
    }
}

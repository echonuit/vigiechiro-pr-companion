package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.ResultatLancement;
import fr.univ_amu.iut.commun.api.Traitement;
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
        when(depot.lancerTraitement(42L, false)).thenReturn(ResultatLancement.accepte());
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(depot), sortie).execute("--passage", "42");

        assertThat(code).isZero();
        assertThat(sortie.toString()).contains("Traitement lancé");
    }

    @Test
    @DisplayName("#1261 : traitement DÉJÀ en cours → code retour 0 (la commande est idempotente, pas en échec)")
    void traitement_deja_lance_code_zero() {
        // Rejouer la commande (script, cron, second essai) ne doit pas faire échouer le script : le
        // traitement est bel et bien en route, c'est tout ce qui compte.
        when(depot.lancerTraitement(42L, false))
                .thenReturn(ResultatLancement.dejaLance(
                        new Traitement(EtatTraitement.EN_COURS, null, null, null, null, null)));
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(depot), sortie).execute("--passage", "42");

        assertThat(code).isZero();
        assertThat(sortie.toString()).contains("déjà en cours");
    }

    @Test
    @DisplayName("#1261 : nuit déjà analysée → relance bloquée, code retour 1, message qui oriente vers l'import")
    void relance_bloquee_code_un() {
        when(depot.lancerTraitement(42L, false))
                .thenReturn(ResultatLancement.relanceBloquee(
                        new Traitement(EtatTraitement.FINI, null, null, null, null, null)));
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(depot), sortie).execute("--passage", "42");

        assertThat(code).isEqualTo(1);
        assertThat(sortie.toString()).contains("déjà été analysée", "importer-vigiechiro");
    }

    @Test
    @DisplayName("traitement refusé par le serveur → code retour 1")
    void traitement_refuse_code_un() {
        when(depot.lancerTraitement(42L, false)).thenReturn(ResultatLancement.refuse(403, "interdit"));

        int code = ligne(Optional.of(depot), new StringWriter()).execute("--passage", "42");

        assertThat(code).isEqualTo(1);
    }

    @Test
    @DisplayName("serveur injoignable → code retour 1")
    void serveur_injoignable_code_un() {
        when(depot.lancerTraitement(42L, false)).thenReturn(ResultatLancement.injoignable());
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(depot), sortie).execute("--passage", "42");

        assertThat(code).isEqualTo(1);
        assertThat(sortie.toString()).contains("injoignable");
    }

    @Test
    @DisplayName("#1265 : sans --forcer, la garde anti-relance s'applique (la nuit déjà analysée est protégée)")
    void sans_forcer_la_garde_sapplique() {
        when(depot.lancerTraitement(42L, false))
                .thenReturn(ResultatLancement.relanceBloquee(
                        new Traitement(EtatTraitement.FINI, null, null, null, null, null)));

        int code = ligne(Optional.of(depot), new StringWriter()).execute("--passage", "42");

        assertThat(code).isEqualTo(1);
        verify(depot).lancerTraitement(42L, false);
    }

    @Test
    @DisplayName("#1265 : --forcer lève la garde — le seul moyen de relancer une analyse en échec")
    void forcer_leve_la_garde() {
        // Après un ERREUR, il n'y a plus d'observations à perdre : la relance est le geste utile. Mais
        // elle reste explicite, jamais le défaut.
        when(depot.lancerTraitement(42L, true)).thenReturn(ResultatLancement.accepte());
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(depot), sortie).execute("--passage", "42", "--forcer");

        assertThat(code).isZero();
        assertThat(sortie.toString()).contains("Traitement lancé");
        verify(depot).lancerTraitement(42L, true);
    }

    @Test
    @DisplayName("--token pose le jeton ponctuel (propriété système)")
    void option_token_pose_le_jeton_ponctuel() {
        when(depot.lancerTraitement(42L, false)).thenReturn(ResultatLancement.accepte());

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

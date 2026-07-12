package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `synchroniser-vigiechiro` (#1181) : contrôle d'identité avant synchronisation, agrégation du
/// résumé des rapprocheurs (mockés - leur comportement est couvert par leurs propres tests), jeton
/// ponctuel `--token`, code retour scriptable.
class SynchroniserVigieChiroTest {

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);

    @AfterEach
    void nettoyerJetonPonctuel() {
        System.clearProperty("vigiechiro.token");
    }

    private CommandLine ligne(Set<RapprochementVigieChiro> rapprocheurs, StringWriter sortie) {
        CommandLine ligne = new CommandLine(new SynchroniserVigieChiro(client, () -> rapprocheurs));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(new StringWriter(), true));
        return ligne;
    }

    @Test
    @DisplayName("connecté : identité affichée, rapprocheurs rejoués, résumé agrégé, code 0")
    void connecte_synchronise_et_resume() {
        when(client.moi()).thenReturn(Optional.of(new ProfilVigieChiro("u-1", "sebastien", "Observateur")));
        Set<RapprochementVigieChiro> rapprocheurs = Set.of(
                c -> Optional.of(new RapportSynchro("taxons", 385)), c -> Optional.of(new RapportSynchro("sites", 3)));
        StringWriter sortie = new StringWriter();

        int code = ligne(rapprocheurs, sortie).execute();

        assertThat(code).isZero();
        assertThat(sortie.toString())
                .contains("Connecté : sebastien.")
                .contains("Synchronisé : ")
                .contains("385 taxons")
                .contains("3 sites");
    }

    @Test
    @DisplayName("rien récupéré (rapprocheurs muets) : message explicite, code 0 quand même")
    void rien_a_synchroniser() {
        when(client.moi()).thenReturn(Optional.of(new ProfilVigieChiro("u-1", "sebastien", "Observateur")));
        StringWriter sortie = new StringWriter();

        int code = ligne(Set.of(c -> Optional.empty()), sortie).execute();

        assertThat(code).isZero();
        assertThat(sortie.toString()).contains("Rien à synchroniser");
    }

    @Test
    @DisplayName("jeton mort ou hors ligne : refus explicite avant toute synchronisation, code non nul")
    void non_connecte_code_non_nul() {
        when(client.moi()).thenReturn(Optional.empty());
        boolean[] appele = {false};
        Set<RapprochementVigieChiro> rapprocheurs = Set.of(c -> {
            appele[0] = true;
            return Optional.empty();
        });

        int code = ligne(rapprocheurs, new StringWriter()).execute();

        assertThat(code).isNotZero();
        assertThat(appele[0])
                .as("aucun rapprocheur rejoué sans identité vérifiée")
                .isFalse();
    }

    @Test
    @DisplayName("--token : jeton ponctuel posé pour la durée de la commande (propriété système)")
    void token_ponctuel_pose() {
        when(client.moi()).thenReturn(Optional.of(new ProfilVigieChiro("u-1", "sebastien", "Observateur")));

        ligne(Set.of(), new StringWriter()).execute("--token", "jeton-x");

        assertThat(System.getProperty("vigiechiro.token")).isEqualTo("jeton-x");
    }
}

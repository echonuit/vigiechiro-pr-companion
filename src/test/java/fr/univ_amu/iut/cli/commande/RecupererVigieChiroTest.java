package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `recuperer-vigiechiro` (#1181) : contrôle d'identité avant rapatriement, agrégation du
/// résumé des rapprocheurs (mockés - leur comportement est couvert par leurs propres tests), jeton
/// ponctuel `--token`, code retour scriptable.
class RecupererVigieChiroTest {

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);

    @AfterEach
    void nettoyerJetonPonctuel() {
        System.clearProperty("vigiechiro.token");
    }

    private CommandLine ligne(Set<RapprochementVigieChiro> rapprocheurs, StringWriter sortie) {
        CommandLine ligne = new CommandLine(new RecupererVigieChiro(client, () -> rapprocheurs));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(new StringWriter(), true));
        return ligne;
    }

    @Test
    @DisplayName("connecté : identité affichée, rapprocheurs rejoués, résumé agrégé, code 0")
    void connecte_recupere_et_resume() {
        when(client.moi()).thenReturn(ReponseApi.succes(new ProfilVigieChiro("u-1", "sebastien", "Observateur")));
        Set<RapprochementVigieChiro> rapprocheurs = Set.of(
                c -> Optional.of(new RapportSynchro("taxons", 385)), c -> Optional.of(new RapportSynchro("sites", 3)));
        StringWriter sortie = new StringWriter();

        int code = ligne(rapprocheurs, sortie).execute();

        assertThat(code).isZero();
        assertThat(sortie.toString())
                .contains("Connecté : sebastien.")
                .contains("Récupéré : ")
                .contains("385 taxons")
                .contains("3 sites");
    }

    @Test
    @DisplayName("#2558 : la commande RELAIE la progression d un rapprocheur long, ligne par ligne")
    void progression_relayee_ligne_par_ligne() {
        when(client.moi()).thenReturn(ReponseApi.succes(new ProfilVigieChiro("u-1", "sebastien", "Observateur")));
        // Un rapprocheur LONG, qui surcharge la variante suivie : c'est le cas des passages depuis #2557.
        // Les lambdas des autres tests passent, elles, par le `default` du port, qui ignore le suivi - donc
        // aucune n'exerçait ce relais.
        RapprochementVigieChiro passages = new RapprochementVigieChiro() {
            @Override
            public Optional<RapportSynchro> synchroniser(ClientVigieChiro c) {
                return Optional.of(new RapportSynchro("nuit(s) récupérée(s)", 2));
            }

            @Override
            public Optional<RapportSynchro> synchroniser(
                    ClientVigieChiro c,
                    java.util.function.Consumer<fr.univ_amu.iut.commun.model.Progression> suivi,
                    fr.univ_amu.iut.commun.model.JetonAnnulation jeton) {
                suivi.accept(new fr.univ_amu.iut.commun.model.Progression("Nuits 1/2", 0.5));
                suivi.accept(new fr.univ_amu.iut.commun.model.Progression("Nuits 2/2", 1.0));
                return synchroniser(c);
            }
        };
        StringWriter sortie = new StringWriter();

        int code = ligne(Set.of(passages), sortie).execute();

        assertThat(code).isZero();
        assertThat(sortie.toString())
                .as("sans ces lignes, la commande reste muette plusieurs minutes sur un gros compte")
                .contains("Nuits 1/2")
                .contains("Nuits 2/2")
                .contains("2 nuit(s) récupérée(s)");
    }

    @Test
    @DisplayName("rien récupéré (rapprocheurs muets) : message explicite, code 0 quand même")
    void rien_a_recuperer() {
        when(client.moi()).thenReturn(ReponseApi.succes(new ProfilVigieChiro("u-1", "sebastien", "Observateur")));
        StringWriter sortie = new StringWriter();

        int code = ligne(Set.of(c -> Optional.empty()), sortie).execute();

        assertThat(code).isZero();
        assertThat(sortie.toString()).contains("Rien à récupérer");
    }

    @Test
    @DisplayName("jeton mort ou hors ligne : refus explicite avant tout rapatriement, code non nul")
    void non_connecte_code_non_nul() {
        when(client.moi()).thenReturn(ReponseApi.injoignable("délai d'attente dépassé"));
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
        when(client.moi()).thenReturn(ReponseApi.succes(new ProfilVigieChiro("u-1", "sebastien", "Observateur")));

        ligne(Set.of(), new StringWriter()).execute("--token", "jeton-x");

        assertThat(System.getProperty("vigiechiro.token")).isEqualTo("jeton-x");
    }
}

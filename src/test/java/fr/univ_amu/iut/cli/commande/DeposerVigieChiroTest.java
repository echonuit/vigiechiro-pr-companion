package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.SuiviDepot;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `deposer-vigiechiro` (#1043) : délégation au moteur reprenable (#982), sortie par fichier, bilan et
/// **code retour scriptable** (0 = dépôt complet, 1 sinon), jeton ponctuel `--token`. Moteur et service
/// mockés (le comportement reprenable est couvert par `DepotVigieChiroTest`).
class DeposerVigieChiroTest {

    private final ServiceLot serviceLot = mock(ServiceLot.class);
    private final DepotVigieChiro depot = mock(DepotVigieChiro.class);

    @AfterEach
    void nettoyerJetonPonctuel() {
        System.clearProperty("vigiechiro.token");
    }

    private CommandLine ligne(Optional<DepotVigieChiro> moteur, StringWriter sortie) {
        CommandLine ligne = new CommandLine(new DeposerVigieChiro(serviceLot, moteur));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(new StringWriter(), true));
        return ligne;
    }

    @Test
    @DisplayName("dépôt complet : une ligne « + » par fichier, bilan « complet », code retour 0")
    void depot_complet_code_zero() {
        when(serviceLot.sequencesADeposer(42L)).thenReturn(List.of(Path.of("/ws/a.wav")));
        when(depot.deposer(eq(42L), any(), any(), any())).thenAnswer(invocation -> {
            SuiviDepot suivi = invocation.getArgument(3);
            suivi.planEtabli(List.of(unite("a.wav", StatutDepotUnite.A_DEPOSER)));
            suivi.uniteDeposee(unite("a.wav", StatutDepotUnite.DEPOSE));
            return new BilanDepot("part-1", 1, List.of());
        });
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(depot), sortie).execute("--passage", "42");

        assertThat(code).isZero();
        assertThat(sortie.toString())
                .contains("Plan de dépôt : 1 fichier(s).")
                .contains("+ a.wav")
                .contains("Dépôt complet")
                .contains("Déposé");
    }

    @Test
    @DisplayName("dépôt incomplet : ligne « ! » avec la raison, bilan « INCOMPLET », code retour 1")
    void depot_incomplet_code_un() {
        when(serviceLot.sequencesADeposer(42L)).thenReturn(List.of(Path.of("/ws/a.wav"), Path.of("/ws/b.wav")));
        when(depot.deposer(eq(42L), any(), any(), any())).thenAnswer(invocation -> {
            SuiviDepot suivi = invocation.getArgument(3);
            suivi.uniteEchouee("b.wav", "coupure réseau");
            return new BilanDepot("part-1", 1, List.of("b.wav"));
        });
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(depot), sortie).execute("--passage", "42");

        assertThat(code).isEqualTo(1);
        assertThat(sortie.toString())
                .contains("! b.wav — coupure réseau")
                .contains("INCOMPLET")
                .contains("Relancez la commande");
    }

    @Test
    @DisplayName("--token pose le jeton ponctuel (propriété système), sans toucher à la connexion enregistrée")
    void option_token_pose_le_jeton_ponctuel() {
        when(serviceLot.sequencesADeposer(42L)).thenReturn(List.of(Path.of("/ws/a.wav")));
        when(depot.deposer(eq(42L), any(), any(), any())).thenReturn(new BilanDepot("part-1", 1, List.of()));

        ligne(Optional.of(depot), new StringWriter()).execute("--passage", "42", "--token", "jeton-essai");

        assertThat(System.getProperty("vigiechiro.token")).isEqualTo("jeton-essai");
    }

    @Test
    @DisplayName("dépôt indisponible (contexte sans connexion) : échec d'exécution, rien n'est tenté")
    void depot_indisponible_echoue() {
        int code = ligne(Optional.empty(), new StringWriter()).execute("--passage", "42");

        assertThat(code).isNotZero();
    }

    @Test
    @DisplayName("rendrePlan : mentionne la reprise quand des fichiers sont déjà en ligne")
    void rendre_plan_reprise() {
        assertThat(DeposerVigieChiro.rendrePlan(List.of(
                        unite("a.wav", StatutDepotUnite.DEPOSE),
                        unite("b.wav", StatutDepotUnite.ECHEC),
                        unite("c.wav", StatutDepotUnite.A_DEPOSER))))
                .isEqualTo("Plan de dépôt : 3 fichier(s) (1 déjà en ligne, reprise).");
    }

    private static DepotUnite unite(String identifiant, StatutDepotUnite statut) {
        return new DepotUnite(1L, 42L, identifiant, TypeDepotUnite.WAV, statut, null, null, "2026-07-11T15:00:00");
    }
}

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
        when(serviceLot.fichiersDepotParDefaut(42L)).thenReturn(List.of(Path.of("/ws/a.wav")));
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
        when(serviceLot.fichiersDepotParDefaut(42L)).thenReturn(List.of(Path.of("/ws/a.wav"), Path.of("/ws/b.wav")));
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
        when(serviceLot.fichiersDepotParDefaut(42L)).thenReturn(List.of(Path.of("/ws/a.wav")));
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
    @DisplayName("#984 : --archives dépose les ZIP du dossier depot/ au lieu des séquences WAV")
    void option_archives_depose_les_zip() {
        when(serviceLot.consulterLot(42L))
                .thenReturn(new fr.univ_amu.iut.lot.model.EtatLot(
                        fr.univ_amu.iut.commun.model.StatutWorkflow.PRET_A_DEPOSER,
                        "/ws/session-42",
                        4806,
                        8192L,
                        List.of(),
                        null));
        when(serviceLot.archivesDepot("/ws/session-42"))
                .thenReturn(List.of(
                        new fr.univ_amu.iut.lot.model.ArchiveDepot(
                                Path.of("/ws/session-42/depot/Car-1.zip"), 1, 2048L, 2),
                        new fr.univ_amu.iut.lot.model.ArchiveDepot(
                                Path.of("/ws/session-42/depot/Car-2.zip"), 2, 4096L, 3)));
        when(depot.deposer(eq(42L), any(), any(), any())).thenReturn(new BilanDepot("part-1", 2, List.of()));
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(depot), sortie).execute("--passage", "42", "--archives");

        assertThat(code).isZero();
        org.mockito.Mockito.verify(depot)
                .deposer(
                        eq(42L),
                        eq(List.of(
                                Path.of("/ws/session-42/depot/Car-1.zip"), Path.of("/ws/session-42/depot/Car-2.zip"))),
                        any(),
                        any());
        org.mockito.Mockito.verify(serviceLot, org.mockito.Mockito.never()).sequencesADeposer(42L);
    }

    @Test
    @DisplayName("#984 : --archives sans archive sur disque → refus explicite, rien n'est tenté")
    void option_archives_sans_archive_echoue() {
        when(serviceLot.consulterLot(42L))
                .thenReturn(new fr.univ_amu.iut.lot.model.EtatLot(
                        fr.univ_amu.iut.commun.model.StatutWorkflow.PRET_A_DEPOSER,
                        "/ws/session-42",
                        4806,
                        8192L,
                        List.of(),
                        null));
        when(serviceLot.archivesDepot("/ws/session-42")).thenReturn(List.of());

        int code = ligne(Optional.of(depot), new StringWriter()).execute("--passage", "42", "--archives");

        assertThat(code).isNotZero();
        org.mockito.Mockito.verifyNoInteractions(depot);
    }

    @Test
    @DisplayName("#984 : sans option, le choix des fichiers est celui de M-Lot (ZIP d'abord, repli WAV)")
    void defaut_delegue_le_choix_au_service() {
        when(serviceLot.fichiersDepotParDefaut(42L)).thenReturn(List.of(Path.of("/ws/session-42/depot/Car-1.zip")));
        when(depot.deposer(eq(42L), any(), any(), any())).thenReturn(new BilanDepot("part-1", 1, List.of()));

        int code = ligne(Optional.of(depot), new StringWriter()).execute("--passage", "42");

        assertThat(code).isZero();
        org.mockito.Mockito.verify(depot)
                .deposer(eq(42L), eq(List.of(Path.of("/ws/session-42/depot/Car-1.zip"))), any(), any());
        org.mockito.Mockito.verify(serviceLot, org.mockito.Mockito.never()).sequencesADeposer(42L);
    }

    @Test
    @DisplayName("#984 : --wav force les séquences WAV, même si des archives existent")
    void option_wav_force_les_sequences() {
        when(serviceLot.sequencesADeposer(42L)).thenReturn(List.of(Path.of("/ws/a.wav")));
        when(depot.deposer(eq(42L), any(), any(), any())).thenReturn(new BilanDepot("part-1", 1, List.of()));

        int code = ligne(Optional.of(depot), new StringWriter()).execute("--passage", "42", "--wav");

        assertThat(code).isZero();
        org.mockito.Mockito.verify(depot).deposer(eq(42L), eq(List.of(Path.of("/ws/a.wav"))), any(), any());
        org.mockito.Mockito.verify(serviceLot, org.mockito.Mockito.never()).fichiersDepotParDefaut(42L);
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

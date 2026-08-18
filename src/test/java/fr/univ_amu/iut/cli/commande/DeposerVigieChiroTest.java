package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.CauseRefus;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.EchecUnite;
import fr.univ_amu.iut.lot.model.ModeDepot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.SourceDepot;
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
        when(serviceLot.sourceDepotParDefaut(42L)).thenReturn(SourceDepot.desFichiers(List.of(Path.of("/ws/a.wav"))));
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
        when(serviceLot.sourceDepotParDefaut(42L))
                .thenReturn(SourceDepot.desFichiers(List.of(Path.of("/ws/a.wav"), Path.of("/ws/b.wav"))));
        when(depot.deposer(eq(42L), any(), any(), any())).thenAnswer(invocation -> {
            SuiviDepot suivi = invocation.getArgument(3);
            suivi.uniteEchouee("b.wav", "coupure réseau", false);
            return new BilanDepot("part-1", 1, List.of(EchecUnite.rejouable("b.wav", "HTTP 503")));
        });
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(depot), sortie).execute("--passage", "42");

        assertThat(code).isEqualTo(1);
        assertThat(sortie.toString())
                .contains("! b.wav : coupure réseau")
                .contains("INCOMPLET")
                .contains("Relancez la commande");
    }

    @Test
    @DisplayName("--token pose le jeton ponctuel (propriété système), sans toucher à la connexion enregistrée")
    void option_token_pose_le_jeton_ponctuel() {
        when(serviceLot.sourceDepotParDefaut(42L)).thenReturn(SourceDepot.desFichiers(List.of(Path.of("/ws/a.wav"))));
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
        SourceDepot source = SourceDepot.desFichiers(List.of(Path.of("/ws/session-42/depot/Car-1.zip")));
        when(serviceLot.sourceDepot(42L, ModeDepot.ARCHIVES_ZIP)).thenReturn(source);
        when(depot.deposer(eq(42L), any(), any(), any())).thenReturn(new BilanDepot("part-1", 2, List.of()));

        int code = ligne(Optional.of(depot), new StringWriter()).execute("--passage", "42", "--archives");

        assertThat(code).isZero();
        org.mockito.Mockito.verify(depot).deposer(eq(42L), eq(source), any(), any());
        org.mockito.Mockito.verify(serviceLot, org.mockito.Mockito.never()).sourceDepotParDefaut(42L);
    }

    @Test
    @DisplayName("#984 : --archives sans archive sur disque → refus explicite, rien n'est tenté")
    void option_archives_sans_archive_regenere_au_lieu_d_echouer() {
        // Avant #1994, l'option listait les ZIP PRÉSENTS et refusait s'il n'y en avait aucun. C'est
        // devenu le cas normal : le pipeline libère ses archives au fil de l'eau, donc après un dépôt
        // interrompu le dossier est vide. Forcer le mode ZIP rend maintenant une source régénérable.
        SourceDepot regenerable = SourceDepot.desFichiers(List.of(Path.of("/ws/session-42/depot/Car-1.zip")));
        when(serviceLot.sourceDepot(42L, ModeDepot.ARCHIVES_ZIP)).thenReturn(regenerable);
        when(depot.deposer(eq(42L), any(), any(), any())).thenReturn(new BilanDepot("part-1", 1, List.of()));

        int code = ligne(Optional.of(depot), new StringWriter()).execute("--passage", "42", "--archives");

        assertThat(code).isZero();
        org.mockito.Mockito.verify(serviceLot, org.mockito.Mockito.never()).archivesDepot(anyString());
    }

    @Test
    @DisplayName("#984 : sans option, le choix des fichiers est celui de M-Lot (ZIP d'abord, repli WAV)")
    void defaut_delegue_le_choix_au_service() {
        when(serviceLot.sourceDepotParDefaut(42L))
                .thenReturn(SourceDepot.desFichiers(List.of(Path.of("/ws/session-42/depot/Car-1.zip"))));
        when(depot.deposer(eq(42L), any(), any(), any())).thenReturn(new BilanDepot("part-1", 1, List.of()));

        int code = ligne(Optional.of(depot), new StringWriter()).execute("--passage", "42");

        assertThat(code).isZero();
        org.mockito.Mockito.verify(depot)
                .deposer(
                        eq(42L),
                        eq(SourceDepot.desFichiers(List.of(Path.of("/ws/session-42/depot/Car-1.zip")))),
                        any(),
                        any());
        org.mockito.Mockito.verify(serviceLot, org.mockito.Mockito.never()).sequencesADeposer(42L);
    }

    @Test
    @DisplayName("#984 : --wav force les séquences WAV, même si des archives existent")
    void option_wav_force_les_sequences() {
        SourceDepot source = SourceDepot.desFichiers(List.of(Path.of("/ws/a.wav")));
        when(serviceLot.sourceDepot(42L, ModeDepot.SEQUENCES_WAV)).thenReturn(source);
        when(depot.deposer(eq(42L), any(), any(), any())).thenReturn(new BilanDepot("part-1", 1, List.of()));

        int code = ligne(Optional.of(depot), new StringWriter()).execute("--passage", "42", "--wav");

        assertThat(code).isZero();
        org.mockito.Mockito.verify(depot).deposer(eq(42L), eq(source), any(), any());
        org.mockito.Mockito.verify(serviceLot, org.mockito.Mockito.never()).sourceDepotParDefaut(42L);
    }

    @Test
    @DisplayName("clôture #2802 : le bilan dit le VOLUME en ligne, que l'écran affiche depuis #2653")
    void rendre_bilan_dit_le_volume() {
        String complet = DeposerVigieChiro.rendreBilan(new BilanDepot("p-1", 14, List.of(), 4_500_000_000L));
        String partiel = DeposerVigieChiro.rendreBilan(
                new BilanDepot("p-1", 9, List.of(EchecUnite.rejouable("Car-10.zip", "HTTP 503")), 2_900_000_000L));

        assertThat(complet).contains("14 fichier(s) téléversé(s) (4,5 Go)");
        assertThat(partiel).contains("9 fichier(s) téléversé(s) (2,9 Go)").contains("1 en échec");
    }

    @Test
    @DisplayName("#3962 : la CLI ne dit plus « relancez » d'une archive que la relance ne reprendra pas")
    void rendre_bilan_distingue_les_refus_definitifs() {
        String refuse = DeposerVigieChiro.rendreBilan(new BilanDepot(
                "p-1", 9, List.of(new EchecUnite("Car-10.zip", "HTTP 422", true, CauseRefus.CONTENU)), 0));

        assertThat(refuse)
                .as("la relance était promise pour une archive que Vigie-Chiro a refusée")
                .doesNotContain("Relancez la commande");
        // ⚠️ Cette assertion disait « que la relance ne reprendra pas », et c'était FAUX : la mesure de
        // #3946 montre que la relance les retente bel et bien. Ce qui ne repart pas, c'est l'archive
        // INCHANGÉE - le contenu doit changer, pas la façon de le renvoyer.
        assertThat(refuse).contains("Car-10.zip").contains("relancer telles quelles ferait refuser de même");
        assertThat(refuse)
                .as("le geste qui marche est vérifié, donc il se nomme (ADR 3854)")
                .contains("Régénérez les archives");
    }

    @Test
    @DisplayName("#3962 : la CLI ne conseille la reconnexion que si elle peut lever la cause")
    void rendre_bilan_ne_conseille_que_le_geste_applicable() {
        String droits = DeposerVigieChiro.rendreBilan(new BilanDepot(
                "p-1", 9, List.of(new EchecUnite("Car-10.zip", "HTTP 403", true, CauseRefus.AUTHENTIFICATION)), 0));
        String contenu = DeposerVigieChiro.rendreBilan(new BilanDepot(
                "p-1", 9, List.of(new EchecUnite("Car-11.zip", "HTTP 422", true, CauseRefus.CONTENU)), 0));

        assertThat(droits).contains("Reconnectez-vous");
        assertThat(contenu).doesNotContain("Reconnectez-vous");
    }

    @Test
    @DisplayName("#3962 : reprenables et refusés se disent tous les deux, chacun avec son compte")
    void rendre_bilan_dit_les_deux_familles() {
        String melange = DeposerVigieChiro.rendreBilan(new BilanDepot(
                "p-1",
                8,
                List.of(
                        EchecUnite.rejouable("Car-10.zip", "HTTP 503"),
                        new EchecUnite("Car-11.zip", "HTTP 422", true, CauseRefus.CONTENU)),
                0));

        assertThat(melange).contains("reprendre les 1 manquante(s)").contains("1 refusée(s) par Vigie-Chiro");
    }

    @Test
    @DisplayName("volume non mesuré : rien n'est dit, plutôt qu'un « 0 Ko téléversé »")
    void rendre_bilan_tait_un_volume_absent() {
        String texte = DeposerVigieChiro.rendreBilan(new BilanDepot("p-1", 3, List.of()));

        assertThat(texte)
                .as("annoncer une absence fait chercher ce qui n'a pas eu lieu")
                .doesNotContain("(0")
                .contains("3 fichier(s) téléversé(s) (participation p-1)");
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

    @Test
    @DisplayName("clôture #2350 : une nouvelle tentative se dit, au lieu de passer pour un blocage")
    void reprise_annoncee_sur_la_console() {
        StringWriter journal = new StringWriter();
        SuiviDepot suivi = new DeposerVigieChiro.SuiviConsole(new PrintWriter(journal, true));

        suivi.uniteReprise("Car640380-2026-Pass1-A1.zip", java.time.Duration.ofSeconds(8));

        // Depuis le réessai gradué (#2354), une coupure momentanée n'échoue plus : elle ATTEND. L'écran le
        // disait ; la console se taisait, et une temporisation passait pour un gel - ce qu'un utilisateur
        // interrompt au clavier, annulant un dépôt qui allait aboutir.
        assertThat(journal.toString()).contains("Car640380-2026-Pass1-A1.zip").contains("nouvelle tentative dans 8 s");
    }

    private static DepotUnite unite(String identifiant, StatutDepotUnite statut) {
        return new DepotUnite(1L, 42L, identifiant, TypeDepotUnite.WAV, statut, null, null, "2026-07-11T15:00:00");
    }
}

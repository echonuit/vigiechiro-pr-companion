package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.Lot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.passage.model.Passage;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// Compte rendu du dépôt de `deposer` (#617). `rendreDepot` est une fonction pure : passage, date de
/// dépôt, nombre de séquences et volume lisible ; le volume peut être `null` (non calculé).
class DeposerTest {

    @Test
    @DisplayName("Dépôt avec volume : passage, date, nombre de séquences et volume lisible")
    void rendre_depot_avec_volume() {
        String texte = Deposer.rendreDepot(12L, 128, 536_870_912L, "2026-06-20T10:00:00");

        assertThat(texte)
                // La base porte la date avec son heure, en ISO : elle se lit comme dans `statut-passage` (#5761).
                .contains("Passage #12 déposé le 20/06/2026.")
                .doesNotContain("2026-06-20")
                .contains("128 séquence(s)")
                .contains("537 Mo");
    }

    @Test
    @DisplayName("Volume non calculé (null) : mention « volume inconnu »")
    void rendre_depot_volume_inconnu() {
        String texte = Deposer.rendreDepot(3L, 0, null, "2026-06-20T10:00:00");

        assertThat(texte).contains("Passage #3").contains("0 séquence(s)").contains("volume inconnu");
    }

    /// `deposer` commençait toujours par préparer le lot, et la préparation n'admet qu'un passage
    /// « Vérifié » : un dépôt entamé était donc refusé, alors que le moteur autorise bien « Dépôt en
    /// cours » vers « Déposé ». C'est le dernier geste du repli manuel (#5867), et le défaut que #5599
    /// avait corrigé dans `exporter-lot`.
    @Test
    @DisplayName("#5867 : sur un dépôt entamé, deposer marque le passage sans le re-préparer")
    void sur_un_depot_entame_deposer_marque_sans_re_preparer() {
        ServiceLot service = mock(ServiceLot.class);
        when(service.consulterLot(42L))
                .thenReturn(new EtatLot(
                        StatutWorkflow.DEPOT_EN_COURS, "/ws/session-42", 128, 537_000_000L, List.of(), null));
        Passage depose = mock(Passage.class);
        when(depose.deposeLe()).thenReturn("2026-06-20T10:00:00");
        when(service.marquerDepose(42L)).thenReturn(depose);
        StringWriter sortie = new StringWriter();

        int code = ligne(service, sortie).execute("--passage", "42");

        assertThat(code).isZero();
        verify(service, never()).preparerLot(42L);
        verify(service).marquerDepose(42L);
        assertThat(sortie.toString())
                .contains("Passage #42 déposé le 20/06/2026")
                .contains("128 séquence(s)");
    }

    @Test
    @DisplayName("#5867 : sur un passage vérifié, deposer prépare toujours avant de marquer")
    void sur_un_passage_verifie_deposer_prepare_puis_marque() {
        ServiceLot service = mock(ServiceLot.class);
        when(service.consulterLot(42L))
                .thenReturn(new EtatLot(StatutWorkflow.VERIFIE, "/ws/session-42", 128, 537_000_000L, List.of(), null));
        when(service.preparerLot(42L)).thenReturn(new Lot(42L, "/ws/session-42", List.of(), 537_000_000L));
        Passage depose = mock(Passage.class);
        when(depose.deposeLe()).thenReturn("2026-06-20T10:00:00");
        when(service.marquerDepose(42L)).thenReturn(depose);

        int code = ligne(service, new StringWriter()).execute("--passage", "42");

        assertThat(code).isZero();
        verify(service).preparerLot(42L);
        verify(service).marquerDepose(42L);
    }

    private static CommandLine ligne(ServiceLot service, StringWriter sortie) {
        CommandLine ligne = new CommandLine(new Deposer(service));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(new StringWriter(), true));
        return ligne;
    }
}

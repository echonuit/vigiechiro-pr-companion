package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.Lot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import picocli.CommandLine;

/// `exporter-lot` génère ce que l'écran génère (#5599, ADR 0014).
///
/// La commande préparait **toujours** avant de générer, et la préparation n'admet que « Vérifié » : un
/// passage déjà préparé, ou dont le dépôt est entamé, était refusé avant même d'atteindre la
/// génération, quand l'écran savait régénérer.
class ExporterLotTest {

    private static final long PASSAGE = 42L;

    private final ServiceLot service = mock(ServiceLot.class);

    private final StringWriter sortie = new StringWriter();

    private int executer() {
        CommandLine ligne = new CommandLine(new ExporterLot(service));
        ligne.setOut(new PrintWriter(sortie));
        ligne.setErr(new PrintWriter(new StringWriter()));
        return ligne.execute("--passage", String.valueOf(PASSAGE));
    }

    private void passageAuStatut(StatutWorkflow statut) {
        passageAuStatut(statut, 2048L);
    }

    private void passageAuStatut(StatutWorkflow statut, Long volume) {
        when(service.consulterLot(PASSAGE)).thenReturn(new EtatLot(statut, "/ws/s", 2, volume, List.of(), null));
        when(service.preparerLot(anyLong())).thenReturn(new Lot(PASSAGE, "/ws/s", List.of(), 2048L));
        when(service.genererArchivesDepot(PASSAGE))
                .thenReturn(List.of(new ArchiveDepot(Path.of("/ws/s/depot/Car-1.zip"), 1, 2048L, 2)));
    }

    @Test
    @DisplayName("un dépôt entamé se régénère sans nouvelle préparation, que le workflow refuserait")
    void un_depot_entame_se_regenere_sans_preparer() {
        passageAuStatut(StatutWorkflow.DEPOT_EN_COURS);

        assertThat(executer()).isZero();

        verify(service, never()).preparerLot(anyLong());
        verify(service).genererArchivesDepot(PASSAGE);
        assertThat(sortie.toString())
                .contains("Dépôt déjà préparé pour le passage #42 (Dépôt en cours).")
                .contains("Séquences : 2")
                .contains("Volume    : 2048 octets")
                .contains("Dossier   : /ws/s")
                .contains("Archives de dépôt (1)")
                .contains("Car-1.zip (2 fichiers, 2048 octets)");
    }

    @Test
    @DisplayName("un volume inconnu s'écrit « - », pas « null octets »")
    void un_volume_inconnu_s_ecrit_tiret() {
        passageAuStatut(StatutWorkflow.DEPOT_EN_COURS, null);

        executer();

        assertThat(sortie.toString()).contains("Volume    : -").doesNotContain("null");
    }

    @Test
    @DisplayName("un passage vérifié se prépare, puis se génère, comme avant")
    void un_passage_verifie_se_prepare_puis_se_genere() {
        passageAuStatut(StatutWorkflow.VERIFIE);

        assertThat(executer()).isZero();

        InOrder ordre = inOrder(service);
        ordre.verify(service).preparerLot(PASSAGE);
        ordre.verify(service).genererArchivesDepot(PASSAGE);
        assertThat(sortie.toString())
                .contains("Dépôt prêt pour le passage #42.")
                .contains("Dossier   : /ws/s");
    }

    /// La commande et l'écran passent par la même génération : ce que le service refuse, la commande
    /// ne le maquille pas (#5975). Quand toutes les séquences sont déjà en ligne, il n'y a rien à
    /// déposer à la main, et aucune archive n'est annoncée.
    @Test
    @DisplayName("#5975 : quand le service refuse parce que tout est en ligne, la commande n'annonce aucune archive")
    void tout_est_en_ligne_la_commande_n_annonce_aucune_archive() {
        passageAuStatut(StatutWorkflow.DEPOT_EN_COURS);
        when(service.genererArchivesDepot(PASSAGE))
                .thenThrow(new RegleMetierException("Toutes les séquences de cette nuit sont déjà sur Vigie-Chiro :"
                        + " il ne reste rien à déposer à la main."));

        assertThat(executer()).as("un refus n'est pas un succès").isNotZero();

        assertThat(sortie.toString()).doesNotContain("Archives de dépôt");
    }
}

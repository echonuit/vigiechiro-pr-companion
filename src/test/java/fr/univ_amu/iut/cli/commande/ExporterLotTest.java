package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private int executer() {
        CommandLine ligne = new CommandLine(new ExporterLot(service));
        ligne.setOut(new PrintWriter(new StringWriter()));
        ligne.setErr(new PrintWriter(new StringWriter()));
        return ligne.execute("--passage", String.valueOf(PASSAGE));
    }

    private void passageAuStatut(StatutWorkflow statut) {
        when(service.consulterLot(PASSAGE)).thenReturn(new EtatLot(statut, "/ws/s", 2, 2048L, List.of(), null));
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
    }

    @Test
    @DisplayName("un passage vérifié se prépare, puis se génère, comme avant")
    void un_passage_verifie_se_prepare_puis_se_genere() {
        passageAuStatut(StatutWorkflow.VERIFIE);

        assertThat(executer()).isZero();

        InOrder ordre = inOrder(service);
        ordre.verify(service).preparerLot(PASSAGE);
        ordre.verify(service).genererArchivesDepot(PASSAGE);
    }
}

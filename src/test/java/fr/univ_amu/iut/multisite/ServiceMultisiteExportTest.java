package fr.univ_amu.iut.multisite;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Test de la matérialisation disque de la vue agrégée
/// ([ServiceMultisite#exporterCsvVers(Path, List)]) : l'écriture fichier produit exactement le même
/// CSV que [ServiceMultisite#exporterCsv(List)] (déjà couvert par approval). Les DAO ne sont pas
/// sollicités par l'export — mockés et inutilisés.
@ExtendWith(MockitoExtension.class)
class ServiceMultisiteExportTest {

    @Mock
    private SiteDao siteDao;

    @Mock
    private PointDao pointDao;

    @Mock
    private PassageDao passageDao;

    @Mock
    private ReleveTraitementDao releves;

    @Mock
    private ResultatsIdentificationDao resultats;

    @Mock
    private Horloge horloge;

    @Test
    @DisplayName("exporterCsvVers écrit dans le fichier le même CSV que exporterCsv")
    void exporterCsvVers_ecrit_le_meme_csv(@TempDir Path dossier) throws Exception {
        ServiceMultisite service = new ServiceMultisite(siteDao, pointDao, passageDao, releves, resultats, horloge);
        List<LignePassage> lignes = List.of(
                new LignePassage(
                        1L,
                        "640380",
                        "A1",
                        2026,
                        1,
                        "2026-06-21",
                        StatutWorkflow.DEPOSE,
                        Verdict.OK,
                        EtatAnalyse.A_IMPORTER,
                        "2026-07-14T09:00:00Z"),
                new LignePassage(
                        2L,
                        "640381",
                        "B2",
                        2025,
                        3,
                        "2025-07-02",
                        StatutWorkflow.VERIFIE,
                        null,
                        EtatAnalyse.SANS_OBJET,
                        null));
        Path fichier = dossier.resolve("vue.csv");

        service.exporterCsvVers(fichier, lignes);

        assertThat(fichier).exists();
        assertThat(Files.readString(fichier)).isEqualTo(service.exporterCsv(lignes));
    }
}

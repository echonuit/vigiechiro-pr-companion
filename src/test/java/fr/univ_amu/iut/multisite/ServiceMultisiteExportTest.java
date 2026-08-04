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
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Test de la matérialisation disque de la vue agrégée
/// ([ServiceMultisite#exporterCsvVers(Path, List)]) : l'écriture fichier produit exactement le même
/// CSV que [ServiceMultisite#exporterCsv(List)] (déjà couvert par approval). Les DAO ne sont pas
/// sollicités par l'export : mockés et inutilisés.
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
    private PointCommuneDao communesDao;

    @Mock
    private Horloge horloge;

    @Test
    @DisplayName("Le CSV porte la commune, comme la table depuis #3163")
    void le_csv_porte_la_commune() {
        // L'écran montre la commune depuis #3163 ; le fichier exporté ne la portait pas. Ce test exige
        // la VALEUR, là où l'approbation ne montrerait qu'une colonne vide : la fixture partagée ne
        // résout aucune commune, si bien qu'un en-tête présent n'y prouverait rien.
        ServiceMultisite service = new ServiceMultisite(
                siteDao, pointDao, passageDao, releves, resultats, communesDao, Optional.empty(), horloge);
        LignePassage aix = new LignePassage(
                1L,
                "640380",
                "A1",
                2026,
                1,
                "2026-06-21",
                StatutWorkflow.DEPOSE,
                Verdict.OK,
                EtatAnalyse.A_IMPORTER,
                null,
                null,
                "Aix-en-Provence",
                "Étang");
        LignePassage sansCommune = new LignePassage(
                2L,
                "640381",
                "B2",
                2025,
                3,
                "2025-07-02",
                StatutWorkflow.VERIFIE,
                null,
                EtatAnalyse.SANS_OBJET,
                null,
                null,
                null,
                null);

        String csv = service.exporterCsv(List.of(aix, sansCommune));

        assertThat(csv.lines().findFirst().orElseThrow())
                .as("l'en-tête annonce la commune entre le nom de site et le point")
                .contains("nom_site;commune;point");
        assertThat(csv).contains("640380;Étang;Aix-en-Provence;A1;");
        assertThat(csv)
                .as("une commune non résolue laisse la cellule vide, comme dans la table")
                .contains("640381;;;B2;");
    }

    @Test
    @DisplayName("exporterCsvVers écrit dans le fichier le même CSV que exporterCsv")
    void exporterCsvVers_ecrit_le_meme_csv(@TempDir Path dossier) throws Exception {
        ServiceMultisite service = new ServiceMultisite(
                siteDao, pointDao, passageDao, releves, resultats, communesDao, Optional.empty(), horloge);
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
                        "2026-07-14T09:00:00Z",
                        null,
                        null,
                        null),
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
                        null,
                        null,
                        null,
                        null));
        Path fichier = dossier.resolve("vue.csv");

        service.exporterCsvVers(fichier, lignes);

        assertThat(fichier).exists();
        assertThat(Files.readString(fichier)).isEqualTo(service.exporterCsv(lignes));
    }
}

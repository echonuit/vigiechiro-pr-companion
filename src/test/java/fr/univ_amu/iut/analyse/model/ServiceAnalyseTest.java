package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAnalyseDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// [ServiceAnalyse] avec [ProjectionsAnalyseDao] mocké : le service **fournit** les observations enrichies (le
/// filtrage et l'agrégation vivent désormais dans le ViewModel, #537) et **exporte** l'inventaire en CSV.
@ExtendWith(MockitoExtension.class)
class ServiceAnalyseTest {

    private static final String ID = "u-1";

    @Mock
    private ProjectionsAnalyseDao observationDao;

    @Test
    @DisplayName("observationsAnalyse délègue au DAO la lecture des observations enrichies")
    void observations_analyse_delegue_au_dao() {
        ObservationAnalyse obs = new ObservationAnalyse(
                "Pippip",
                "Pipistrellus",
                "Pipistrelle commune",
                "Chiroptères",
                StatutObservation.VALIDEE,
                1L,
                2026,
                "640380",
                "Étang",
                10L);
        when(observationDao.observationsAnalyse(ID)).thenReturn(List.of(obs));

        assertThat(new ServiceAnalyse(observationDao).observationsAnalyse(ID)).containsExactly(obs);
    }

    @Test
    @DisplayName("observationsDeLEspece délègue au DAO le détail filtré par statut")
    void observations_de_l_espece_delegue_au_dao() {
        ObservationEspece detail = new ObservationEspece(
                1L,
                1L,
                1L,
                1,
                2026,
                "2026-06-20",
                "640380",
                "A1",
                "Étang",
                "Pippip",
                0.9,
                "Pippip",
                0.95,
                StatutObservation.VALIDEE);
        when(observationDao.observationsDeLEspece(ID, "Pippip", StatutObservation.VALIDEE))
                .thenReturn(List.of(detail));

        assertThat(new ServiceAnalyse(observationDao).observationsDeLEspece(ID, "Pippip", StatutObservation.VALIDEE))
                .containsExactly(detail);
    }

    @Test
    @DisplayName("exporterEspeces écrit un CSV avec en-tête et une ligne par espèce")
    void exporter_especes_ecrit_le_csv(@TempDir Path dossier) throws IOException {
        Path cible = dossier.resolve("especes.csv");
        EspeceAgregee espece = new EspeceAgregee(
                "Pippip", "Pipistrellus", "Pipistrelle commune", "Chiroptères", 5, 3, 2, 2, 2024, 2026);

        new ServiceAnalyse(observationDao).exporterEspeces(cible, List.of(espece));

        String contenu = Files.readString(cible);
        assertThat(contenu).contains("code").contains("detections"); // en-tête
        assertThat(contenu)
                .contains("Pippip")
                .contains("Pipistrelle commune")
                .contains("Chiroptères")
                .contains("5");
    }

    @Test
    @DisplayName("exporterCarres écrit un CSV avec en-tête et une ligne par carré")
    void exporter_carres_ecrit_le_csv(@TempDir Path dossier) throws IOException {
        Path cible = dossier.resolve("carres.csv");
        CarreEspeces carre = new CarreEspeces("640380", "Étang", 4, 10, 2025, 2026);

        new ServiceAnalyse(observationDao).exporterCarres(cible, List.of(carre));

        String contenu = Files.readString(cible);
        assertThat(contenu).contains("carre").contains("richesse"); // en-tête
        assertThat(contenu).contains("640380").contains("Étang").contains("4").contains("10");
    }
}

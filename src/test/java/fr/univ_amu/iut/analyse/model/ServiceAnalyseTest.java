package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// [ServiceAnalyse] avec un [ObservationDao] mocké : vérifie que le **filtre de statut** et l'agrégation
/// **côté client** (#537 étape 4) reproduisent le comportement de l'ancienne agrégation SQL — mêmes
/// espèces retenues par statut, même richesse par carré — sans base de données.
@ExtendWith(MockitoExtension.class)
class ServiceAnalyseTest {

    private static final String ID = "u-1";

    @Mock
    private ObservationDao observationDao;

    /// Une observation d'analyse sur le carré 640380, statut donné (le taxon retenu identifie l'espèce).
    private static ObservationAnalyse obs(String taxon, StatutObservation statut) {
        return new ObservationAnalyse(taxon, null, taxon, "Chiroptères", statut, 1L, 2026, "640380", "Étang", 10L);
    }

    private ServiceAnalyse serviceAvecTroisStatuts() {
        when(observationDao.observationsAnalyse(ID))
                .thenReturn(List.of(
                        obs("Pippip", StatutObservation.VALIDEE),
                        obs("Tadten", StatutObservation.CORRIGEE),
                        obs("Nyclei", StatutObservation.NON_TOUCHEE)));
        return new ServiceAnalyse(observationDao);
    }

    @Test
    @DisplayName("inventaire par espèce : le filtre de statut restreint l'inventaire (agrégation client)")
    void inventaire_par_espece_filtre_statut() {
        ServiceAnalyse service = serviceAvecTroisStatuts();

        assertThat(service.inventaireParEspece(ID, null))
                .extracting(EspeceAgregee::code)
                .containsExactlyInAnyOrder("Pippip", "Tadten", "Nyclei");
        assertThat(service.inventaireParEspece(ID, StatutObservation.VALIDEE))
                .extracting(EspeceAgregee::code)
                .containsExactly("Pippip");
        assertThat(service.inventaireParEspece(ID, StatutObservation.CORRIGEE))
                .extracting(EspeceAgregee::code)
                .containsExactly("Tadten");
        assertThat(service.inventaireParEspece(ID, StatutObservation.NON_TOUCHEE))
                .extracting(EspeceAgregee::code)
                .containsExactly("Nyclei");
    }

    @Test
    @DisplayName("inventaire par carré : richesse = espèces distinctes, restreinte par le statut")
    void inventaire_par_carre_filtre_statut() {
        ServiceAnalyse service = serviceAvecTroisStatuts();

        assertThat(service.inventaireParCarre(ID, null)).singleElement().satisfies(carre -> {
            assertThat(carre.numeroCarre()).isEqualTo("640380");
            assertThat(carre.richesse()).as("3 espèces distinctes").isEqualTo(3);
            assertThat(carre.nbObservations()).isEqualTo(3);
        });
        assertThat(service.inventaireParCarre(ID, StatutObservation.VALIDEE))
                .singleElement()
                .satisfies(carre -> {
                    assertThat(carre.richesse()).isEqualTo(1);
                    assertThat(carre.nbObservations()).isEqualTo(1);
                });
    }
}

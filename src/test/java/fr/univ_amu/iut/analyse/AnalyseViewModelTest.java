package fr.univ_amu.iut.analyse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.analyse.viewmodel.Regroupement;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [AnalyseViewModel] avec [ServiceAnalyse] mocké : chargement selon le regroupement
/// (espèce/carré), passage du filtre de statut au service, et résumé. Pas de base ni de JavaFX UI.
@ExtendWith(MockitoExtension.class)
class AnalyseViewModelTest {

    private static final String ID = "u-1";

    @Mock
    private ServiceAnalyse service;

    private static EspeceAgregee espece(String code, int nbObs) {
        return new EspeceAgregee(code, null, "Pipistrelle commune", "Pipistrellus", nbObs, 1, 1, 1, 2026, 2026);
    }

    private static CarreEspeces carre(String numero, int richesse, int nbObs) {
        return new CarreEspeces(numero, "Étang", richesse, nbObs, 2025, 2026);
    }

    @Test
    @DisplayName("Par espèce (défaut) : rafraichir charge l'inventaire par espèce et résume")
    void par_espece_charge_et_resume() {
        when(service.inventaireParEspece(eq(ID), isNull()))
                .thenReturn(List.of(espece("Pippip", 5), espece("Nyclei", 3)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);

        vm.rafraichir();

        assertThat(vm.especes()).hasSize(2);
        assertThat(vm.carres()).isEmpty();
        assertThat(vm.resumeProperty().get()).contains("2 espèces").contains("8 détections");
    }

    @Test
    @DisplayName("Basculer Par carré charge l'inventaire par carré (et vide la liste espèces)")
    void par_carre_charge_la_richesse() {
        when(service.inventaireParCarre(eq(ID), isNull())).thenReturn(List.of(carre("640380", 4, 10)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);

        vm.regroupementProperty().set(Regroupement.PAR_CARRE);

        assertThat(vm.carres())
                .singleElement()
                .satisfies(c -> assertThat(c.richesse()).isEqualTo(4));
        assertThat(vm.especes()).isEmpty();
        assertThat(vm.resumeProperty().get()).contains("1 carré").contains("10 détections");
    }

    @Test
    @DisplayName("Changer le filtre de statut ré-interroge le service avec ce statut")
    void filtre_statut_re_interroge() {
        when(service.inventaireParEspece(eq(ID), eq(StatutObservation.VALIDEE)))
                .thenReturn(List.of(espece("Pippip", 2)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);

        vm.filtreStatutProperty().set(StatutObservation.VALIDEE);

        verify(service).inventaireParEspece(ID, StatutObservation.VALIDEE);
        assertThat(vm.especes()).hasSize(1);
    }
}

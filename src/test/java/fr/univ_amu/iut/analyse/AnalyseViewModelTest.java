package fr.univ_amu.iut.analyse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.analyse.viewmodel.Regroupement;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
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

    private static ObservationEspece observation(long idPassage) {
        return new ObservationEspece(
                idPassage,
                idPassage,
                idPassage,
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

    @Test
    @DisplayName("Le filtre texte s'applique en mémoire (sans nouvelle requête), insensible aux accents")
    void filtre_texte_en_memoire() {
        when(service.inventaireParEspece(eq(ID), isNull()))
                .thenReturn(List.of(
                        espece("Pippip", 5), // Pipistrelle commune
                        new EspeceAgregee(
                                "Nyclei",
                                "Nyctalus leisleri",
                                "Noctule de Leisler",
                                "Nyctalus",
                                3,
                                1,
                                1,
                                1,
                                2026,
                                2026)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);
        vm.rafraichir();
        assertThat(vm.especes()).hasSize(2);

        vm.filtreTexteProperty().set("noctule");

        assertThat(vm.especes()).extracting(EspeceAgregee::code).containsExactly("Nyclei");
        verify(service, times(1)).inventaireParEspece(eq(ID), isNull()); // pas de re-requête au filtre texte
    }

    @Test
    @DisplayName("Exporter délègue au service l'écriture CSV de l'inventaire affiché et restitue un bilan")
    void exporter_delegue_au_service(@TempDir Path dossier) {
        when(service.inventaireParEspece(eq(ID), isNull())).thenReturn(List.of(espece("Pippip", 5)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);
        vm.rafraichir();
        Path cible = dossier.resolve("inventaire.csv");

        boolean ok = vm.exporter(cible);

        assertThat(ok).isTrue();
        verify(service).exporterEspeces(eq(cible), anyList());
        assertThat(vm.messageProperty().get()).contains("exporté");
    }

    @Test
    @DisplayName("Sélectionner une espèce charge ses observations (détail) et titre le panneau")
    void selectionner_espece_charge_le_detail() {
        when(service.inventaireParEspece(eq(ID), isNull())).thenReturn(List.of(espece("Pippip", 2)));
        when(service.observationsDeLEspece(eq(ID), eq("Pippip"), isNull()))
                .thenReturn(List.of(observation(10L), observation(11L)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);
        vm.rafraichir();

        vm.selectionnerEspece(espece("Pippip", 2));

        assertThat(vm.observations()).extracting(ObservationEspece::idPassage).containsExactly(10L, 11L);
        assertThat(vm.detailTitreProperty().get())
                .contains("Pipistrelle commune")
                .contains("2 observations");
    }

    @Test
    @DisplayName("Sélectionner null (ou Par carré) vide le panneau détail")
    void selectionner_null_vide_le_detail() {
        when(service.inventaireParEspece(eq(ID), isNull())).thenReturn(List.of(espece("Pippip", 2)));
        when(service.observationsDeLEspece(eq(ID), eq("Pippip"), isNull())).thenReturn(List.of(observation(10L)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);
        vm.rafraichir();
        vm.selectionnerEspece(espece("Pippip", 2));
        assertThat(vm.observations()).isNotEmpty();

        vm.selectionnerEspece(null);

        assertThat(vm.observations()).isEmpty();
        assertThat(vm.detailTitreProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("En regroupement Par carré, sélectionner une espèce ne charge aucun détail")
    void selectionner_espece_sans_effet_en_par_carre() {
        when(service.inventaireParCarre(eq(ID), isNull())).thenReturn(List.of(carre("640380", 4, 10)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);
        vm.regroupementProperty().set(Regroupement.PAR_CARRE);

        vm.selectionnerEspece(espece("Pippip", 2));

        assertThat(vm.observations()).isEmpty();
        assertThat(vm.detailTitreProperty().get()).isEmpty();
        verify(service, never()).observationsDeLEspece(any(), any(), any());
    }
}

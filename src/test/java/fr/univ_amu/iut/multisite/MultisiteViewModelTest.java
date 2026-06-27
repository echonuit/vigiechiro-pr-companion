package fr.univ_amu.iut.multisite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.SavedView;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires du [MultisiteViewModel] : chargement/résumé du tableau, ré-interrogation du
/// service à chaque changement de filtre ou de tri, réinitialisation, et délégation de l'export.
/// Service mocké, pas de base de données ni de JavaFX UI.
@ExtendWith(MockitoExtension.class)
class MultisiteViewModelTest {

    private static final String ID = "u-1";

    @Mock
    private ServiceMultisite service;

    @org.junit.jupiter.api.BeforeEach
    void stubCarteParDefaut() {
        // rafraichir() agrège aussi pour la carte (#152) ; stub lenient pour les tests qui n'y touchent pas.
        lenient().when(service.agregerPourCarte(any())).thenReturn(List.of());
    }

    private static LignePassage ligne(String carre, String point, int annee, int numero) {
        return new LignePassage(
                (long) numero, carre, point, annee, numero, "2026-06-2" + numero, StatutWorkflow.DEPOSE, Verdict.OK);
    }

    @Test
    @DisplayName("rafraichir charge le tableau et résume le nombre de passages")
    void rafraichir_charge_et_resume() {
        when(service.listerPassages(eq(ID), any(), any()))
                .thenReturn(List.of(ligne("640380", "A1", 2026, 1), ligne("640381", "B2", 2026, 2)));
        MultisiteViewModel vm = new MultisiteViewModel(service, ID);

        vm.rafraichir();

        assertThat(vm.lignes()).hasSize(2);
        assertThat(vm.nonVideProperty().get()).isTrue();
        assertThat(vm.resumeProperty().get()).contains("2 passage");
    }

    @Test
    @DisplayName("#152 : rafraichir alimente aussi l'agrégat des carrés pour la carte")
    void rafraichir_alimente_la_carte() {
        when(service.listerPassages(eq(ID), any(), any())).thenReturn(List.of());
        when(service.agregerPourCarte(ID)).thenReturn(List.of(new CarreAgrege("640380", "Étang", List.of(), 0)));
        MultisiteViewModel vm = new MultisiteViewModel(service, ID);

        vm.rafraichir();

        assertThat(vm.carresCarte()).extracting(CarreAgrege::numeroCarre).containsExactly("640380");
    }

    @Test
    @DisplayName("Changer un filtre ré-interroge le service avec les critères courants")
    void filtre_re_interroge_le_service() {
        when(service.listerPassages(eq(ID), any(), any())).thenReturn(List.of());
        MultisiteViewModel vm = new MultisiteViewModel(service, ID);

        vm.filtreStatutProperty().set(StatutWorkflow.DEPOSE);

        ArgumentCaptor<FiltresMultisite> capteur = ArgumentCaptor.forClass(FiltresMultisite.class);
        verify(service, atLeastOnce()).listerPassages(eq(ID), capteur.capture(), any());
        assertThat(capteur.getValue().statut()).isEqualTo(StatutWorkflow.DEPOSE);
    }

    @Test
    @DisplayName("Changer le tri ré-interroge le service avec le critère de tri choisi")
    void tri_re_interroge_le_service() {
        when(service.listerPassages(eq(ID), any(), any())).thenReturn(List.of());
        MultisiteViewModel vm = new MultisiteViewModel(service, ID);

        vm.triProperty().set(TriMultisite.PAR_ANNEE);

        verify(service).listerPassages(eq(ID), any(), eq(TriMultisite.PAR_ANNEE));
    }

    @Test
    @DisplayName("Un numéro de carré vide ou en blanc n'applique aucun filtre (null)")
    void numero_carre_blanc_pas_de_filtre() {
        when(service.listerPassages(eq(ID), any(), any())).thenReturn(List.of());
        MultisiteViewModel vm = new MultisiteViewModel(service, ID);

        vm.filtreNumeroCarreProperty().set("640380");
        vm.filtreNumeroCarreProperty().set("   "); // blanc → aucun filtre

        ArgumentCaptor<FiltresMultisite> capteur = ArgumentCaptor.forClass(FiltresMultisite.class);
        verify(service, atLeastOnce()).listerPassages(eq(ID), capteur.capture(), any());
        assertThat(capteur.getValue().numeroCarre()).isNull();
    }

    @Test
    @DisplayName("reinitialiserFiltres remet tous les critères à zéro")
    void reinitialiser_remet_a_zero() {
        when(service.listerPassages(eq(ID), any(), any())).thenReturn(List.of());
        MultisiteViewModel vm = new MultisiteViewModel(service, ID);
        vm.filtreStatutProperty().set(StatutWorkflow.DEPOSE);
        vm.filtreNumeroCarreProperty().set("640380");

        vm.reinitialiserFiltres();

        assertThat(vm.filtreStatutProperty().get()).isNull();
        assertThat(vm.filtreNumeroCarreProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("exporter délègue l'écriture au service et restitue un bilan")
    void exporter_delegue_au_service() {
        when(service.listerPassages(eq(ID), any(), any())).thenReturn(List.of(ligne("640380", "A1", 2026, 1)));
        MultisiteViewModel vm = new MultisiteViewModel(service, ID);
        vm.rafraichir();

        boolean ok = vm.exporter(Path.of("/tmp/vue-multisite.csv"));

        assertThat(ok).isTrue();
        verify(service).exporterCsvVers(eq(Path.of("/tmp/vue-multisite.csv")), anyList());
        assertThat(vm.messageProperty().get()).contains("exporté");
    }

    @Test
    @DisplayName("enregistrerVue enregistre la combinaison courante et recharge la liste des vues")
    void enregistrer_vue() {
        when(service.listerPassages(eq(ID), any(), any())).thenReturn(List.of());
        when(service.listerVues()).thenReturn(List.of(new SavedView(1L, "Déposés 2026", "{}")));
        MultisiteViewModel vm = new MultisiteViewModel(service, ID);
        vm.filtreStatutProperty().set(StatutWorkflow.DEPOSE);

        boolean ok = vm.enregistrerVue("Déposés 2026");

        assertThat(ok).isTrue();
        ArgumentCaptor<FiltresMultisite> capteur = ArgumentCaptor.forClass(FiltresMultisite.class);
        verify(service).enregistrerVue(eq("Déposés 2026"), capteur.capture());
        assertThat(capteur.getValue().statut()).isEqualTo(StatutWorkflow.DEPOSE);
        assertThat(vm.vues()).hasSize(1);
    }

    @Test
    @DisplayName("enregistrerVue refuse un nom vide")
    void enregistrer_vue_refuse_nom_vide() {
        MultisiteViewModel vm = new MultisiteViewModel(service, ID);

        assertThat(vm.enregistrerVue("   ")).isFalse();
        assertThat(vm.messageProperty().get()).contains("nom");
    }

    @Test
    @DisplayName("appliquerVue rejoue les filtres de la vue et ne recharge le tableau qu'une fois")
    void appliquer_vue() {
        when(service.listerPassages(eq(ID), any(), any())).thenReturn(List.of());
        when(service.chargerVue(5L)).thenReturn(new FiltresMultisite("640380", StatutWorkflow.VERIFIE, null, 2026));
        MultisiteViewModel vm = new MultisiteViewModel(service, ID);

        boolean ok = vm.appliquerVue(new SavedView(5L, "Ma vue", "{}"));

        assertThat(ok).isTrue();
        assertThat(vm.filtreNumeroCarreProperty().get()).isEqualTo("640380");
        assertThat(vm.filtreStatutProperty().get()).isEqualTo(StatutWorkflow.VERIFIE);
        assertThat(vm.filtreAnneeProperty().get()).isEqualTo(2026);
        // Les 4 critères sont posés sous garde → un seul rafraîchissement (un seul appel service).
        verify(service, times(1)).listerPassages(eq(ID), any(), any());
    }

    @Test
    @DisplayName("mettreAJourVue met à jour la vue avec la combinaison courante")
    void mettre_a_jour_vue() {
        when(service.listerVues()).thenReturn(List.of());
        MultisiteViewModel vm = new MultisiteViewModel(service, ID);

        boolean ok = vm.mettreAJourVue(new SavedView(8L, "Ancien", "{}"), "Nouveau");

        assertThat(ok).isTrue();
        verify(service).mettreAJourVue(eq(8L), eq("Nouveau"), any(FiltresMultisite.class));
    }

    @Test
    @DisplayName("supprimerVue supprime la vue et recharge la liste")
    void supprimer_vue() {
        when(service.listerVues()).thenReturn(List.of());
        MultisiteViewModel vm = new MultisiteViewModel(service, ID);

        boolean ok = vm.supprimerVue(new SavedView(3L, "X", "{}"));

        assertThat(ok).isTrue();
        verify(service).supprimerVue(3L);
    }
}

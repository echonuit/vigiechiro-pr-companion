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
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
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

/// Tests unitaires de [AnalyseViewModel] avec [ServiceAnalyse] mocké : le VM charge les observations
/// **brutes** une fois, puis les **filtre** via le socle partagé ([AnalyseViewModel#filtres()], piloté par
/// la barre à puces de la vue depuis #537 étape 6) et **agrège** côté client. Le détail et la source audio
/// reçoivent le **statut courant en paramètre** (lu par la vue sur la barre). Pas de base ni de JavaFX UI.
@ExtendWith(MockitoExtension.class)
class AnalyseViewModelTest {

    private static final String ID = "u-1";
    private static final String CARRE = "640380";

    @Mock
    private ServiceAnalyse service;

    /// Observation enrichie de test : carré/point/passage fixes (les compteurs distincts sont testés dans
    /// AgregationAnalyseTest) ; seuls taxon, vernaculaire, groupe, statut et carré varient selon le besoin.
    private static ObservationAnalyse obs(
            String taxon, String vern, String groupe, StatutObservation statut, String carre) {
        return new ObservationAnalyse(taxon, taxon + " (latin)", vern, groupe, statut, 1L, 2026, carre, "Étang", 10L);
    }

    private static ObservationAnalyse chiro(String taxon, String vern, StatutObservation statut) {
        return obs(taxon, vern, "Chiroptères", statut, CARRE);
    }

    private static EspeceAgregee espece(String code) {
        return new EspeceAgregee(code, null, "Pipistrelle commune", "Chiroptères", 1, 1, 1, 1, 2026, 2026);
    }

    private static ObservationEspece observation(long idPassage, String carre) {
        return new ObservationEspece(
                idPassage,
                idPassage,
                idPassage,
                1,
                2026,
                "2026-06-20",
                carre,
                "A1",
                "Étang",
                "Pippip",
                0.9,
                "Pippip",
                0.95,
                StatutObservation.VALIDEE);
    }

    @Test
    @DisplayName("#audio : sourceAudioEspece porte l'espèce sélectionnée ET le statut courant fourni")
    void source_audio_espece_avec_statut_courant() {
        when(service.observationsDeLEspece(eq(ID), eq("Pippip"), any())).thenReturn(List.of(observation(42L, CARRE)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);

        vm.selectionnerEspece(espece("Pippip"), StatutObservation.VALIDEE);

        assertThat(vm.sourceAudioEspece(StatutObservation.VALIDEE))
                .isInstanceOfSatisfying(SourceObservations.ParEspece.class, espece -> {
                    assertThat(espece.idUtilisateur()).isEqualTo(ID);
                    assertThat(espece.codeEspece()).isEqualTo("Pippip");
                    assertThat(espece.statut()).isEqualTo("VALIDEE");
                    assertThat(espece.libelle()).isEqualTo("Pipistrelle commune");
                });
    }

    @Test
    @DisplayName(
            "#1208 : chargerObservations lit sans muter l'état ; appliquer agrège ; signalerErreur route le message")
    void charger_appliquer_signaler_separes() {
        when(service.observationsAnalyse(ID))
                .thenReturn(List.of(chiro("Pippip", "Pipistrelle commune", StatutObservation.VALIDEE)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);

        var chargees = vm.chargerObservations();
        assertThat(chargees).hasSize(1);
        assertThat(vm.especes())
                .as("chargerObservations ne mute pas l'état observable")
                .isEmpty();

        vm.appliquer(chargees);
        assertThat(vm.especes()).extracting(EspeceAgregee::code).containsExactly("Pippip");

        vm.signalerErreur(new IllegalStateException("base indisponible"));
        assertThat(vm.messageProperty().get()).isEqualTo("base indisponible");
        vm.signalerErreur(new IllegalStateException());
        assertThat(vm.messageProperty().get()).contains("impossible");
    }

    @Test
    @DisplayName("Par espèce (défaut) : rafraichir agrège l'inventaire par espèce et résume")
    void par_espece_agrege_et_resume() {
        when(service.observationsAnalyse(ID))
                .thenReturn(List.of(
                        chiro("Pippip", "Pipistrelle commune", StatutObservation.VALIDEE),
                        chiro("Pippip", "Pipistrelle commune", StatutObservation.VALIDEE),
                        chiro("Nyclei", "Noctule de Leisler", StatutObservation.VALIDEE)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);

        vm.rafraichir();

        assertThat(vm.especes()).extracting(EspeceAgregee::code).containsExactly("Pippip", "Nyclei");
        assertThat(vm.carres()).isEmpty();
        assertThat(vm.resumeProperty().get()).contains("2 espèces").contains("3 détections");
    }

    @Test
    @DisplayName("Basculer Par carré agrège par carré (richesse = espèces distinctes) et vide la liste espèces")
    void par_carre_agrege_la_richesse() {
        when(service.observationsAnalyse(ID))
                .thenReturn(List.of(
                        chiro("Pippip", "Pipistrelle commune", StatutObservation.VALIDEE),
                        chiro("Nyclei", "Noctule de Leisler", StatutObservation.VALIDEE),
                        chiro("Tadten", "Molosse de Cestoni", StatutObservation.VALIDEE)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);
        vm.rafraichir();

        vm.regroupementProperty().set(Regroupement.PAR_CARRE);

        assertThat(vm.carres()).singleElement().satisfies(carre -> {
            assertThat(carre.numeroCarre()).isEqualTo(CARRE);
            assertThat(carre.richesse()).isEqualTo(3);
        });
        assertThat(vm.especes()).isEmpty();
        assertThat(vm.resumeProperty().get()).contains("1 carré").contains("3 détections");
    }

    @Test
    @DisplayName("#537 : un prédicat de statut posé sur le socle filtre côté client, sans ré-interroger le service")
    void filtre_statut_client_side() {
        when(service.observationsAnalyse(ID))
                .thenReturn(List.of(
                        chiro("Pippip", "Pipistrelle commune", StatutObservation.VALIDEE),
                        chiro("Nyclei", "Noctule de Leisler", StatutObservation.NON_TOUCHEE),
                        chiro("Tadten", "Molosse de Cestoni", StatutObservation.CORRIGEE)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);
        vm.rafraichir();
        assertThat(vm.especes()).hasSize(3);

        vm.filtres().definir("statut", o -> o.statut() == StatutObservation.VALIDEE);

        assertThat(vm.especes()).extracting(EspeceAgregee::code).containsExactly("Pippip");
        verify(service, times(1)).observationsAnalyse(ID); // pas de re-requête au changement de filtre
    }

    @Test
    @DisplayName("#518 : un prédicat de taxon parent (groupe) filtre côté client ; groupes présents listés")
    void filtre_taxon_parent_client_side() {
        when(service.observationsAnalyse(ID))
                .thenReturn(List.of(
                        chiro("Pippip", "Pipistrelle commune", StatutObservation.VALIDEE),
                        obs("Turmer", "Merle noir", "Oiseaux", StatutObservation.VALIDEE, CARRE)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);
        vm.rafraichir();
        assertThat(vm.especes()).hasSize(2);
        assertThat(vm.groupesDisponibles()).containsExactly("Chiroptères", "Oiseaux");

        vm.filtres().definir("groupe", o -> "Chiroptères".equals(o.groupe()));

        assertThat(vm.especes()).extracting(EspeceAgregee::code).containsExactly("Pippip");
        verify(service, times(1)).observationsAnalyse(ID);
    }

    @Test
    @DisplayName("Un prédicat texte posé sur le socle filtre côté client, sans nouvelle requête")
    void filtre_texte_client_side() {
        when(service.observationsAnalyse(ID))
                .thenReturn(List.of(
                        chiro("Pippip", "Pipistrelle commune", StatutObservation.VALIDEE),
                        chiro("Nyclei", "Noctule de Leisler", StatutObservation.VALIDEE)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);
        vm.rafraichir();
        assertThat(vm.especes()).hasSize(2);

        vm.filtres()
                .definir(
                        "texte",
                        o -> o.nomVernaculaireFr() != null
                                && o.nomVernaculaireFr().contains("Noctule"));

        assertThat(vm.especes()).extracting(EspeceAgregee::code).containsExactly("Nyclei");
        verify(service, times(1)).observationsAnalyse(ID);
    }

    @Test
    @DisplayName("Exporter délègue au service l'écriture CSV de l'inventaire affiché et restitue un bilan")
    void exporter_delegue_au_service(@TempDir Path dossier) {
        when(service.observationsAnalyse(ID))
                .thenReturn(List.of(chiro("Pippip", "Pipistrelle commune", StatutObservation.VALIDEE)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);
        vm.rafraichir();
        Path cible = dossier.resolve("inventaire.csv");

        boolean ok = vm.exporter(cible);

        assertThat(ok).isTrue();
        verify(service).exporterEspeces(eq(cible), anyList());
        assertThat(vm.messageProperty().get()).contains("exporté");
    }

    @Test
    @DisplayName("Sélectionner une espèce charge ses observations (détail, statut courant) et titre le panneau")
    void selectionner_espece_charge_le_detail() {
        when(service.observationsAnalyse(ID))
                .thenReturn(List.of(chiro("Pippip", "Pipistrelle commune", StatutObservation.VALIDEE)));
        when(service.observationsDeLEspece(eq(ID), eq("Pippip"), isNull()))
                .thenReturn(List.of(observation(10L, CARRE), observation(11L, CARRE)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);
        vm.rafraichir();

        vm.selectionnerEspece(espece("Pippip"), null);

        assertThat(vm.observations()).extracting(ObservationEspece::idPassage).containsExactly(10L, 11L);
        assertThat(vm.detailTitreProperty().get())
                .contains("Pipistrelle commune")
                .contains("2 observations");
    }

    @Test
    @DisplayName("Sélectionner null vide le panneau détail")
    void selectionner_null_vide_le_detail() {
        when(service.observationsAnalyse(ID))
                .thenReturn(List.of(chiro("Pippip", "Pipistrelle commune", StatutObservation.VALIDEE)));
        when(service.observationsDeLEspece(eq(ID), eq("Pippip"), isNull()))
                .thenReturn(List.of(observation(10L, CARRE)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);
        vm.rafraichir();
        vm.selectionnerEspece(espece("Pippip"), null);
        assertThat(vm.observations()).isNotEmpty();

        vm.selectionnerEspece(null, null);

        assertThat(vm.observations()).isEmpty();
        assertThat(vm.detailTitreProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("Sélectionner une espèce expose ses carrés distincts (carte) ; null les vide")
    void selectionner_espece_expose_les_carres_distincts() {
        when(service.observationsAnalyse(ID))
                .thenReturn(List.of(chiro("Pippip", "Pipistrelle commune", StatutObservation.VALIDEE)));
        when(service.observationsDeLEspece(eq(ID), eq("Pippip"), isNull()))
                .thenReturn(
                        List.of(observation(10L, "640380"), observation(11L, "640380"), observation(12L, "640381")));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);
        vm.rafraichir();

        vm.selectionnerEspece(espece("Pippip"), null);
        assertThat(vm.carresEspeceSelectionnee()).containsExactly("640380", "640381");

        vm.selectionnerEspece(null, null);
        assertThat(vm.carresEspeceSelectionnee()).isEmpty();
    }

    @Test
    @DisplayName("En regroupement Par carré, sélectionner une espèce ne charge aucun détail")
    void selectionner_espece_sans_effet_en_par_carre() {
        when(service.observationsAnalyse(ID))
                .thenReturn(List.of(chiro("Pippip", "Pipistrelle commune", StatutObservation.VALIDEE)));
        AnalyseViewModel vm = new AnalyseViewModel(service, ID);
        vm.rafraichir();
        vm.regroupementProperty().set(Regroupement.PAR_CARRE);

        vm.selectionnerEspece(espece("Pippip"), null);

        assertThat(vm.observations()).isEmpty();
        assertThat(vm.detailTitreProperty().get()).isEmpty();
        verify(service, never()).observationsDeLEspece(any(), any(), any());
    }
}

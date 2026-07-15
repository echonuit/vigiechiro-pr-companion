package fr.univ_amu.iut.multisite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires du [MultisiteViewModel]. Depuis #537, les passages sont chargés **une fois** puis
/// filtrés/triés **en mémoire** via le socle partagé [MultisiteViewModel#filtres()] (piloté par la barre à
/// puces de la vue depuis l'étape 6b) : ces tests vérifient le chargement/résumé du tableau, le **filtrage
/// client-side sans ré-interroger le service**, le tri, l'export et l'édition des positions. Les **vues
/// mémorisées** ne sont plus ici (composant partagé `GestionnaireVues`). Service mocké, pas de base ni de
/// JavaFX UI.
@ExtendWith(MockitoExtension.class)
class MultisiteViewModelTest {

    private static final String ID = "u-1";

    @Mock
    private ServiceMultisite service;

    @Mock
    private ServiceSites serviceSites;

    @Test
    @DisplayName("#1209 : charger lit sans muter ; appliquer publie tableau + carte ; signalerErreur route le message")
    void charger_appliquer_signaler_separes() {
        when(service.listerPassages(ID)).thenReturn(List.of(ligne("640380", "A1", 2026, 1)));
        when(service.agregerPourCarte(ID)).thenReturn(List.of());
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.empty(), ID);

        var donnees = vm.charger();
        assertThat(donnees.passages()).hasSize(1);
        assertThat(vm.lignes()).as("charger ne mute pas l'état observable").isEmpty();

        vm.appliquer(donnees);
        assertThat(vm.lignes()).hasSize(1);

        vm.signalerErreur(new IllegalStateException("base indisponible"));
        assertThat(vm.messageProperty().get()).isEqualTo("base indisponible");
        vm.signalerErreur(new IllegalStateException());
        assertThat(vm.messageProperty().get()).contains("impossible");
    }

    // --- Relevé groupé de l'état des analyses (#1338) ---

    @Test
    @DisplayName("#1338 : le relevé groupé n'est disponible que connecté à VigieChiro")
    void releve_disponible_seulement_connecte() {
        assertThat(new MultisiteViewModel(service, serviceSites, Optional.empty(), ID).releveAnalysesDisponible())
                .isFalse();
        assertThat(new MultisiteViewModel(service, serviceSites, Optional.of(mock(SuiviTraitement.class)), ID)
                        .releveAnalysesDisponible())
                .isTrue();
    }

    @Test
    @DisplayName("#1338 : relever n'interroge QUE les nuits déposées, et rend compte du rafraîchissement")
    void relever_les_nuits_deposees_et_rend_compte() {
        SuiviTraitement suivi = mock(SuiviTraitement.class);
        when(suivi.releverTout(anyList())).thenReturn(new SuiviTraitement.BilanReleveGroupe(2, 0));
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.of(suivi), ID);
        when(service.listerPassages(ID))
                .thenReturn(List.of(
                        ligne("640380", "A1", 2026, 1, StatutWorkflow.DEPOSE),
                        ligne("640381", "B2", 2026, 2, StatutWorkflow.VERIFIE),
                        ligne("640382", "C3", 2026, 3, StatutWorkflow.DEPOSE)));
        vm.rafraichir();

        List<Long> deposees = vm.nuitsDeposees();
        assertThat(deposees).containsExactly(1L, 3L);

        String compteRendu = vm.releverAnalyses(deposees);

        verify(suivi).releverTout(List.of(1L, 3L));
        assertThat(compteRendu).contains("2 nuit(s)");
    }

    @Test
    @DisplayName("#1338 : sans nuit déposée, le relevé le DIT plutôt que d'interroger le serveur pour rien")
    void relever_sans_nuit_deposee() {
        SuiviTraitement suivi = mock(SuiviTraitement.class);
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.of(suivi), ID);

        assertThat(vm.releverAnalyses(List.of())).contains("Aucune nuit déposée");
        verify(suivi, never()).releverTout(anyList());
    }

    @Test
    @DisplayName("#1338 : un échec réseau ne ment pas sur la fraîcheur — il compte les injoignables")
    void relever_avec_echecs_le_dit() {
        SuiviTraitement suivi = mock(SuiviTraitement.class);
        when(suivi.releverTout(anyList())).thenReturn(new SuiviTraitement.BilanReleveGroupe(1, 2));
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.of(suivi), ID);

        assertThat(vm.releverAnalyses(List.of(1L, 2L, 3L)))
                .contains("1 nuit(s) sur 3")
                .contains("2 injoignable(s)")
                .contains("dernier état connu");
    }

    @Test
    @DisplayName("#1338 : relever puis charger publie le compte rendu APRÈS le rechargement (non effacé)")
    void relever_puis_charger_conserve_le_message() {
        SuiviTraitement suivi = mock(SuiviTraitement.class);
        when(suivi.releverTout(anyList())).thenReturn(new SuiviTraitement.BilanReleveGroupe(1, 0));
        when(service.listerPassages(ID)).thenReturn(List.of(ligne("640380", "A1", 2026, 1, StatutWorkflow.DEPOSE)));
        when(service.agregerPourCarte(ID)).thenReturn(List.of());
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.of(suivi), ID);

        MultisiteViewModel.ResultatReleve resultat = vm.releverPuisCharger(List.of(1L));
        vm.appliquerReleve(resultat);

        assertThat(vm.messageProperty().get())
                .as("le compte rendu doit survivre au rechargement (appliquer efface le message avant)")
                .contains("relevé pour 1 nuit(s)");
    }

    private static LignePassage ligne(String carre, String point, int annee, int numero) {
        return ligne(carre, point, annee, numero, StatutWorkflow.DEPOSE);
    }

    private static LignePassage ligne(String carre, String point, int annee, int numero, StatutWorkflow statut) {
        return new LignePassage(
                (long) numero,
                carre,
                point,
                annee,
                numero,
                "2026-06-2" + numero,
                statut,
                Verdict.OK,
                EtatAnalyse.SANS_OBJET,
                null);
    }

    @Test
    @DisplayName("rafraichir charge le tableau et résume le nombre de passages")
    void rafraichir_charge_et_resume() {
        when(service.listerPassages(ID))
                .thenReturn(List.of(ligne("640380", "A1", 2026, 1), ligne("640381", "B2", 2026, 2)));
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.empty(), ID);

        vm.rafraichir();

        assertThat(vm.lignes()).hasSize(2);
        assertThat(vm.nonVideProperty().get()).isTrue();
        assertThat(vm.resumeProperty().get()).contains("2 passage");
    }

    @Test
    @DisplayName("#152 : rafraichirCarte alimente l'agrégat des carrés (séparé du tableau)")
    void rafraichirCarte_alimente_la_carte() {
        when(service.agregerPourCarte(ID)).thenReturn(List.of(new CarreAgrege("640380", "Étang", List.of(), 0)));
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.empty(), ID);

        vm.rafraichirCarte();

        assertThat(vm.carresCarte()).extracting(CarreAgrege::numeroCarre).containsExactly("640380");
    }

    @Test
    @DisplayName("#152 : filtrer ne recalcule PAS l'agrégat carte (coût évité)")
    void filtrer_ne_touche_pas_la_carte() {
        when(service.listerPassages(ID)).thenReturn(List.of());
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.empty(), ID);

        vm.rafraichir();
        vm.filtres().definir("statut", ligne -> ligne.statut() == StatutWorkflow.DEPOSE); // re-filtre en mémoire

        verify(service, never()).agregerPourCarte(any());
    }

    @Test
    @DisplayName("#537 : un prédicat posé sur le socle filtre EN MÉMOIRE, sans ré-interroger le service")
    void filtre_filtre_en_memoire() {
        when(service.listerPassages(ID))
                .thenReturn(List.of(
                        ligne("640380", "A1", 2026, 1, StatutWorkflow.DEPOSE),
                        ligne("640381", "B2", 2026, 2, StatutWorkflow.VERIFIE)));
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.empty(), ID);
        vm.rafraichir();
        assertThat(vm.lignes()).hasSize(2);

        vm.filtres().definir("statut", ligne -> ligne.statut() == StatutWorkflow.VERIFIE);

        assertThat(vm.lignes()).extracting(LignePassage::statut).containsExactly(StatutWorkflow.VERIFIE);
        verify(service, times(1)).listerPassages(ID); // chargé une fois, pas de ré-requête au filtrage
    }

    @Test
    @DisplayName("#537 : changer le tri ré-ordonne en mémoire, sans ré-interroger le service")
    void tri_re_ordonne_en_memoire() {
        when(service.listerPassages(ID))
                .thenReturn(List.of(ligne("640380", "A1", 2026, 1), ligne("640381", "B2", 2024, 2)));
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.empty(), ID);
        vm.rafraichir();

        vm.triProperty().set(TriMultisite.PAR_ANNEE);

        assertThat(vm.lignes()).extracting(LignePassage::annee).containsExactly(2024, 2026);
        verify(service, times(1)).listerPassages(ID);
    }

    @Test
    @DisplayName("exporter délègue l'écriture au service et restitue un bilan")
    void exporter_delegue_au_service() {
        when(service.listerPassages(ID)).thenReturn(List.of(ligne("640380", "A1", 2026, 1)));
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.empty(), ID);
        vm.rafraichir();

        boolean ok = vm.exporter(Path.of("/tmp/vue-multisite.csv"));

        assertThat(ok).isTrue();
        verify(service).exporterCsvVers(eq(Path.of("/tmp/vue-multisite.csv")), anyList());
        assertThat(vm.messageProperty().get()).contains("exporté");
    }

    @Test
    @DisplayName("#291 : exporter(lignes fournies) écrit EXACTEMENT l'ordre donné (le tri affiché)")
    void exporter_respecte_l_ordre_fourni() {
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.empty(), ID);
        // Ordre « affiché » (p. ex. après un tri par clic d'en-tête) différent de l'ordre interne.
        List<LignePassage> ordreAffiche = List.of(ligne("640381", "B2", 2025, 3), ligne("640380", "A1", 2026, 1));

        boolean ok = vm.exporter(Path.of("/tmp/vue.csv"), ordreAffiche);

        assertThat(ok).isTrue();
        verify(service).exporterCsvVers(eq(Path.of("/tmp/vue.csv")), eq(ordreAffiche));
    }

    // --- Édition des positions (#154) ---

    @Test
    @DisplayName("#154 : un déplacement est mis en attente (rien n'est écrit), le bouton s'active")
    void deplacement_en_attente_n_ecrit_rien() {
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.empty(), ID);

        vm.positionsEnAttente().deplacer(42L, 43.4055, -1.5680);

        assertThat(vm.positionsEnAttente().aDesEnAttente()).isTrue();
        assertThat(vm.positionsEnAttente().modifieesProperty().get()).isTrue();
        verify(serviceSites, never()).deplacerPoint(any(), any(), any());
    }

    @Test
    @DisplayName("#154 : enregistrer persiste chaque déplacement puis recharge la carte et vide la file")
    void enregistrer_positions_persiste_et_recharge() {
        when(service.agregerPourCarte(ID)).thenReturn(List.of());
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.empty(), ID);
        vm.positionsEnAttente().deplacer(42L, 43.4055, -1.5680);
        vm.positionsEnAttente().deplacer(7L, 43.4010, -1.5740);

        int enregistres = vm.positionsEnAttente().enregistrer();

        assertThat(enregistres).isEqualTo(2);
        verify(serviceSites).deplacerPoint(42L, 43.4055, -1.5680);
        verify(serviceSites).deplacerPoint(7L, 43.4010, -1.5740);
        assertThat(vm.positionsEnAttente().aDesEnAttente()).isFalse();
        assertThat(vm.positionsEnAttente().modifieesProperty().get()).isFalse();
        verify(service, atLeastOnce()).agregerPourCarte(ID); // carte rechargée après enregistrement
    }

    @Test
    @DisplayName("#154 : abandonner vide la file sans rien persister et recharge la carte")
    void annuler_positions_n_ecrit_rien() {
        when(service.agregerPourCarte(ID)).thenReturn(List.of());
        MultisiteViewModel vm = new MultisiteViewModel(service, serviceSites, Optional.empty(), ID);
        vm.positionsEnAttente().deplacer(42L, 43.4055, -1.5680);

        vm.positionsEnAttente().annuler();

        assertThat(vm.positionsEnAttente().aDesEnAttente()).isFalse();
        assertThat(vm.positionsEnAttente().modifieesProperty().get()).isFalse();
        verify(serviceSites, never()).deplacerPoint(any(), any(), any());
        verify(service, atLeastOnce()).agregerPourCarte(ID);
    }
}

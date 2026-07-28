package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.CiblePassage;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.ControleCoherence;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.SourceDepot;
import fr.univ_amu.iut.lot.model.StatutControle;
import fr.univ_amu.iut.lot.model.SuiviDepot;
import fr.univ_amu.iut.lot.model.TeleversementGroupe;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// Tests de [TeleversementGroupe] (#2357, lot 3, PR 3/5).
///
/// Deux propriétés valent plus que le chemin nominal, et sont vérifiées ici :
///
/// - **hors connexion, tout est écarté** avec ce motif - c'est l'annonce qui le dira, avant que la
///   moindre participation ne soit créée sur la plateforme ;
/// - **le jeton du lot est relayé au dépôt**, qui le consulte avant chaque unité. C'est la seule
///   action du lot à le faire, parce que son état interrompu est nommé et reprenable.
class TeleversementGroupeTest {

    private static final CiblePassage CIBLE = new CiblePassage(42L, "640380 / A1 / 2026 n°1");

    private ServiceLot service;
    private DepotVigieChiro depot;

    @BeforeEach
    void preparer() {
        service = mock(ServiceLot.class);
        depot = mock(DepotVigieChiro.class);
    }

    private TeleversementGroupe action() {
        return new TeleversementGroupe(service, Optional.of(depot));
    }

    private static EtatLot etat(StatutWorkflow statut, List<ControleCoherence> controles) {
        return new EtatLot(statut, "/tmp/session", 30, 1_000L, controles, null);
    }

    @Test
    @DisplayName("un lot préparé et cohérent est éligible")
    void pret_a_deposer_est_eligible() {
        when(service.consulterLot(42L)).thenReturn(etat(StatutWorkflow.PRET_A_DEPOSER, List.of()));

        assertThat(action().motifNonEligible(CIBLE)).isEmpty();
    }

    @Test
    @DisplayName("un dépôt déjà entamé reste éligible : c'est le propre d'un dépôt reprenable")
    void depot_en_cours_est_eligible() {
        when(service.consulterLot(42L)).thenReturn(etat(StatutWorkflow.DEPOT_EN_COURS, List.of()));

        assertThat(action().motifNonEligible(CIBLE)).isEmpty();
    }

    @Test
    @DisplayName("hors connexion, tout est écarté AVANT qu'une participation ne soit créée")
    void hors_connexion_tout_est_ecarte() {
        TeleversementGroupe horsLigne = new TeleversementGroupe(service, Optional.empty());

        assertThat(horsLigne.motifNonEligible(CIBLE)).contains("hors connexion à Vigie-Chiro");
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("un passage pas encore préparé est écarté")
    void pas_prepare_est_ecarte() {
        when(service.consulterLot(42L)).thenReturn(etat(StatutWorkflow.VERIFIE, List.of()));

        assertThat(action().motifNonEligible(CIBLE)).contains("dépôt pas encore préparé");
    }

    @Test
    @DisplayName("un passage déjà déposé est écarté")
    void deja_depose_est_ecarte() {
        when(service.consulterLot(42L)).thenReturn(etat(StatutWorkflow.DEPOSE, List.of()));

        assertThat(action().motifNonEligible(CIBLE)).contains("déjà déposé");
    }

    @Test
    @DisplayName("un contrôle en échec écarte le passage")
    void incoherent_est_ecarte() {
        when(service.consulterLot(42L))
                .thenReturn(etat(
                        StatutWorkflow.PRET_A_DEPOSER,
                        List.of(new ControleCoherence("Journal absent", StatutControle.ECHEC, null))));

        assertThat(action().motifNonEligible(CIBLE)).contains("contrôle de cohérence en échec");
    }

    @Test
    @DisplayName("un passage sans lot connu est écarté avec le motif du service, pas avec une pile")
    void lot_introuvable_est_ecarte_avec_son_motif() {
        // Chemin non nominal trouvé par PIT : le refus de `consulterLot` remontait sans qu'aucun test ne
        // le suive. Sans cette reprise, le lot entier tomberait sur le premier passage inconnu.
        when(service.consulterLot(42L))
                .thenThrow(new fr.univ_amu.iut.commun.model.RegleMetierException("Passage introuvable : 42"));

        assertThat(action().motifNonEligible(CIBLE)).contains("Passage introuvable : 42");
    }

    @Test
    @DisplayName("le libellé est celui que l'observateur lit dans le suivi et le compte rendu")
    void libelle_lisible() {
        assertThat(action().libelle()).isEqualTo("Téléverser vers Vigie-Chiro");
    }

    @Test
    @DisplayName("le jeton du lot est relayé au dépôt : renoncer arrête entre deux unités")
    void le_jeton_est_relaye() {
        SourceDepot source = mock(SourceDepot.class);
        when(service.sourceDepotParDefaut(42L)).thenReturn(source);
        JetonAnnulation jeton = new JetonAnnulation();

        action().executer(CIBLE, jeton);

        ArgumentCaptor<BooleanSupplier> annule = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(depot).deposer(eq(42L), eq(source), annule.capture(), any(SuiviDepot.class));
        // Ce n'est pas une constante `false` qui a été passée : le signal SUIT le jeton du lot.
        assertThat(annule.getValue().getAsBoolean()).isFalse();
        jeton.annuler();
        assertThat(annule.getValue().getAsBoolean())
                .as("sans ce relais, renoncer obligerait à attendre la fin du téléversement en cours")
                .isTrue();
    }
}

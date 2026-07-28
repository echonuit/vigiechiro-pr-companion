package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.CiblePassage;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.ControleCoherence;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.PreparationGroupee;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.StatutControle;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de [PreparationGroupee] (#2357) : ce qu'elle **écarte** et pourquoi, puisque c'est tout ce
/// qu'elle ajoute à [ServiceLot] - lequel porte les règles et reste mocké ici.
///
/// Le sujet du lot 3 est le chemin **non nominal** : les motifs d'écart sont donc éprouvés un par un,
/// et pas seulement le cas qui passe.
class PreparationGroupeeTest {

    private static final CiblePassage CIBLE = new CiblePassage(42L, "640380 / A1 / 2026 n°1");

    private ServiceLot service;
    private PreparationGroupee action;

    @BeforeEach
    void preparer() {
        service = mock(ServiceLot.class);
        action = new PreparationGroupee(service);
    }

    private static EtatLot etat(StatutWorkflow statut, List<ControleCoherence> controles) {
        return new EtatLot(statut, "/tmp/session", 30, 1_000L, controles, null);
    }

    @Test
    @DisplayName("un passage vérifié et cohérent est éligible")
    void verifie_et_coherent_est_eligible() {
        when(service.consulterLot(42L)).thenReturn(etat(StatutWorkflow.VERIFIE, List.of()));

        assertThat(action.motifNonEligible(CIBLE)).isEmpty();
    }

    @Test
    @DisplayName("un passage déjà déposé est écarté, et le motif le dit en clair")
    void deja_depose_est_ecarte() {
        when(service.consulterLot(42L)).thenReturn(etat(StatutWorkflow.DEPOSE, List.of()));

        assertThat(action.motifNonEligible(CIBLE)).contains("déjà déposé");
    }

    @Test
    @DisplayName("un dépôt déjà préparé est écarté : le relancer ne ferait rien de plus")
    void deja_prepare_est_ecarte() {
        when(service.consulterLot(42L)).thenReturn(etat(StatutWorkflow.PRET_A_DEPOSER, List.of()));

        assertThat(action.motifNonEligible(CIBLE)).contains("dépôt déjà préparé");
    }

    @Test
    @DisplayName("un passage pas encore vérifié est écarté")
    void pas_verifie_est_ecarte() {
        when(service.consulterLot(42L)).thenReturn(etat(StatutWorkflow.TRANSFORME, List.of()));

        assertThat(action.motifNonEligible(CIBLE)).contains("pas encore vérifié");
    }

    @Test
    @DisplayName("un contrôle bloquant écarte le passage, et c'est SON libellé qu'on lit")
    void controle_bloquant_ecarte_avec_son_libelle() {
        when(service.consulterLot(42L))
                .thenReturn(etat(
                        StatutWorkflow.VERIFIE,
                        List.of(
                                new ControleCoherence("Relevé climatique absent", StatutControle.AVERTISSEMENT, null),
                                new ControleCoherence("Journal du capteur absent", StatutControle.ECHEC, null))));

        // L'avertissement ne bloque pas (R20) : c'est le contrôle en ÉCHEC qui doit ressortir, et non
        // le premier de la liste.
        assertThat(action.motifNonEligible(CIBLE)).contains("journal du capteur absent"); // égalité exacte
    }

    @Test
    @DisplayName("un passage introuvable est écarté avec le message du service, pas une exception")
    void introuvable_est_ecarte_pas_leve() {
        when(service.consulterLot(42L)).thenThrow(new RegleMetierException("Passage introuvable : 42"));

        // L'éligibilité est consultée sur TOUTE la sélection avant le moindre traitement : une cible
        // disparue entre-temps ne doit pas faire tomber l'annonce entière.
        // `Optional.contains` teste l'ÉGALITÉ : ici on veut la sous-chaîne, le message venant du service.
        assertThat(action.motifNonEligible(CIBLE))
                .hasValueSatisfying(motif -> assertThat(motif).contains("introuvable"));
    }

    @Test
    @DisplayName("exécuter délègue au service, sans rien réimplémenter")
    void executer_delegue() {
        action.executer(CIBLE, new JetonAnnulation());

        verify(service).preparerLot(42L);
    }

    @Test
    @DisplayName("le libellé est celui du menu et du compte rendu")
    void libelle_lisible() {
        assertThat(action.libelle()).isEqualTo("Préparer le dépôt");
    }
}

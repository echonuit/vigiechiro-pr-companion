package fr.univ_amu.iut.saison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.saison.model.CasePassage;
import fr.univ_amu.iut.saison.model.LigneSaison;
import fr.univ_amu.iut.saison.model.ServiceSoldeSaison;
import fr.univ_amu.iut.saison.model.SoldeSaison;
import fr.univ_amu.iut.saison.viewmodel.SaisonViewModel;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ViewModel de l'écran M-Saison : on lui donne un [ServiceSoldeSaison] simulé qui renvoie un
/// [SoldeSaison] fabriqué, et on vérifie ce qu'il publie (lignes, résumé, signalement, années).
class SaisonViewModelTest {

    private static final String ID = "u-test";

    private static LigneSaison ligne(String carre, String point, CasePassage p1, CasePassage p2, String reste) {
        return new LigneSaison(carre, point, 1L, p1, p2, reste);
    }

    private static CasePassage depose(String date) {
        return new CasePassage(10L, StatutWorkflow.DEPOSE, Verdict.OK, LocalDate.parse(date), false, null);
    }

    @Test
    @DisplayName("chargerCourant publie les lignes, le résumé décompté et l'année")
    void charger_courant_publie() {
        ServiceSoldeSaison service = mock(ServiceSoldeSaison.class);
        SoldeSaison solde = new SoldeSaison(
                2026,
                LocalDate.of(2026, 7, 20),
                List.of(
                        ligne("640001", "A1", depose("2026-06-20"), depose("2026-08-20"), ""),
                        ligne("640002", "B1", depose("2026-06-22"), CasePassage.absente(), "Poser")));
        when(service.soldeCourant(ID)).thenReturn(solde);

        SaisonViewModel vm = new SaisonViewModel(service, ID);
        vm.chargerCourant();

        assertThat(vm.lignes()).extracting(LigneSaison::numeroCarre).containsExactly("640001", "640002");
        assertThat(vm.annee()).isEqualTo(2026);
        assertThat(vm.resumeProperty().get()).contains("2 point(s) suivi(s)").contains("jusqu'au 30/09");
    }

    @Test
    @DisplayName("le sélecteur propose la saison chargée et les deux précédentes")
    void annees_proposees() {
        ServiceSoldeSaison service = mock(ServiceSoldeSaison.class);
        when(service.soldeCourant(ID)).thenReturn(new SoldeSaison(2026, LocalDate.of(2026, 7, 20), List.of()));

        SaisonViewModel vm = new SaisonViewModel(service, ID);
        assertThat(vm.anneesProposees()).as("vide tant qu'aucun solde chargé").isEmpty();

        vm.chargerCourant();
        assertThat(vm.anneesProposees()).containsExactly(2026, 2025, 2024);
    }

    @Test
    @DisplayName("le signalement apparaît quand la fenêtre du second passage se referme bientôt")
    void signalement_fenetre_proche() {
        ServiceSoldeSaison service = mock(ServiceSoldeSaison.class);
        // Aujourd'hui = 25/08 → 36 jours avant le 30/09 ; un point sans P2 → en attente.
        SoldeSaison solde = new SoldeSaison(
                2026,
                LocalDate.of(2026, 8, 25),
                List.of(ligne("640002", "B1", depose("2026-06-22"), CasePassage.absente(), "Poser")));
        when(service.soldeCourant(ID)).thenReturn(solde);

        SaisonViewModel vm = new SaisonViewModel(service, ID);
        vm.chargerCourant();

        assertThat(vm.signalementProperty().get()).contains("36 jour(s)").contains("1 point(s)");
    }

    @Test
    @DisplayName("pas de signalement quand la fenêtre est encore loin")
    void pas_de_signalement_si_fenetre_lointaine() {
        ServiceSoldeSaison service = mock(ServiceSoldeSaison.class);
        // Aujourd'hui = 01/07 → 91 jours avant le 30/09 : trop loin pour signaler.
        SoldeSaison solde = new SoldeSaison(
                2026,
                LocalDate.of(2026, 7, 1),
                List.of(ligne("640002", "B1", depose("2026-06-22"), CasePassage.absente(), "Poser")));
        when(service.soldeCourant(ID)).thenReturn(solde);

        SaisonViewModel vm = new SaisonViewModel(service, ID);
        vm.chargerCourant();

        assertThat(vm.signalementProperty().get()).isEmpty();
    }
}

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
import fr.univ_amu.iut.saison.view.CriteresSaison;
import fr.univ_amu.iut.saison.viewmodel.SaisonViewModel;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Les deux filtres ajoutés à « Ma saison » (#3103) : chercher un lieu, et ne garder que ce qu'il reste
/// à faire.
///
/// L'écran garde ses deux `ComboBox` (année, campagne) : elles disent la **structure** du travail, et
/// l'écart avec les quatre écrans à puces est justifié par le domaine, pas par un oubli. Ces deux
/// filtres-ci s'ajoutent, ils ne remplacent rien.
class SaisonFiltresTest {

    private static final String ID = "u-test";

    private static LigneSaison ligne(String carre, String point, String reste) {
        return new LigneSaison(carre, point, 1L, depose(), depose(), List.of(), reste);
    }

    private static CasePassage depose() {
        return new CasePassage(10L, StatutWorkflow.DEPOSE, Verdict.OK, LocalDate.parse("2026-06-22"), false, null);
    }

    private static SaisonViewModel viewModelAvec(LigneSaison... lignes) {
        ServiceSoldeSaison service = mock(ServiceSoldeSaison.class);
        when(service.soldeCourant(ID, null))
                .thenReturn(new SoldeSaison(2026, LocalDate.of(2026, 7, 20), List.of(lignes)));
        SaisonViewModel viewModel = new SaisonViewModel(service, ID);
        viewModel.chargerCourant();
        return viewModel;
    }

    @Test
    @DisplayName("#3103 : la recherche porte sur le carré ET le code du point")
    void la_recherche_porte_sur_le_carre_et_le_point() {
        // « Où en est ce lieu précis ? » : sur cet écran, un lieu se nomme par son carré ou par le code
        // de son point. Ce sont les deux seules colonnes d'identité de la table.
        LigneSaison aix = ligne("640380", "A1", "");

        assertThat(CriteresSaison.rechercheTexte().test(aix, "640380")).isTrue();
        assertThat(CriteresSaison.rechercheTexte().test(aix, "a1")).isTrue();
        assertThat(CriteresSaison.rechercheTexte().test(aix, "b2")).isFalse();
    }

    @Test
    @DisplayName("#3103 : chercher un lieu restreint la table, sans toucher au solde de la saison")
    void chercher_un_lieu_restreint_la_table() {
        SaisonViewModel viewModel = viewModelAvec(ligne("640380", "A1", ""), ligne("710255", "B2", "Poser le 2e"));

        viewModel
                .filtres()
                .definir(
                        CriteresSaison.RECHERCHE,
                        l -> CriteresSaison.rechercheTexte().test(l, "710255"));

        assertThat(viewModel.lignesFiltrees()).hasSize(1);
        assertThat(viewModel.lignes())
                .as("le solde de la saison se compte sur toute la saison, pas sur ce qu'on regarde")
                .hasSize(2);
    }

    @Test
    @DisplayName("#3103 : « Reste à faire » ne garde que les points qui ne sont pas à jour")
    void reste_a_faire_ne_garde_que_ce_qui_reste() {
        // La raison d'être de l'écran : « qu'est-ce qu'il me reste à faire ? ». Un point à jour porte un
        // « reste à faire » vide, et c'est exactement ce que dit `aJour()`.
        LigneSaison aJour = ligne("640380", "A1", "");
        LigneSaison enRetard = ligne("710255", "B2", "Poser l'enregistreur pour le passage 2");

        SaisonViewModel viewModel = viewModelAvec(aJour, enRetard);

        viewModel.filtres().definir(CriteresSaison.RESTE_A_FAIRE, CriteresSaison.resteAFaire());

        assertThat(viewModel.lignesFiltrees()).containsExactly(enRetard);
    }

    @Test
    @DisplayName("#3103 : retirer un filtre rétablit toutes les lignes")
    void retirer_un_filtre_retablit_tout() {
        // Le pendant du geste : décocher « Reste à faire » ou vider la recherche doit rendre la saison
        // entière. Un filtre qu'on ne sait pas retirer est un écran qui ment à la visite suivante.
        SaisonViewModel viewModel = viewModelAvec(ligne("640380", "A1", ""), ligne("710255", "B2", "Poser le 2e"));

        viewModel.filtres().definir(CriteresSaison.RESTE_A_FAIRE, CriteresSaison.resteAFaire());
        assertThat(viewModel.lignesFiltrees()).hasSize(1);

        viewModel.filtres().definir(CriteresSaison.RESTE_A_FAIRE, null);

        assertThat(viewModel.lignesFiltrees()).hasSize(2);
    }
}

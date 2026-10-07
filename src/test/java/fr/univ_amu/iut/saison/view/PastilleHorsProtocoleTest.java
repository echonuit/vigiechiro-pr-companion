package fr.univ_amu.iut.saison.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.saison.model.CasePassage;
import fr.univ_amu.iut.saison.model.LigneSaison;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Ce que la pastille « Hors protocole » écrit, et ce que son infobulle détaille (#6106).
///
/// Le rendu à l'écran, lui, est tenu par `PastillesDeLaSaisonTest` : cette classe-ci ne juge que le
/// texte, sans table ni police.
class PastilleHorsProtocoleTest {

    private static final LocalDate PREMIER_JOUR = LocalDate.of(2026, 6, 25);

    private static CasePassage nuit(LocalDate date) {
        return new CasePassage(7L, StatutWorkflow.DEPOSE, Verdict.OK, date, true, null);
    }

    /// Une ligne dont le point porte `combien` nuits opportunistes, datées de jours qui se suivent.
    private static LigneSaison ligne(int combien) {
        List<CasePassage> nuits = new ArrayList<>();
        for (int i = 0; i < combien; i++) {
            nuits.add(nuit(PREMIER_JOUR.plusDays(i)));
        }
        return ligne(nuits);
    }

    private static LigneSaison ligne(List<CasePassage> nuits) {
        return new LigneSaison("640005", "E1", 5L, CasePassage.absente(), CasePassage.absente(), nuits, "", null, null);
    }

    @Test
    @DisplayName("aucune nuit : ni pastille ni infobulle")
    void aucune_nuit_ne_dit_rien() {
        assertThat(PastilleHorsProtocole.libelle(ligne(0))).isNull();
        assertThat(PastilleHorsProtocole.infobulle(ligne(0))).isNull();
    }

    @Test
    @DisplayName("une nuit : la pastille la nomme avec sa date, et n'a pas d'infobulle à ajouter")
    void une_nuit_se_nomme_sans_infobulle() {
        assertThat(PastilleHorsProtocole.libelle(ligne(1))).isEqualTo("Opportuniste · 25/06");
        assertThat(PastilleHorsProtocole.infobulle(ligne(1)))
                .as("l'infobulle répéterait la pastille mot pour mot")
                .isNull();
    }

    @Test
    @DisplayName("une nuit sans date : « Opportuniste » seul, jamais « 1 nuits »")
    void une_nuit_sans_date() {
        assertThat(PastilleHorsProtocole.libelle(ligne(List.of(nuit(null))))).isEqualTo("Opportuniste");
    }

    @Test
    @DisplayName("deux nuits : la pastille compte, au pluriel")
    void deux_nuits_se_comptent() {
        assertThat(PastilleHorsProtocole.libelle(ligne(2))).isEqualTo("2 nuits");
    }

    @Test
    @DisplayName("le compte suit le nombre de nuits, jusqu'à trois chiffres")
    void le_compte_suit_le_nombre() {
        assertThat(PastilleHorsProtocole.libelle(ligne(5))).isEqualTo("5 nuits");
        assertThat(PastilleHorsProtocole.libelle(ligne(120))).isEqualTo("120 nuits");
    }

    @Test
    @DisplayName("plusieurs nuits : l'infobulle en donne une par ligne, dans l'ordre de la liste")
    void l_infobulle_liste_chaque_nuit() {
        assertThat(PastilleHorsProtocole.infobulle(ligne(3)))
                .isEqualTo("Opportuniste · 25/06\nOpportuniste · 26/06\nOpportuniste · 27/06");
    }

    @Test
    @DisplayName("l'infobulle garde l'ordre reçu, même quand il n'est pas celui des dates")
    void l_infobulle_ne_retrie_pas() {
        LigneSaison ligne = ligne(List.of(nuit(LocalDate.of(2026, 8, 25)), nuit(LocalDate.of(2026, 6, 25))));

        assertThat(PastilleHorsProtocole.infobulle(ligne)).isEqualTo("Opportuniste · 25/08\nOpportuniste · 25/06");
    }

    @Test
    @DisplayName("une nuit sans date garde sa ligne dans l'infobulle")
    void l_infobulle_garde_une_nuit_sans_date() {
        LigneSaison ligne = ligne(List.of(nuit(null), nuit(PREMIER_JOUR)));

        assertThat(PastilleHorsProtocole.infobulle(ligne)).isEqualTo("Opportuniste\nOpportuniste · 25/06");
    }
}

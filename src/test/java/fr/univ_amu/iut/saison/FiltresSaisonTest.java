package fr.univ_amu.iut.saison;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.saison.model.CasePassage;
import fr.univ_amu.iut.saison.model.FiltresSaison;
import fr.univ_amu.iut.saison.model.LigneSaison;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Les deux filtres de « Ma saison », écrits **une seule fois** pour l'écran et la ligne de commande
/// (passe 2 de la clôture #3092).
///
/// L'écran les a gagnés en #3103 ; `solde-saison` ne savait ni chercher un lieu, ni ne garder que ce
/// qu'il reste à faire. Deux surfaces sur la même question méritent la même réponse, et deux écritures
/// de la même règle finissent par diverger.
class FiltresSaisonTest {

    private static LigneSaison ligne(String carre, String point, String reste) {
        return ligne(carre, point, reste, null);
    }

    private static LigneSaison ligne(String carre, String point, String reste, String nomSite) {
        return new LigneSaison(carre, point, 1L, depose(), depose(), List.of(), reste, nomSite);
    }

    private static CasePassage depose() {
        return new CasePassage(10L, StatutWorkflow.DEPOSE, Verdict.OK, LocalDate.parse("2026-06-22"), false, null);
    }

    @Test
    @DisplayName("#3092 : le lieu se cherche sur le carré ET sur le code du point")
    void le_lieu_se_cherche_sur_le_carre_et_le_point() {
        LigneSaison aix = ligne("640380", "A1", "");

        assertThat(FiltresSaison.correspondAuLieu(aix, "640380")).isTrue();
        assertThat(FiltresSaison.correspondAuLieu(aix, "a1")).isTrue();
        assertThat(FiltresSaison.correspondAuLieu(aix, "b2")).isFalse();
    }

    @Test
    @DisplayName("#3092 : une recherche vide ne retient rien de particulier")
    void une_recherche_vide_ne_retient_rien_de_particulier() {
        // La ligne de commande passe `null` quand `--lieu` est absent : le filtre doit alors se taire,
        // pas écarter toutes les lignes.
        LigneSaison aix = ligne("640380", "A1", "");

        assertThat(FiltresSaison.parLieu(List.of(aix), null)).containsExactly(aix);
        assertThat(FiltresSaison.parLieu(List.of(aix), "   ")).containsExactly(aix);
    }

    @Test
    @DisplayName("#3092 : « reste à faire » écarte les points à jour")
    void reste_a_faire_ecarte_les_points_a_jour() {
        LigneSaison aJour = ligne("640380", "A1", "");
        LigneSaison enRetard = ligne("710255", "B2", "Poser l'enregistreur");

        assertThat(FiltresSaison.resteAFaire(List.of(aJour, enRetard))).containsExactly(enRetard);
    }

    @Test
    @DisplayName("#3092 : le nom donné au carré se cherche aussi (#3219)")
    void le_nom_donne_au_carre_se_cherche_aussi() {
        // L'écran l'a gagné juste avant cette clôture : l'extraction vers `model` doit le porter, sinon
        // la ligne de commande et l'écran répondraient déjà différemment le jour de leur naissance.
        LigneSaison nomme = ligne("640380", "A1", "", "Vallon des Sources");

        assertThat(FiltresSaison.correspondAuLieu(nomme, "vallon")).isTrue();
    }

    @Test
    @DisplayName("#3092 : les accents et la casse ne font pas rater un lieu")
    void les_accents_et_la_casse_ne_font_pas_rater_un_lieu() {
        // `--lieu vallon` doit trouver « Vallon » : c'est la promesse déjà tenue par `--lieu` de
        // lister-observations, et un naturaliste ne tape pas les accents dans un terminal.
        LigneSaison accentue = ligne("640380", "Éolienne", "");

        assertThat(FiltresSaison.correspondAuLieu(accentue, "eolienne")).isTrue();
    }
}

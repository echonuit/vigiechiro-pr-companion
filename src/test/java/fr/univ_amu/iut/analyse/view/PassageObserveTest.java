package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La valeur que porte une cellule « Passage » (#5901) : elle se compare sur la date puis le numéro, et
/// se lit en français. Sans écran : c'est la règle seule, que [ColonnePassageDesObservationsTest]
/// retrouve ensuite dessinée.
class PassageObserveTest {

    private static PassageObserve passage(int numero, String date) {
        return PassageObserve.de(new ObservationEspece(
                1,
                1,
                1,
                numero,
                2026,
                date,
                "640380",
                "A1",
                "Étang",
                "Pippip",
                0.9,
                null,
                null,
                StatutObservation.values()[0],
                "Ahetze"));
    }

    @Test
    @DisplayName("#5901 : la valeur lit la date de la base, et son libellé la dit en français")
    void la_valeur_lit_la_date_et_la_dit_en_francais() {
        PassageObserve valeur = passage(1, "2026-06-22");

        assertThat(valeur.date()).isEqualTo(LocalDate.of(2026, 6, 22));
        assertThat(valeur).hasToString("22/06/2026 · n°1");
    }

    @Test
    @DisplayName("#5901 : l'ordre est celui du temps, là où le libellé français trierait par jour du mois")
    void l_ordre_est_celui_du_temps() {
        List<PassageObserve> passages =
                new ArrayList<>(List.of(passage(3, "2026-07-01"), passage(2, "2026-06-22"), passage(1, "2026-06-22")));

        passages.sort(null);

        assertThat(passages)
                .extracting(PassageObserve::toString)
                .containsExactly("22/06/2026 · n°1", "22/06/2026 · n°2", "01/07/2026 · n°3");
    }

    @Test
    @DisplayName("#5901 : une date que la base ne porte pas lisible se range en dernier, et s'affiche telle quelle")
    void une_date_illisible_se_range_en_dernier() {
        List<PassageObserve> passages = new ArrayList<>(List.of(passage(1, "inconnue"), passage(2, "2026-06-22")));

        passages.sort(null);

        assertThat(passages.get(0)).hasToString("22/06/2026 · n°2");
        assertThat(passages.get(1).date()).isNull();
        assertThat(passages.get(1)).hasToString("inconnue · n°1");
    }
}

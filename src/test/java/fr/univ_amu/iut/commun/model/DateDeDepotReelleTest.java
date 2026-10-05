package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.ColonneDate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La date de dépôt **telle que la production l'écrit** se lit en français (#5761).
///
/// `DepotVigieChiro` et `ServicePassage` écrivent `horloge.maintenant().toString()` : un instant local,
/// « 2026-06-21T08:00:15.123456 ». Les lecteurs n'acceptaient qu'une date seule, et leurs tests leur en
/// donnaient une : ils passaient sur une forme que la base ne porte jamais.
class DateDeDepotReelleTest {

    /// Ce que `Horloge#maintenant` rend, mis en chaîne comme le font les deux services.
    private static final String ECRITE_PAR_LA_PRODUCTION =
            LocalDateTime.of(2026, 6, 21, 8, 0, 15, 123_456_000).toString();

    @Test
    @DisplayName("#5761 : la date de dépôt écrite par la production se lit « 21/06/2026 » dans une phrase")
    void la_date_de_depot_reelle_se_lit_dans_une_phrase() {
        assertThat(ECRITE_PAR_LA_PRODUCTION).isEqualTo("2026-06-21T08:00:15.123456");

        assertThat(Horodatage.dateSeule(ECRITE_PAR_LA_PRODUCTION)).isEqualTo("21/06/2026");
    }

    @Test
    @DisplayName("#5761 : la même date se lit dans une colonne, au lieu de s'y afficher absente")
    void la_date_de_depot_reelle_se_lit_dans_une_colonne() {
        assertThat(ColonneDate.analyser(ECRITE_PAR_LA_PRODUCTION)).isEqualTo(LocalDate.of(2026, 6, 21));
    }

    @Test
    @DisplayName("Une date seule se lit comme avant")
    void une_date_seule_se_lit_comme_avant() {
        assertThat(Horodatage.dateSeule("2026-06-21")).isEqualTo("21/06/2026");
        assertThat(ColonneDate.analyser("2026-06-21")).isEqualTo(LocalDate.of(2026, 6, 21));
    }

    /// Couper un instant de la plateforme au `T` change le jour dès que le décalage traverse minuit
    /// (#4017) : il n'est pas lu ici, et reste visible tel quel plutôt que faux.
    @Test
    @DisplayName("#5761 : un instant avec décalage n'est pas lu comme une date de la base")
    void un_instant_avec_decalage_n_est_pas_coupe() {
        assertThat(Horodatage.dateDe("2026-07-03T23:30:00+00:00")).isEmpty();
        assertThat(Horodatage.dateSeule("2026-07-03T23:30:00+00:00")).isEqualTo("2026-07-03T23:30:00+00:00");
    }

    @Test
    @DisplayName("Absente ou illisible, une date reste absente ou telle quelle")
    void absente_ou_illisible() {
        assertThat(Horodatage.dateDe(null)).isEmpty();
        assertThat(Horodatage.dateDe("  ")).isEmpty();
        assertThat(Horodatage.dateDe("hier soir")).isEmpty();
        assertThat(Horodatage.dateSeule("hier soir")).isEqualTo("hier soir");
        assertThat(ColonneDate.analyser("hier soir")).isNull();
    }
}

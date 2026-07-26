package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/// Vérifie la dimension dérivée **nuit** d'un [ContactHoraire] : la date du soir (bascule à midi), qui sert
/// de filtre « Nuit » (une nuit = un passage).
class ContactHoraireTest {

    private static ContactHoraire a(LocalDateTime heure) {
        return new ContactHoraire("PIPKUH", "Pipistrelle de Kuhl", "Chiroptères", heure);
    }

    @Test
    void la_nuit_est_la_date_du_soir_a_cheval_sur_minuit() {
        assertThat(a(LocalDateTime.of(2026, 6, 21, 22, 0)).nuit())
                .as("22:00 appartient à la nuit du 21")
                .isEqualTo(LocalDate.of(2026, 6, 21));
        assertThat(a(LocalDateTime.of(2026, 6, 22, 2, 0)).nuit())
                .as("02:00 du 22 appartient encore à la nuit du 21 (bascule à midi)")
                .isEqualTo(LocalDate.of(2026, 6, 21));
    }

    @Test
    void un_contact_sans_heure_n_a_pas_de_nuit() {
        assertThat(a(null).nuit()).isNull();
    }
}

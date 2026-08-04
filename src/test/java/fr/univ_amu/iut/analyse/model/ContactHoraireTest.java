package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/// Vérifie les dimensions **dérivées** d'un [ContactHoraire] : la **nuit** (date du soir, bascule à midi,
/// filtre « Nuit ») et le **point qualifié** par son carré (#2967, filtre « Lieu »).
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

    @Test
    void le_point_est_qualifie_par_son_carre() {
        // Le schéma pose UNIQUE(site_id, code) : « Z1 » existe sur presque tous les carrés. Proposer ce
        // code nu dans une liste de filtre laisserait cocher une valeur qui en désigne plusieurs (#2992).
        assertThat(avecLieu("Ahetze", "640380", "Z1").pointQualifie()).isEqualTo("640380 · Z1");
        assertThat(avecLieu("Biarritz", "870150", "Z1").pointQualifie())
                .as("le même code, dans un autre carré, est une AUTRE valeur")
                .isEqualTo("870150 · Z1");
    }

    @Test
    void sans_point_il_n_y_a_rien_a_qualifier() {
        // Nul, et non « 640380 · null » : la liste de filtre écarte les valeurs nulles, et une chaîne
        // fabriquée y entrerait comme une entrée cochable désignant l'absence de point.
        assertThat(avecLieu("Ahetze", "640380", null).pointQualifie()).isNull();
    }

    private static ContactHoraire avecLieu(String commune, String carre, String point) {
        return new ContactHoraire(
                "PIPKUH",
                "Pipistrelle de Kuhl",
                "Chiroptères",
                LocalDateTime.of(2026, 6, 21, 22, 0),
                commune,
                carre,
                point,
                1L,
                null);
    }
}

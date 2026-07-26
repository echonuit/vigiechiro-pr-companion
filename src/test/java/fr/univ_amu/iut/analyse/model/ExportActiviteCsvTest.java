package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/// Vérifie le formateur CSV de la courbe d'activité ([ExportActiviteCsv]) : en-têtes, une ligne par point
/// portant son contexte (passage, tranche), rattachement à la **nuit biologique** (bascule à midi) et cas
/// du passage sans contact.
class ExportActiviteCsvTest {

    @Test
    void ecrit_les_en_tetes_puis_une_ligne_de_contexte_par_point() {
        CourbeEspece courbe = new CourbeEspece(
                "PIPKUH",
                "Pipistrelle de Kuhl",
                "Chiroptères",
                17,
                List.of(new PointActivite(LocalDateTime.of(2026, 6, 21, 22, 30), 14)));

        String csv = ExportActiviteCsv.contenu(7L, LargeurTranche.DEMI_HEURE, List.of(courbe));

        assertThat(csv)
                .startsWith(
                        "\uFEFFPassage;Nuit;Code espèce;Nom espèce;Groupe;Début tranche;Tranche (min);Contacts\r\n");
        assertThat(csv)
                .as("chaque ligne porte son contexte : passage, nuit, tranche, contacts")
                .contains("7;2026-06-21;PIPKUH;Pipistrelle de Kuhl;Chiroptères;2026-06-21T22:30;30;14\r\n");
    }

    @Test
    void rattache_les_tranches_d_apres_minuit_a_la_nuit_du_soir() {
        // Deux tranches de la même nuit biologique, à cheval sur minuit : 22:30 (soir) et 02:00 (matin).
        CourbeEspece courbe = new CourbeEspece(
                "PIPKUH",
                "Pipistrelle de Kuhl",
                "Chiroptères",
                17,
                List.of(
                        new PointActivite(LocalDateTime.of(2026, 6, 21, 22, 30), 14),
                        new PointActivite(LocalDateTime.of(2026, 6, 22, 2, 0), 3)));

        String csv = ExportActiviteCsv.contenu(7L, LargeurTranche.DEMI_HEURE, List.of(courbe));

        // La tranche de 02:00 (date du 22) est rattachée à la nuit du 21 (bascule à midi), comme la courbe.
        assertThat(csv).contains(";2026-06-21;PIPKUH;Pipistrelle de Kuhl;Chiroptères;2026-06-22T02:00;30;3\r\n");
        assertThat(csv)
                .as("les deux tranches à cheval sur minuit portent la même nuit biologique")
                .doesNotContain("2026-06-22;PIPKUH");
    }

    @Test
    void un_passage_sans_contact_ecrit_les_en_tetes_seules() {
        String csv = ExportActiviteCsv.contenu(7L, LargeurTranche.HEURE, List.of());

        assertThat(csv)
                .isEqualTo("\uFEFFPassage;Nuit;Code espèce;Nom espèce;Groupe;Début tranche;Tranche (min);Contacts\r\n");
    }

    @Test
    void un_nom_d_espece_absent_laisse_la_colonne_vide() {
        CourbeEspece sansNom = new CourbeEspece(
                "PIPXXX", null, null, 2, List.of(new PointActivite(LocalDateTime.of(2026, 6, 21, 23, 0), 2)));

        String csv = ExportActiviteCsv.contenu(7L, LargeurTranche.DEMI_HEURE, List.of(sansNom));

        assertThat(csv).contains("7;2026-06-21;PIPXXX;;;2026-06-21T23:00;30;2\r\n");
    }
}

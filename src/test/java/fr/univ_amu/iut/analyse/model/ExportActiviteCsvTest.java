package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/// Vérifie le formateur CSV de l'activité ([ExportActiviteCsv]) : en-têtes, une ligne par tranche portant
/// **son contexte entier** (carré, point, nuit), et cas du jeu vide.
class ExportActiviteCsvTest {

    private static LigneActivite ligne(String carre, String point, String taxon, LocalDateTime tranche, int nombre) {
        return new LigneActivite(
                carre, point, LocalDate.of(2026, 6, 21), taxon, "Pipistrelle de Kuhl", "Chiroptères", tranche, nombre);
    }

    @Test
    void ecrit_les_en_tetes_puis_une_ligne_portant_son_lieu() {
        String csv = ExportActiviteCsv.contenu(
                LargeurTranche.DEMI_HEURE,
                List.of(ligne("640380", "A1", "PIPKUH", LocalDateTime.of(2026, 6, 21, 22, 30), 14)));

        assertThat(csv)
                .startsWith(
                        "\uFEFFCarré;Point;Nuit;Code espèce;Nom espèce;Groupe;Début tranche;Tranche (min);Contacts\r\n");
        assertThat(csv)
                .as("sans le carré et le point, un export multi-nuits ne dit plus d'où vient sa valeur")
                .contains("640380;A1;2026-06-21;PIPKUH;Pipistrelle de Kuhl;Chiroptères;2026-06-21T22:30;30;14\r\n");
    }

    @Test
    void un_lieu_inconnu_laisse_les_colonnes_vides_sans_decaler_les_autres() {
        String csv = ExportActiviteCsv.contenu(
                LargeurTranche.HEURE, List.of(ligne(null, null, "PIPKUH", LocalDateTime.of(2026, 6, 21, 23, 0), 2)));

        assertThat(csv).contains(";;2026-06-21;PIPKUH;");
    }

    @Test
    void un_caractere_special_en_premiere_position_declenche_deja_l_echappement() {
        // La détection ne cherche pas un caractère spécial "quelque part" mais dès le PREMIER : un nom
        // qui commence par lui (position 0) doit être aussi bien échappé qu'un nom qui le contient plus loin.
        assertThat(ExportActiviteCsv.contenu(LargeurTranche.HEURE, List.of(ligneAvecNomEspece(";Kuhl"))))
                .contains("\";Kuhl\"");
        assertThat(ExportActiviteCsv.contenu(LargeurTranche.HEURE, List.of(ligneAvecNomEspece("\"Kuhl"))))
                .contains("\"\"\"Kuhl\"");
        assertThat(ExportActiviteCsv.contenu(LargeurTranche.HEURE, List.of(ligneAvecNomEspece("\nKuhl"))))
                .contains("\"\nKuhl\"");
        assertThat(ExportActiviteCsv.contenu(LargeurTranche.HEURE, List.of(ligneAvecNomEspece("\rKuhl"))))
                .contains("\"\rKuhl\"");
    }

    private static LigneActivite ligneAvecNomEspece(String nomEspece) {
        return new LigneActivite(
                "640380",
                "A1",
                LocalDate.of(2026, 6, 21),
                "PIPKUH",
                nomEspece,
                "Chiroptères",
                LocalDateTime.of(2026, 6, 21, 22, 0),
                1);
    }

    @Test
    void aucune_ligne_ecrit_les_en_tetes_seules() {
        String csv = ExportActiviteCsv.contenu(LargeurTranche.HEURE, List.of());

        assertThat(csv)
                .isEqualTo(
                        "\uFEFFCarré;Point;Nuit;Code espèce;Nom espèce;Groupe;Début tranche;Tranche (min);Contacts\r\n");
    }
}

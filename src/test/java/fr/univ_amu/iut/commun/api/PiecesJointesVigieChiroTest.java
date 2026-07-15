package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Parseur des **pièces jointes** ([PiecesJointesVigieChiro], #1565) : le tableau `_items` de
/// `GET /participations/{id}/pieces_jointes?<filtre>` vers des [PieceJointe], best-effort (élément sans
/// `_id` ignoré, `disponible` absent → `false`, corps illisible → liste vide). Aucun réseau.
class PiecesJointesVigieChiroTest {

    @Test
    @DisplayName("pieces : lit _id, titre et disponible depuis _items (forme réelle du CSV d'observations)")
    void pieces_liste() {
        // Forme réelle de GET /participations/{id}/pieces_jointes?processing_extra=true (probe #1568).
        String corps = "{\"_items\":[{\"_id\":\"6a4ff786\","
                + "\"titre\":\"participation-6a4961f5-observations.csv\","
                + "\"mime\":\"application/x-processing-extra\",\"disponible\":true,"
                + "\"s3_id\":\"processing_extra/56-participation-6a4961f5-observations.csv\"}]}";

        assertThat(PiecesJointesVigieChiro.pieces(corps))
                .containsExactly(new PieceJointe("6a4ff786", "participation-6a4961f5-observations.csv", true));
    }

    @Test
    @DisplayName("pieces : plusieurs fichiers, dont un indisponible (WAV extrait d'un ZIP, #1244)")
    void pieces_disponibilites_melangees() {
        String corps = "{\"_items\":["
                + "{\"_id\":\"w1\",\"titre\":\"Car_000.wav\",\"disponible\":false},"
                + "{\"_id\":\"c1\",\"titre\":\"participation-x-observations.csv\",\"disponible\":true}]}";

        assertThat(PiecesJointesVigieChiro.pieces(corps))
                .containsExactly(
                        new PieceJointe("w1", "Car_000.wav", false),
                        new PieceJointe("c1", "participation-x-observations.csv", true));
    }

    @Test
    @DisplayName("pieces : élément sans _id ignoré ; disponible absent ou null → false")
    void pieces_tolerant() {
        String corps = "{\"_items\":["
                + "{\"titre\":\"sans-id.csv\",\"disponible\":true},"
                + "{\"_id\":\"a1\",\"titre\":\"sans-disponible.ta\"},"
                + "{\"_id\":\"a2\",\"disponible\":null}]}";

        assertThat(PiecesJointesVigieChiro.pieces(corps))
                .containsExactly(
                        new PieceJointe("a1", "sans-disponible.ta", false), new PieceJointe("a2", null, false));
    }

    @Test
    @DisplayName("pieces : corps illisible ou forme inattendue → liste vide (jamais d'exception)")
    void pieces_illisible() {
        assertThat(PiecesJointesVigieChiro.pieces("pas du json")).isEmpty();
        assertThat(PiecesJointesVigieChiro.pieces("{\"autre\":1}")).isEmpty();
        assertThat(PiecesJointesVigieChiro.pieces("[]")).isEmpty();
    }
}

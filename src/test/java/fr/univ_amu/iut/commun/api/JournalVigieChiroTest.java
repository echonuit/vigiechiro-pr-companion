package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Parseur du **journal de traitement** ([JournalVigieChiro], #1132) : extraction de l’id du
/// document `fichiers` (`participation.logs`) et de l’URL S3 signée (`/fichiers/{id}/acces`),
/// best-effort (vide si absent, indisponible ou illisible — jamais d’exception).
class JournalVigieChiroTest {

    @Test
    @DisplayName("idJournal : logs présent et disponible → _id du document fichiers")
    void id_journal_disponible() {
        String corps = """
                {"_id": "6a49", "logs": {"_id": "6a4fcb7d", "disponible": true, "mime": "text/plain"}}
                """;

        assertThat(JournalVigieChiro.idJournal(corps)).contains("6a4fcb7d");
    }

    @Test
    @DisplayName("idJournal : logs absent, indisponible ou corps illisible → vide")
    void id_journal_absent_ou_indisponible() {
        assertThat(JournalVigieChiro.idJournal("{\"_id\": \"6a49\"}")).isEmpty();
        assertThat(JournalVigieChiro.idJournal("{\"logs\": {\"_id\": \"6a4f\", \"disponible\": false}}"))
                .isEmpty();
        assertThat(JournalVigieChiro.idJournal("pas du json")).isEmpty();
    }
}

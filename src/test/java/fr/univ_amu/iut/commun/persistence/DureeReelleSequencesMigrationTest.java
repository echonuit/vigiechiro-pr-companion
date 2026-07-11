package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Workspace;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Régression de la migration `V20__duree_reelle_sequences.sql` (#1051) : `listening_sequence.duration_s`
/// était persisté **×10** (durée d'écoute au lieu de la durée réelle d'acquisition). La migration divise par
/// 10 les valeurs existantes et **préserve les NULL**. On simule deux séquences héritées (une à 50 s, une
/// NULL) puis on rejoue le script.
class DureeReelleSequencesMigrationTest {

    private static final String V20 = "/db/migration/V20__duree_reelle_sequences.sql";

    @TempDir
    Path dossier;

    private SourceDeDonnees source;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
    }

    @Test
    @DisplayName("V20 divise par 10 les durées expansées existantes et préserve les NULL")
    void repare_les_durees_expansees() {
        // Séquences héritées insérées sans le chaînage complet : FK désactivées le temps du seed (la
        // migration est un UPDATE pur, la validité référentielle est hors sujet ici).
        seed(
                "INSERT INTO listening_sequence (file_name, original_recording_id, file_path, session_id, duration_s)"
                        + " VALUES ('a_000.wav', 1, '/x/a_000.wav', 1, 50.0)",
                "INSERT INTO listening_sequence (file_name, original_recording_id, file_path, session_id, duration_s)"
                        + " VALUES ('b_000.wav', 1, '/x/b_000.wav', 1, NULL)");

        rejouerScript(V20);

        assertThat(dureeDe("a_000.wav")).isEqualTo(5.0);
        assertThat(dureeDe("b_000.wav")).as("les NULL sont préservés").isNull();
    }

    private void seed(String... inserts) {
        try (Connection connexion = source.getConnection();
                Statement st = connexion.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");
            for (String sql : inserts) {
                st.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Échec du seed", e);
        }
    }

    private Double dureeDe(String fichier) {
        try (Connection connexion = source.getConnection();
                Statement st = connexion.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT duration_s FROM listening_sequence WHERE file_name = '" + fichier + "'")) {
            if (!rs.next()) {
                return null;
            }
            double valeur = rs.getDouble(1);
            return rs.wasNull() ? null : valeur;
        } catch (SQLException e) {
            throw new IllegalStateException("Échec de lecture", e);
        }
    }

    private void rejouerScript(String ressource) {
        for (String instruction : decouperInstructions(lireRessource(ressource))) {
            try (Connection connexion = source.getConnection();
                    Statement st = connexion.createStatement()) {
                st.execute(instruction);
            } catch (SQLException e) {
                throw new IllegalStateException("Échec SQL : " + instruction, e);
            }
        }
    }

    private static String lireRessource(String chemin) {
        try (InputStream in = DureeReelleSequencesMigrationTest.class.getResourceAsStream(chemin)) {
            return new String(Optional.ofNullable(in).orElseThrow().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Lecture impossible : " + chemin, e);
        }
    }

    private static String[] decouperInstructions(String sql) {
        StringBuilder sansCommentaires = new StringBuilder();
        for (String ligne : sql.split("\n")) {
            if (!ligne.strip().startsWith("--")) {
                sansCommentaires.append(ligne).append('\n');
            }
        }
        return Arrays.stream(sansCommentaires.toString().split(";"))
                .map(String::strip)
                .filter(instruction -> !instruction.isEmpty())
                .toArray(String[]::new);
    }
}

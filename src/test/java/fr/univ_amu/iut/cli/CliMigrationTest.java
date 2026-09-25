package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.fixture.SortieCapturee;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Les erreurs de migration survenues après la construction de la CLI restent lisibles (#5506).
class CliMigrationTest {

    @TempDir
    Path workspace;

    private Cli cli;
    private final SortieCapturee capture = new SortieCapturee();

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        cli = Cli.applicative();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#5506 : un incident de migration en processus rend un message et journalise sa trace")
    void incident_de_migration_en_processus_sans_pile_sur_stderr() throws Exception {
        Files.writeString(workspace.resolve("vigiechiro.db"), "ceci n'est pas une base SQLite");
        Logger logCli = Logger.getLogger(Cli.class.getName());
        List<LogRecord> journalises = new ArrayList<>();
        Handler captureJournal = new Handler() {
            @Override
            public void publish(LogRecord enregistrement) {
                journalises.add(enregistrement);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        logCli.addHandler(captureJournal);
        try {
            int code = cli.executer(new String[] {"lister-sites"}, capture.sortie(), capture.erreur());

            assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
            assertThat(capture.texteErreur())
                    .startsWith("Échec :")
                    .doesNotContain("at fr.univ_amu.iut")
                    .doesNotContain("picocli.CommandLine");
            assertThat(journalises)
                    .as("la pile quitte stderr, mais reste disponible dans le journal")
                    .anySatisfy(enregistrement -> {
                        assertThat(enregistrement.getLevel()).isEqualTo(Level.SEVERE);
                        assertThat(enregistrement.getThrown()).isNotNull();
                    });
        } finally {
            logCli.removeHandler(captureJournal);
        }
    }
}

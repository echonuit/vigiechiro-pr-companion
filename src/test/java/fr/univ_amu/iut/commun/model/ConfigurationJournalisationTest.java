package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// L'amorçage de la journalisation (#1523) crée le dossier des logs et y ouvre un fichier tournant. On
/// exerce [ConfigurationJournalisation#installer] sur un logger **jetable** (jamais le logger racine
/// réel), pour ne pas polluer les autres tests : en test, aucun fichier de log n'est écrit, car ni
/// `App.start()` ni `Cli.main()` ne s'y exécutent.
class ConfigurationJournalisationTest {

    @Test
    @DisplayName("Le retrait de la console laisse le fichier de journal en place (#3570)")
    void retire_la_console_et_seulement_elle(@TempDir Path racine) throws Exception {
        Logger jetable = Logger.getAnonymousLogger();
        jetable.setUseParentHandlers(false);
        ConsoleHandler console = new ConsoleHandler();
        jetable.addHandler(console);
        FileHandler fichier = ConfigurationJournalisation.installer(jetable, racine.resolve("logs"));
        try {
            ConfigurationJournalisation.retirerLaConsole(jetable);

            assertThat(jetable.getHandlers())
                    .as("la pile d'un incident partait sur la sortie d'erreur de l'utilisateur, en plus"
                            + " de la phrase que la CLI écrit : c'est la console qu'on retire")
                    .noneMatch(ConsoleHandler.class::isInstance);
            assertThat(jetable.getHandlers())
                    .as("et SEULEMENT elle : sans le fichier, la trace ne serait plus nulle part")
                    .contains(fichier);
        } finally {
            fichier.close();
            console.close();
        }
    }

    @Test
    @DisplayName("Installe un fichier de journal tournant dans le dossier des logs (créé au besoin)")
    void installe_un_fichier_de_journal(@TempDir Path racine) throws Exception {
        Path dossierLogs = racine.resolve("logs");
        Logger jetable = Logger.getAnonymousLogger();
        jetable.setUseParentHandlers(false);

        FileHandler handler = ConfigurationJournalisation.installer(jetable, dossierLogs);
        try {
            assertThat(handler)
                    .as("le fichier de journal a bien pu être ouvert")
                    .isNotNull();
            assertThat(dossierLogs)
                    .as("le dossier des logs est créé au passage")
                    .isDirectory();

            jetable.severe("incident de test");
            handler.flush();

            try (Stream<Path> fichiers = Files.list(dossierLogs)) {
                assertThat(fichiers.map(fichier -> fichier.getFileName().toString()))
                        .as("un fichier vigiechiro-*.log est présent")
                        .anyMatch(nom -> nom.startsWith("vigiechiro") && nom.endsWith(".log"));
            }
        } finally {
            handler.close();
            jetable.removeHandler(handler);
        }
    }
}

package fr.univ_amu.iut.commun.api.plateforme;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.function.Consumer;
import org.testcontainers.containers.output.OutputFrame;

/// Les journaux des conteneurs de la plateforme de test (#6183), versés sous
/// `target/plateforme-de-test/journaux`, que le job `plateforme-de-test` dépose en artefact quand il
/// rougit.
///
/// Sans eux, un rouge ne disait que ce que la JVM avait vu : une écriture sans réponse, et rien de ce
/// que l'API, le faux S3 ou Mongo faisaient au même moment.
final class JournauxDesConteneurs {

    private JournauxDesConteneurs() {}

    /// Le fichier où le conteneur ainsi nommé verse son journal. Il porte le numéro du processus :
    /// chaque fork monte sa propre plateforme.
    static Path fichier(String conteneur) {
        return PlateformeDeTest.dossierDeLaPlateforme()
                .getParent()
                .getParent()
                .resolve("target/plateforme-de-test/journaux")
                .resolve(ProcessHandle.current().pid() + "-" + conteneur + ".log");
    }

    /// Le journal d'un conteneur, une ligne par sortie, précédée de l'instant où la JVM l'a reçue.
    ///
    /// Il s'écrit ligne à ligne, pour qu'un fork qui meurt laisse ce qu'il avait reçu.
    static Consumer<OutputFrame> pour(String conteneur) {
        Path fichier = fichier(conteneur);
        try {
            Files.createDirectories(fichier.getParent());
            Files.deleteIfExists(fichier);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Object verrou = new Object();
        return sortie -> {
            if (sortie.getType() == OutputFrame.OutputType.END) {
                return;
            }
            String ligne = Instant.now() + " " + sortie.getType() + " " + sortie.getUtf8StringWithoutLineEnding()
                    + System.lineSeparator();
            synchronized (verrou) {
                try {
                    Files.writeString(fichier, ligne, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }
}

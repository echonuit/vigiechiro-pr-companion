package fr.univ_amu.iut.commun.api.plateforme;

import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.model.BuildResponseItem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.testcontainers.DockerClientFactory;

/// Construit une image de la plateforme de test, et verse la sortie de sa construction dans les journaux
/// de la plateforme (#6267, chantier #5646).
///
/// `ImageFromDockerfile` ne rend pas cette sortie, et Testcontainers la journalise par SLF4J, que les
/// tests branchent sur `slf4j-nop` : quand un fork ne construisait pas l'image du front, rien ne disait
/// pourquoi. Elle va maintenant dans `<pid>-construction-<nom>.log`, à côté des journaux des conteneurs,
/// que le job `plateforme-de-test` dépose en artefact quand il rougit. Chaque construction du fork s'y
/// ajoute à la suite, derrière une ligne qui la date.
///
/// La construction reste celle d'avant : le même Dockerfile, les mêmes arguments, la même étiquette,
/// et le cache du démon tel qu'il est (ADR 5666).
final class ConstructionDeLImage {

    private ConstructionDeLImage() {}

    /// Construit l'image `etiquette` depuis `dockerfile`, dans le contexte de son dossier, et rend son
    /// étiquette. Un échec lève, après que la sortie de la construction a été versée.
    static String construire(String nom, Path dockerfile, String etiquette, Map<String, String> arguments) {
        // Le journal ne se vide pas : un fork qui échoue reconstruit l'image pour sa classe suivante, et
        // vider le journal à chaque construction effaçait la sortie de l'échec sous celle de la réussite.
        Path journal = JournauxDesConteneurs.fichier("construction-" + nom);
        try {
            Files.createDirectories(journal.getParent());
            Files.writeString(
                    journal,
                    "=== construction de " + etiquette + ", " + Instant.now() + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        BuildImageCmd commande = DockerClientFactory.instance()
                .client()
                .buildImageCmd()
                .withDockerfile(dockerfile.toFile())
                .withBaseDirectory(dockerfile.getParent().toFile())
                .withTags(Set.of(etiquette));
        arguments.forEach(commande::withBuildArg);
        commande.exec(new Verseur(journal)).awaitImageId();
        return etiquette;
    }

    /// Verse chaque ligne de la sortie de la construction, précédée de l'instant de sa réception.
    private static final class Verseur extends BuildImageResultCallback {

        private final Path journal;

        Verseur(Path journal) {
            this.journal = journal;
        }

        @Override
        public void onNext(BuildResponseItem morceau) {
            String texte = morceau.getStream() != null
                    ? morceau.getStream()
                    : morceau.getErrorDetail() != null
                            ? "ERREUR " + morceau.getErrorDetail().getMessage()
                            : null;
            if (texte != null) {
                verser(texte);
            }
            super.onNext(morceau);
        }

        private synchronized void verser(String texte) {
            StringBuilder lignes = new StringBuilder();
            for (String ligne : texte.split("\n", -1)) {
                if (!ligne.isBlank()) {
                    lignes.append(Instant.now()).append(' ').append(ligne).append(System.lineSeparator());
                }
            }
            try {
                Files.writeString(journal, lignes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}

package fr.univ_amu.iut.passage.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/// Dossier temporaire de **régénération** des tranches, partagé par les deux voies « bruts » (#1653) : la
/// réactivation d'un passage réel ([ReactivationDepuisBruts]) comme l'hydratation d'un passage reconstruit
/// ([HydratationDepuisBruts]) régénèrent dans un temporaire, rebranchent ce qui est vérifié, puis
/// l'effacent - **un brut à la fois**, pour ne pas doubler transitoirement l'occupation disque que
/// l'archivage cherchait justement à libérer.
final class DossierTemporaire {

    private DossierTemporaire() {}

    /// Crée un dossier temporaire préfixé (le préfixe rend un éventuel reliquat identifiable).
    static Path creer(String prefixe) {
        try {
            return Files.createTempDirectory(prefixe);
        } catch (IOException e) {
            throw new UncheckedIOException("Dossier temporaire impossible (" + prefixe + ")", e);
        }
    }

    /// Efface le dossier et son contenu **au mieux** : un reliquat dans le dossier temporaire du système
    /// n'est pas une raison de faire échouer une réactivation par ailleurs réussie.
    static void supprimer(Path dossier) {
        try (Stream<Path> contenu = Files.walk(dossier)) {
            contenu.sorted(Comparator.reverseOrder()).forEach(chemin -> {
                try {
                    Files.deleteIfExists(chemin);
                } catch (IOException ignore) {
                    // Reliquat toléré : voir la Javadoc.
                }
            });
        } catch (IOException ignore) {
            // Idem.
        }
    }
}

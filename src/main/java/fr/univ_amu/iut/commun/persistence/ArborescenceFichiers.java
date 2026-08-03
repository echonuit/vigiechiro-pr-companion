package fr.univ_amu.iut.commun.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/// Copie d'arborescence, partagée par la sauvegarde complète et la restauration complète.
///
/// Elle vit à part depuis que les deux sens en ont besoin (#2727) : la sauvegarde emporte les
/// dossiers de son, la restauration les replace.
final class ArborescenceFichiers {

    private ArborescenceFichiers() {}

    /// Copie `origine` vers `cible` en écrasant les fichiers existants.
    ///
    /// L'écrasement est délibéré : restaurer, c'est remettre l'état sauvegardé par-dessus l'état
    /// courant. Il est aussi ce qui rend la **destination** décisive, et c'est tout le sujet de
    /// #2726 : deux racines qui visent la même destination fusionnent ici, en silence.
    static void copier(Path origine, Path cible) throws IOException {
        try (Stream<Path> arbre = Files.walk(origine)) {
            for (Path chemin : (Iterable<Path>) arbre::iterator) {
                Path destination = cible.resolve(origine.relativize(chemin).toString());
                if (Files.isDirectory(chemin)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(chemin, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}

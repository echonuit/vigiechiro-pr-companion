package fr.univ_amu.iut.fixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/// Ce qu'un dossier contient, pour les cas qui affirment qu'on n'y a pas touché.
///
/// La règle R9 dit que l'import copie depuis la carte et n'écrit jamais dessus. `CopieProtegee` en
/// garde la moitié fichier par fichier, en comparant l'empreinte de la source avant et après. Il y
/// manquait l'autre moitié : un fichier **ajouté** à côté, ou **renommé**, laisse chaque empreinte
/// individuelle intacte. Cette classe compare l'inventaire entier (#5435).
public final class EmpreinteDeDossier {

    private EmpreinteDeDossier() {}

    /// Le chemin relatif de chaque fichier du dossier, et une empreinte de son contenu.
    ///
    /// Ni les dates ni les permissions n'entrent dans l'empreinte. Lire un fichier touche son `atime`
    /// sur les systèmes qui le tiennent, et un cas rougirait alors sur une lecture, c'est-à-dire sur
    /// exactement ce que la règle autorise. C'est ce qui rend la comparaison jouable sous Windows
    /// comme sous POSIX, là où une comparaison de permissions ne l'aurait pas été.
    public static Map<Path, String> de(Path dossier) throws IOException {
        try (Stream<Path> entrees = Files.walk(dossier)) {
            Map<Path, String> empreinte = new TreeMap<>();
            for (Path entree : entrees.filter(Files::isRegularFile).toList()) {
                empreinte.put(
                        dossier.relativize(entree),
                        Files.size(entree) + ":" + Arrays.hashCode(Files.readAllBytes(entree)));
            }
            return empreinte;
        }
    }
}

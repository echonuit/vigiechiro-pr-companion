package fr.univ_amu.iut.commun.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
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

    /// Efface `cible` et tout ce qu'elle contient. Une cible absente n'est pas une erreur.
    ///
    /// ⚠️ Elle **lève** plutôt que d'ignorer, contrairement aux suppressions best-effort du dépôt
    /// (`ExtracteurZip`, `SupprimerSauvegarde`) : ici l'appelant a besoin de savoir. Une bascule de
    /// restauration qui ne parvient pas à retirer l'ancien dossier ne doit pas enchaîner sur le
    /// renommage comme si de rien n'était (#3514).
    /// Ce que pèse un dossier, fichiers réguliers seulement.
    ///
    /// Vient d'`InventaireSauvegardes`, où elle était privée : la sauvegarde en a eu besoin pour
    /// mesurer la place requise **avant** de copier (#3572), et une seconde implémentation aurait été
    /// la huitième variante du même parcours d'arborescence dans ce dépôt.
    ///
    /// ⚠️ Un fichier qui disparaît pendant le parcours compte pour **zéro** plutôt que de faire échouer
    /// la mesure : observer ne doit jamais être plus fragile que ce qu'on observe.
    static long octets(Path dossier) throws IOException {
        try (Stream<Path> arborescence = Files.walk(dossier)) {
            return arborescence
                    .filter(Files::isRegularFile)
                    .mapToLong(ArborescenceFichiers::tailleOuZero)
                    .sum();
        }
    }

    private static long tailleOuZero(Path fichier) {
        try {
            return Files.size(fichier);
        } catch (IOException disparu) {
            return 0L;
        }
    }

    static void supprimerRecursivement(Path cible) throws IOException {
        if (!Files.exists(cible)) {
            return;
        }
        try (Stream<Path> arbre = Files.walk(cible)) {
            for (Path chemin : arbre.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(chemin);
            }
        }
    }
}

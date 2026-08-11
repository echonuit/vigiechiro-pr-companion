package fr.univ_amu.iut.commun.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/// Copie d'arborescence, partagée par la sauvegarde complète et la restauration complète.
///
/// Elle vit à part depuis que les deux sens en ont besoin (#2727) : la sauvegarde emporte les
/// dossiers de son, la restauration les replace.
public final class ArborescenceFichiers {

    private ArborescenceFichiers() {}

    /// Copie `origine` vers `cible` en écrasant les fichiers existants.
    ///
    /// L'écrasement est délibéré : restaurer, c'est remettre l'état sauvegardé par-dessus l'état
    /// courant. Il est aussi ce qui rend la **destination** décisive, et c'est tout le sujet de
    /// #2726 : deux racines qui visent la même destination fusionnent ici, en silence.
    public static void copier(Path origine, Path cible) throws IOException {
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
    public static long octets(Path dossier) throws IOException {
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

    /// Efface `cible` et son contenu **au mieux**, et rend ce qui a résisté.
    ///
    /// Ne lève jamais : un nettoyage de temporaire ne doit pas transformer une opération réussie en
    /// échec. Mais il ne se tait pas non plus - la liste rendue dit ce qui reste, et l'appelant en fait
    /// ce qu'il veut : l'ignorer, ou le rapporter à l'utilisateur.
    ///
    /// ⚠️ C'est le second des deux contrats, et le nom le dit (#3574). Sept variantes de ce geste
    /// vivaient dans le dépôt sous des noms qui se ressemblaient tous, avec **quatre** comportements en
    /// cas d'échec : lever, lever sans le déclarer, avaler, ou rapporter. Une copie faite depuis le
    /// mauvais modèle changeait le comportement en cas de panne sans rien casser de visible.
    ///
    /// Le parcours continue après un échec plutôt que de s'arrêter au premier récalcitrant : il faut
    /// retirer tout ce qui peut l'être, sinon un fichier verrouillé laisserait le reste derrière lui.
    ///
    /// @return ce qui a résisté, du plus profond au plus haut, **avec la raison** - sans elle, un
    ///     appelant qui rend compte à l'utilisateur devrait refaire le parcours pour la retrouver
    public static List<EchecEffacement> effacerAuMieux(Path cible) {
        List<EchecEffacement> restants = new ArrayList<>();
        if (!Files.exists(cible)) {
            return restants;
        }
        try (Stream<Path> arbre = Files.walk(cible)) {
            for (Path chemin : arbre.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(chemin);
                } catch (IOException resiste) {
                    restants.add(new EchecEffacement(chemin, resiste));
                }
            }
        } catch (IOException illisible) {
            restants.add(new EchecEffacement(cible, illisible));
        }
        return restants;
    }

    /// Ce qui a résisté, et pourquoi.
    ///
    /// La raison fait partie du contrat parce qu'un des appelants en **rend compte à l'utilisateur**
    /// (`NettoyageDossiersOrphelins`) : la lui retirer l'obligerait à refaire le parcours pour la
    /// retrouver, c'est-à-dire à réécrire ce que cette classe existe pour n'écrire qu'une fois.
    ///
    /// @param chemin ce qui n'a pas pu être supprimé
    /// @param cause l'échec système, dont le message est ce qu'on montre
    public record EchecEffacement(Path chemin, IOException cause) {}

    public static void supprimerRecursivement(Path cible) throws IOException {
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

package fr.univ_amu.iut.commun.persistence;

import fr.univ_amu.iut.commun.model.TailleFichier;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
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

    /// Ce que pèse un dossier, fichiers réguliers seulement, **pour qui veut l'afficher**.
    ///
    /// Vient d'`InventaireSauvegardes`, où elle était privée : la sauvegarde en a eu besoin pour
    /// mesurer la place requise **avant** de copier (#3572), et une seconde implémentation aurait été
    /// la huitième variante du même parcours d'arborescence dans ce dépôt.
    ///
    /// ⚠️ Un fichier illisible compte pour **zéro** : observer ne doit jamais être plus fragile que ce
    /// qu'on observe, et refuser d'afficher une taille parce qu'un fichier a bougé serait absurde.
    /// C'est juste pour un **inventaire**, et faux pour une **décision** - un garde qui additionne ces
    /// zéros conclut « il y a la place » depuis un silence (#3627). Qui décide appelle [#peser].
    public static long octets(Path dossier) throws IOException {
        return peser(dossier, TailleFichier.reelle()).octets();
    }

    /// Ce que pèse un dossier, **et ce qu'on n'a pas pu lire**.
    ///
    /// Même parcours que [#octets], autre contrat : la mesure ne se tait pas sur ses trous. C'est le
    /// second des deux besoins que #3574 avait déjà séparés pour l'effacement, et que la pesée
    /// confondait encore.
    ///
    /// ⚠️ Elle ne **lève** pas pour autant. Une mesure qui s'interrompt au premier fichier illisible ne
    /// dirait pas combien pèse le reste, et l'appelant qui veut refuser a besoin des deux : le total
    /// connu, et ce qui manque à ce total.
    ///
    /// @param taille le port de lecture, injectable parce que l'illisibilité ne se fabrique pas de
    ///     façon portable sur un vrai système de fichiers
    public static Pesee peser(Path dossier, TailleFichier taille) throws IOException {
        List<EchecLecture> illisibles = new ArrayList<>();
        Deque<Path> aVisiter = new ArrayDeque<>();
        aVisiter.add(dossier);
        long total = 0L;
        while (!aVisiter.isEmpty()) {
            total += peserLeContenu(aVisiter.remove(), taille, aVisiter, illisibles);
        }
        return new Pesee(total, List.copyOf(illisibles));
    }

    /// Pèse un dossier, empile ses sous-dossiers, et **note** celui qu'on n'a pas pu ouvrir.
    ///
    /// ⚠️ Le parcours est explicite parce que `Files.walk` **lève** sur le premier dossier qu'il ne peut
    /// pas lister, et interrompt le flux : on n'apprend ni ce que pèse le reste, ni combien de dossiers
    /// ont résisté (#3634). Même choix que [#effacerAuMieux], qui continue après un récalcitrant.
    ///
    /// ⚠️ `NOFOLLOW_LINKS` sur le test de dossier, et lui seul : c'est ce que fait `Files.walk` par
    /// défaut, et sans lui un lien vers un dossier ancêtre ferait **tourner ce parcours sans fin**. Un
    /// lien vers un fichier, lui, reste pesé comme avant - `isRegularFile` suit le lien.
    private static long peserLeContenu(
            Path dossier, TailleFichier taille, Deque<Path> aVisiter, List<EchecLecture> illisibles) {
        long total = 0L;
        try (DirectoryStream<Path> entrees = Files.newDirectoryStream(dossier)) {
            for (Path entree : entrees) {
                if (Files.isDirectory(entree, LinkOption.NOFOLLOW_LINKS)) {
                    aVisiter.add(entree);
                } else if (Files.isRegularFile(entree)) {
                    total += peserOuNoter(entree, taille, illisibles);
                }
            }
        } catch (IOException ferme) {
            illisibles.add(new EchecLecture(dossier, ferme));
        }
        return total;
    }

    /// Le zéro reste, mais il n'est plus seul : le fichier rejoint la liste des illisibles, et c'est
    /// elle qui empêche l'appelant de prendre ce total pour un compte complet.
    private static long peserOuNoter(Path fichier, TailleFichier taille, List<EchecLecture> illisibles) {
        try {
            return taille.octets(fichier);
        } catch (IOException illisible) {
            illisibles.add(new EchecLecture(fichier, illisible));
            return 0L;
        }
    }

    /// Ce qu'un dossier pèse, et ce que ce total **ne compte pas**.
    ///
    /// @param octets la somme de ce qui a pu être lu, donc un **minorant** dès que `illisibles` n'est
    ///     pas vide : c'est exactement pourquoi il ne faut pas en conclure qu'il y a la place
    /// @param illisibles ce qui n'a pas pu être pesé, avec la raison
    public record Pesee(long octets, List<EchecLecture> illisibles) {

        /// Vrai quand le total compte tout ce que le dossier contient. Seul cas où une **décision**
        /// peut s'appuyer dessus.
        public boolean complete() {
            return illisibles.isEmpty();
        }
    }

    /// Ce qui n'a pas pu être pesé, et pourquoi.
    ///
    /// La raison fait partie du contrat pour la même raison que dans [EchecEffacement] : l'appelant en
    /// rend compte à l'utilisateur, et un refus qui ne dit pas quoi débloquer est un mur.
    ///
    /// @param chemin le fichier dont la taille est restée inconnue
    /// @param cause l'échec système, dont le message est ce qu'on montre
    public record EchecLecture(Path chemin, IOException cause) {}

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

    /// Efface `cible` et tout ce qu'elle contient. Une cible absente n'est pas une erreur.
    ///
    /// ⚠️ Elle **lève** plutôt que d'ignorer, contrairement aux suppressions best-effort du dépôt
    /// (`ExtracteurZip`, `SupprimerSauvegarde`) : ici l'appelant a besoin de savoir. Une bascule de
    /// restauration qui ne parvient pas à retirer l'ancien dossier ne doit pas enchaîner sur le
    /// renommage comme si de rien n'était (#3514).
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

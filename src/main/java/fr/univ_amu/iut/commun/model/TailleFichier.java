package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Ce que **pèse un fichier**, isolé en interface pour la même raison qu'[EspaceDisque] : rendre un
/// garde-fou testable sans dépendre de l'état de la machine.
///
/// ## Pourquoi un port là où `Files.size` suffirait
///
/// Le garde d'espace de la sauvegarde doit se comporter correctement quand un fichier est **illisible**
/// (droit refusé, support démonté en cours de parcours, lien mort). Or cette panne ne se fabrique pas de
/// façon portable : un dossier en `chmod 000` fait d'abord échouer le **parcours**, pas la pesée, et un
/// lien mort est écarté par `isRegularFile` avant qu'on arrive à le peser.
///
/// Sans ce port, le test du garde serait soit impossible, soit **conditionnel** - et un test qui
/// s'abstient rend le même vert que celui qui s'exécute.
///
/// Contrairement à [EspaceDisque], l'échec n'a **qu'une** lecture ici : il est toujours rapporté à
/// l'appelant, jamais traduit en `0`. C'est tout l'objet de #3627 - un zéro venu d'un silence se
/// confondait avec un fichier vide, et le garde en concluait qu'il y avait la place.
@FunctionalInterface
public interface TailleFichier {

    /// Octets occupés par `fichier`.
    ///
    /// @throws IOException si la taille ne peut pas être lue - ce qui n'est **pas** zéro
    long octets(Path fichier) throws IOException;

    /// La taille réelle, lue sur le système de fichiers.
    static TailleFichier reelle() {
        return Files::size;
    }
}

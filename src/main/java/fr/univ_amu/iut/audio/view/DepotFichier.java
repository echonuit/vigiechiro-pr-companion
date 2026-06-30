package fr.univ_amu.iut.audio.view;

import java.io.File;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javafx.scene.Node;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

/// Installe le **glisser-déposer de fichiers** sur un nœud : alternative au `FileChooser` natif quand
/// celui-ci coince (fréquent en devcontainer / bureau distant sous Linux). Quand l'activation est vraie,
/// déposer un fichier sur la cible appelle `surDepot`.
///
/// Côté **vue** (dépend de `javafx.scene.input`), isolé du controller pour le garder léger : le controller
/// n'expose qu'un consommateur de fichiers, sans connaître la mécanique `DragEvent` / `Dragboard`.
final class DepotFichier {

    private DepotFichier() {}

    /// Câble le glisser-déposer sur `cible`. Le survol n'accepte la copie que si `actif` est vrai et que
    /// le presse-papiers porte des fichiers ; le dépôt transmet les fichiers déposés à `surDepot` (qui
    /// renvoie `true` s'il les a pris en charge, pour la complétion du dépôt).
    static void installer(Node cible, BooleanSupplier actif, Predicate<List<File>> surDepot) {
        cible.setOnDragOver(evenement -> {
            Dragboard presse = evenement.getDragboard();
            if (actif.getAsBoolean() && presse.hasFiles()) {
                evenement.acceptTransferModes(TransferMode.COPY);
            }
            evenement.consume();
        });
        cible.setOnDragDropped(evenement -> terminer(evenement, actif, surDepot));
    }

    private static void terminer(DragEvent evenement, BooleanSupplier actif, Predicate<List<File>> surDepot) {
        Dragboard presse = evenement.getDragboard();
        boolean pris = actif.getAsBoolean() && presse.hasFiles() && surDepot.test(presse.getFiles());
        evenement.setDropCompleted(pris);
        evenement.consume();
    }
}

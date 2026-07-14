package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.multisite.model.LignePassage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableObjectValue;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/// État et gestes du menu **☰ actions** de la vue multi-sites, extraits de [MultisiteController] : le
/// controller y avait accumulé le câblage des quatre entrées et le sélecteur de fichier de l'export,
/// jusqu'à franchir le plafond de taille de classe. Rien de plus qu'un déplacement : la logique est
/// inchangée.
///
/// Le fil conducteur est le même pour les quatre : **une entrée qui ne peut rien faire doit dire
/// pourquoi**. Un `MenuItem` désactivé n'accueille pas de tooltip (il ne reçoit plus le survol), donc la
/// cause du grisage passe dans son **libellé** (#789) ; et l'entrée qui n'a aucun sens dans le contexte
/// (reconstruire hors connexion VigieChiro) est **retirée** plutôt que grisée.
final class MenuActionsMultisite {

    private MenuActionsMultisite() {}

    /// Câble l'état des entrées du menu ☰ sur l'état de l'écran.
    ///
    /// @param nonVide vrai quand le tableau filtré contient au moins une ligne
    /// @param selection ligne sélectionnée (`null` si aucune)
    /// @param peutReconstruire vrai quand la passerelle VigieChiro est présente (#1396)
    static void installer(
            MenuItem exporter,
            MenuItem ecouterLot,
            MenuItem ecouterPassage,
            MenuItem reconstruire,
            ObservableBooleanValue nonVide,
            ObservableObjectValue<LignePassage> selection,
            boolean peutReconstruire) {
        exporter.disableProperty().bind(Bindings.not(nonVide));
        exporter.textProperty()
                .bind(Bindings.when(nonVide).then("📤 Exporter…").otherwise("📤 Exporter… (aucune ligne à exporter)"));
        // Écoute : le lot suit la présence de lignes filtrées ; un passage exige une ligne sélectionnée.
        ecouterLot.disableProperty().bind(Bindings.not(nonVide));
        ecouterLot
                .textProperty()
                .bind(Bindings.when(nonVide)
                        .then("🎧 Écouter le lot filtré")
                        .otherwise("🎧 Écouter le lot filtré (aucune ligne)"));
        ecouterPassage.disableProperty().bind(Bindings.isNull(selection));
        // Un item qui ne peut rien faire ne vaut pas mieux qu'un item absent : il vaut moins.
        reconstruire.setVisible(peutReconstruire);
    }

    /// « Exporter » : ouvre le sélecteur de fichier natif (enregistrement) et, si l'utilisateur confirme,
    /// remet le chemin choisi et **l'ordre réellement affiché** (tri par clic d'en-tête inclus, #291) au
    /// travail d'écriture. Le dialogue vit dans la vue (non testé en TestFX) ; l'écriture est testée côté
    /// ViewModel.
    static void exporter(Window fenetre, TableView<LignePassage> table, Consumer<Path> ecrire) {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Exporter les passages en CSV");
        selecteur.setInitialFileName("vue-multisite.csv");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File fichier = selecteur.showSaveDialog(fenetre);
        if (fichier != null) {
            ecrire.accept(fichier.toPath());
        }
    }

    /// Instantané des lignes **telles qu'affichées** (la table applique un `SortedList` par-dessus le
    /// ViewModel) : c'est cet ordre-là qu'on exporte, pas l'ordre interne du ViewModel.
    static List<LignePassage> lignesAffichees(TableView<LignePassage> table) {
        return List.copyOf(table.getItems());
    }
}

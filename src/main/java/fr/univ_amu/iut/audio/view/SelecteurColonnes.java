package fr.univ_amu.iut.audio.view;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/// Choix d'affichage des colonnes de la table audio, offert par **deux entrées synchronisées** : un menu
/// **contextuel** (clic droit sur la table) et un sous-menu **« Colonnes »** dans le menu ☰. Chaque case
/// est liée bidirectionnellement à la `visibleProperty` de sa colonne ; les deux jeux de cases restent
/// donc cohérents entre eux (même propriété source) et avec l'état réel de la table.
///
/// Sorti du [SonsValidationController] pour l'alléger (cohésion, seuil PMD).
final class SelecteurColonnes {

    private SelecteurColonnes() {}

    /// Une colonne proposée au choix d'affichage, avec son libellé lisible (les colonnes-icônes ⭐/💬 ont
    /// un en-tête peu parlant hors contexte).
    record ColonneAffichable(TableColumn<?, ?> colonne, String libelle) {}

    /// Installe le menu contextuel sur `table` et ajoute le sous-menu « Colonnes » à `menu` (☰).
    static void installer(TableView<?> table, MenuButton menu, List<ColonneAffichable> colonnes) {
        table.setContextMenu(new ContextMenu(cases(colonnes).toArray(MenuItem[]::new)));

        Menu sousMenu = new Menu("Colonnes");
        sousMenu.getItems().setAll(cases(colonnes));
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(sousMenu);
    }

    /// Un jeu **neuf** de cases à cocher (un `MenuItem` n'ayant qu'un seul parent, on en construit un jeu
    /// par menu), chacune liée à la visibilité de sa colonne.
    private static List<CheckMenuItem> cases(List<ColonneAffichable> colonnes) {
        List<CheckMenuItem> cases = new ArrayList<>();
        for (ColonneAffichable c : colonnes) {
            CheckMenuItem item = new CheckMenuItem(c.libelle());
            item.selectedProperty().bindBidirectional(c.colonne().visibleProperty());
            cases.add(item);
        }
        return cases;
    }
}

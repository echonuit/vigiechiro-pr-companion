package fr.univ_amu.iut.commun.view;

import java.util.function.Function;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;

/// Construit un sous-menu **« Copier ▸ »** pour le clic droit d'une [TableView] (#1798) : chaque entrée
/// copie une valeur de la ligne sélectionnée vers le presse-papier ([PressePapier]). Le sous-menu est
/// désactivé sans sélection. Geste transversal absent jusqu'ici, très attendu pour recouper des données.
public final class MenuCopier {

    private MenuCopier() {}

    /// Une entrée du sous-menu : son `libelle` et l'extracteur de la valeur à copier depuis la ligne.
    public record Entree<T>(String libelle, Function<T, String> valeur) {}

    @SafeVarargs
    public static <T> Menu creer(TableView<T> table, Entree<T>... entrees) {
        Menu menu = new Menu("Copier");
        menu.disableProperty()
                .bind(table.getSelectionModel().selectedItemProperty().isNull());
        for (Entree<T> entree : entrees) {
            MenuItem item = new MenuItem(entree.libelle());
            item.setOnAction(evenement -> {
                T selection = table.getSelectionModel().getSelectedItem();
                if (selection != null) {
                    PressePapier.copier(entree.valeur().apply(selection));
                }
            });
            menu.getItems().add(item);
        }
        return menu;
    }
}

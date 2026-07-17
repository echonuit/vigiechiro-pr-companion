package fr.univ_amu.iut.commun.view;

import java.util.function.Consumer;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;

/// Fabrique des items de **menu de ligne** (clic droit) liés à la sélection d'une [TableView] : l'item est
/// désactivé tant qu'aucune ligne n'est sélectionnée, et son action reçoit la ligne sélectionnée.
///
/// Couplé à [DoubleClicLigne] (dont le clic droit sélectionne la ligne survolée), le menu contextuel cible
/// toujours la bonne ligne. Centralise le motif « action de ligne au clic droit » réutilisé par les vues
/// tabulaires (#1796), en miroir des boutons / du ☰ déjà présents.
public final class MenuLigne {

    private MenuLigne() {}

    /// Item `libelle` agissant sur la ligne **sélectionnée** de `table` ; désactivé sans sélection.
    public static <T> MenuItem item(String libelle, TableView<T> table, Consumer<T> action) {
        MenuItem item = new MenuItem(libelle);
        item.disableProperty()
                .bind(table.getSelectionModel().selectedItemProperty().isNull());
        item.setOnAction(evenement -> {
            T selection = table.getSelectionModel().getSelectedItem();
            if (selection != null) {
                action.accept(selection);
            }
        });
        return item;
    }
}

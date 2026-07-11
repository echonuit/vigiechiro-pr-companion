package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.view.TableSuivi;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.viewmodel.LigneDepot;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/// Configure la table de dépôt VigieChiro (#983) : une [LigneDepot] par unité téléversée. Délègue au
/// socle [TableSuivi] (colonnes `#`/Progression, cellule état/barre, coloration de la ligne selon
/// l'état) et n'ajoute que les colonnes propres au dépôt : le nom du fichier et sa nature. Encapsulé
/// hors du controller pour garder celui-ci en pur câblage.
final class TableSuiviDepot {

    private TableSuiviDepot() {}

    /// Pose colonnes, cellules et rangées colorées sur `table` (l'alimentation en items reste au controller).
    static void configurer(TableView<LigneDepot> table) {
        TableSuivi.configurer(table, "Aucun dépôt VigieChiro entamé pour l'instant.", colFichier(), colType());
    }

    private static TableColumn<LigneDepot, String> colFichier() {
        TableColumn<LigneDepot, String> col = new TableColumn<>("Fichier");
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().identifiant()));
        col.setPrefWidth(280);
        col.setSortable(false);
        return col;
    }

    private static TableColumn<LigneDepot, String> colType() {
        TableColumn<LigneDepot, String> col = new TableColumn<>("Type");
        col.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(libelle(c.getValue().type())));
        col.setPrefWidth(90);
        col.setSortable(false);
        return col;
    }

    private static String libelle(TypeDepotUnite type) {
        return type == TypeDepotUnite.ZIP ? "Archive" : "Séquence";
    }
}

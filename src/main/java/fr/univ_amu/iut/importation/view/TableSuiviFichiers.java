package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.commun.view.TableSuivi;
import fr.univ_amu.iut.importation.viewmodel.LigneFichierImport;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/// Configure la table de suivi de l'import (#947) : une [LigneFichierImport] par enregistrement
/// original. Délègue au socle [TableSuivi] (colonnes `#`/Progression, cellule état/barre, coloration de
/// la ligne selon l'état) et n'ajoute que les colonnes propres à l'import : le nom du fichier et
/// l'étape en cours (« Copie », « Transformation »). Encapsulé hors du controller pour garder celui-ci
/// en pur câblage.
final class TableSuiviFichiers {

    private TableSuiviFichiers() {}

    /// Pose colonnes, cellules et rangées colorées sur `table` (l'alimentation en items reste au controller).
    static void configurer(TableView<LigneFichierImport> table) {
        TableSuivi.configurer(
                table, "Le détail par fichier apparaîtra au lancement de l'import.", colFichier(), colEtape());
    }

    private static TableColumn<LigneFichierImport, String> colFichier() {
        TableColumn<LigneFichierImport, String> col = new TableColumn<>("Fichier");
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().nomFichier()));
        col.setPrefWidth(260);
        col.setSortable(false);
        return col;
    }

    /// Colonne « Étape » : ce que la ligne « en cours » est en train de faire (« Copie » puis
    /// « Transformation »), vide en attente comme une fois terminée. Suit l'étape **en place** (propriété
    /// observable) puisque les fichiers évoluent pendant qu'ils sont affichés.
    private static TableColumn<LigneFichierImport, String> colEtape() {
        TableColumn<LigneFichierImport, String> col = new TableColumn<>("Étape");
        col.setCellValueFactory(c -> c.getValue().etapeProperty());
        col.setPrefWidth(130);
        col.setSortable(false);
        return col;
    }
}

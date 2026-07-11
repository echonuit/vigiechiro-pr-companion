package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.TableSuivi;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.lot.viewmodel.LigneArchive;
import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/// Configure la table de suivi du dépôt (#820) : une [LigneArchive] par ZIP. Délègue au socle
/// [TableSuivi] (colonnes `#`/Progression, cellule état/barre, coloration de la ligne selon l'état) et
/// n'ajoute que les colonnes propres au dépôt : Fichiers et Taille. Encapsulé hors du controller pour
/// garder celui-ci en pur câblage.
final class TableSuiviArchives {

    private TableSuiviArchives() {}

    /// Pose colonnes, cellules et rangées colorées sur `table` (l'alimentation en items reste au controller),
    /// puis **décrit** les colonnes pour le sélecteur (#918) : l'identité `#` et « Progression » (l'état, cœur
    /// de la table de suivi) sont verrouillées ; « Fichiers » et « Taille » sont masquables. `#` et
    /// « Progression » sont posées par le socle [TableSuivi] (première et dernière colonnes), on les relit donc
    /// sur la table plutôt que de les reconstruire.
    static List<GestionnaireColonnes.Colonne> configurer(TableView<LigneArchive> table) {
        TableColumn<LigneArchive, Integer> fichiers = colFichiers();
        TableColumn<LigneArchive, String> taille = colTaille();
        TableSuivi.configurer(table, "Aucune archive de dépôt pour l'instant.", fichiers, taille);
        TableColumn<?, ?> numero = table.getColumns().get(0);
        TableColumn<?, ?> progression =
                table.getColumns().get(table.getColumns().size() - 1);
        return List.of(
                new GestionnaireColonnes.Colonne(numero, "#", true),
                new GestionnaireColonnes.Colonne(fichiers, "Fichiers", false),
                new GestionnaireColonnes.Colonne(taille, "Taille", false),
                new GestionnaireColonnes.Colonne(progression, "Progression", true));
    }

    private static TableColumn<LigneArchive, Integer> colFichiers() {
        TableColumn<LigneArchive, Integer> col = new TableColumn<>("Fichiers");
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().nombreFichiers()));
        col.setPrefWidth(90);
        col.setSortable(false);
        return col;
    }

    /// Colonne « Taille » : estimée (préfixée d'un `~`) tant que la ligne n'est pas terminée, réelle ensuite.
    /// La valeur suit la taille ET l'état de la ligne (elle passe d'estimée à réelle à la fin).
    private static TableColumn<LigneArchive, String> colTaille() {
        TableColumn<LigneArchive, String> col = new TableColumn<>("Taille");
        col.setCellValueFactory(c -> {
            LigneArchive l = c.getValue();
            return Bindings.createStringBinding(
                    () -> (l.tailleEstimee() ? "~ " : "")
                            + Formats.octetsLisibles(l.tailleOctetsProperty().get()),
                    l.tailleOctetsProperty(),
                    l.etatProperty());
        });
        col.setPrefWidth(110);
        col.setSortable(false);
        return col;
    }
}

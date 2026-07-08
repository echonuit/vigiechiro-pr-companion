package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.importation.viewmodel.NuitVM;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;

/// Construit la **table des nuits** de M-Import (une ligne [NuitVM] par nuit). Helper de vue extrait du
/// controller (comme [CarteRattachement]) pour ne pas y concentrer la configuration des colonnes : case
/// « inclure » **éditable**, date, nombre de fichiers, état (complète / incomplète + motif), n° de
/// passage **proposé** (réactif à la (dé)coche) et indicateur « déjà importée » (#147).
///
/// La table est bâtie **par programme** (colonnes non déclarées en FXML) : la configuration des cellules
/// (cell factories) est du code, mieux placé ici que dispersé entre FXML et controller.
final class TableNuits {

    private TableNuits() {}

    /// Hauteur d'une ligne (déterministe, pour dimensionner la table au nombre de nuits).
    private static final double HAUTEUR_LIGNE = 32.0;

    /// Marge fixe de la table : en-tête + bordures, ajoutée à la hauteur des lignes.
    private static final double HAUTEUR_ENTETE = 36.0;

    /// Au-delà de ce nombre de nuits, la table plafonne et un ascenseur interne apparaît.
    private static final int LIGNES_VISIBLES_MAX = 8;

    /// Table liée à la liste observable des nuits (`editable`, colonnes configurées). La table **suit le
    /// nombre de nuits** : sa hauteur (min **et** préférée) est liée au nombre de lignes. Le `minHeight`
    /// est indispensable ici — l'écran est dans un `ScrollPane` en `fitToHeight`, qui écraserait sinon la
    /// table à une seule ligne (comme la carte de rattachement se protège par son propre `minHeight`).
    static TableView<NuitVM> creer(ObservableList<NuitVM> nuits) {
        TableView<NuitVM> table = new TableView<>(nuits);
        table.setEditable(true);
        table.setFixedCellSize(HAUTEUR_LIGNE);
        var hauteur = Bindings.createDoubleBinding(
                () -> HAUTEUR_LIGNE * Math.min(Math.max(nuits.size(), 1), LIGNES_VISIBLES_MAX) + HAUTEUR_ENTETE, nuits);
        table.minHeightProperty().bind(hauteur);
        table.prefHeightProperty().bind(hauteur);
        table.getColumns().add(colonneInclure());
        table.getColumns().add(colonneTexte("Nuit du", 110, nuit -> nuit.date().toString()));
        table.getColumns().add(colonneTexte("Fichiers", 80, nuit -> Integer.toString(nuit.nombreFichiers())));
        table.getColumns().add(colonneTexte("État", 190, TableNuits::libelleComplete));
        table.getColumns().add(colonnePassage());
        table.getColumns().add(colonneDejaImportee());
        return table;
    }

    /// Colonne « Importer » : case à cocher **éditable** liée à `inclureProperty` (écriture bidirectionnelle).
    private static TableColumn<NuitVM, Boolean> colonneInclure() {
        TableColumn<NuitVM, Boolean> colonne = new TableColumn<>("Importer");
        colonne.setPrefWidth(80);
        colonne.setEditable(true);
        colonne.setCellValueFactory(cellule -> cellule.getValue().inclureProperty());
        colonne.setCellFactory(CheckBoxTableCell.forTableColumn(colonne));
        return colonne;
    }

    /// Colonne texte statique dérivée d'une nuit (date, nombre de fichiers, état).
    private static TableColumn<NuitVM, String> colonneTexte(
            String titre, double largeur, Function<NuitVM, String> valeur) {
        TableColumn<NuitVM, String> colonne = new TableColumn<>(titre);
        colonne.setPrefWidth(largeur);
        colonne.setCellValueFactory(cellule -> new ReadOnlyStringWrapper(valeur.apply(cellule.getValue())));
        return colonne;
    }

    /// Colonne « Passage n° » : **réactive** à la (dé)coche (l'auto-numérotation le recalcule), « — »
    /// pour une nuit exclue.
    private static TableColumn<NuitVM, String> colonnePassage() {
        TableColumn<NuitVM, String> colonne = new TableColumn<>("Passage n°");
        colonne.setPrefWidth(100);
        colonne.setCellValueFactory(cellule -> Bindings.createStringBinding(
                () -> cellule.getValue().numeroPassagePropose() > 0
                        ? Integer.toString(cellule.getValue().numeroPassagePropose())
                        : "—",
                cellule.getValue().numeroPassageProposeProperty()));
        return colonne;
    }

    /// Colonne « déjà importée » (#147) : badge non vide si un passage existe déjà pour cette nuit.
    private static TableColumn<NuitVM, String> colonneDejaImportee() {
        TableColumn<NuitVM, String> colonne = new TableColumn<>("");
        colonne.setPrefWidth(120);
        colonne.setCellValueFactory(cellule -> cellule.getValue().statutDejaImporteeProperty());
        return colonne;
    }

    /// Libellé d'état d'une nuit : « ✓ complète » ou « ⚠ incomplète (motif) ».
    private static String libelleComplete(NuitVM nuit) {
        return nuit.estComplete() ? "✓ complète" : "⚠ incomplète (" + nuit.motifIncompletude() + ")";
    }
}

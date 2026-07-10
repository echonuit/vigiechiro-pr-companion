package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.lot.viewmodel.EtatArchive;
import fr.univ_amu.iut.lot.viewmodel.LigneArchive;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

/// Configure la table de suivi du dépôt (#820) : une [LigneArchive] par ZIP, avec les colonnes
/// #/Fichiers/Taille/Progression, la cellule état/barre ([CelluleProgressionArchive]) et la **coloration de
/// la ligne** selon l'état (classes CSS `.ligne-archive.etat-…`, définies dans `lot.css`). Encapsulé hors
/// du controller pour garder celui-ci en pur câblage.
final class TableSuiviArchives {

    private TableSuiviArchives() {}

    /// Pose colonnes, cellules et rangées colorées sur `table` (l'alimentation en items reste au controller).
    static void configurer(TableView<LigneArchive> table) {
        table.getColumns().setAll(colNumero(), colFichiers(), colTaille(), colProgression());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setRowFactory(t -> ligneColoreeSelonEtat());
        table.setPlaceholder(new Label("Aucune archive de dépôt pour l'instant."));
    }

    private static TableColumn<LigneArchive, Integer> colNumero() {
        TableColumn<LigneArchive, Integer> col = new TableColumn<>("#");
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().numero()));
        col.setPrefWidth(48);
        col.setSortable(false);
        return col;
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

    private static TableColumn<LigneArchive, LigneArchive> colProgression() {
        TableColumn<LigneArchive, LigneArchive> col = new TableColumn<>("Progression");
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        col.setCellFactory(c -> new CelluleProgressionArchive());
        col.setPrefWidth(220);
        col.setSortable(false);
        return col;
    }

    /// Rangée dont la classe d'état (`.ligne-archive.etat-…`) suit **en place** l'état de sa ligne : la
    /// couleur de fond / des icônes change quand l'archive passe en cours, terminée ou en échec.
    private static TableRow<LigneArchive> ligneColoreeSelonEtat() {
        return new TableRow<>() {
            private final ChangeListener<EtatArchive> maj = (obs, avant, apres) -> appliquer(apres);

            @Override
            protected void updateItem(LigneArchive ligne, boolean vide) {
                LigneArchive ancien = getItem();
                if (ancien != null) {
                    ancien.etatProperty().removeListener(maj);
                }
                super.updateItem(ligne, vide);
                if (vide || ligne == null) {
                    appliquer(null);
                } else {
                    ligne.etatProperty().addListener(maj);
                    appliquer(ligne.etatProperty().get());
                }
            }

            private void appliquer(EtatArchive etat) {
                getStyleClass().removeIf(c -> c.equals("ligne-archive") || c.startsWith("etat-"));
                if (etat != null) {
                    getStyleClass().addAll("ligne-archive", classePour(etat));
                }
            }
        };
    }

    private static String classePour(EtatArchive etat) {
        return switch (etat) {
            case EN_ATTENTE -> "etat-attente";
            case EN_COURS -> "etat-cours";
            case TERMINEE -> "etat-terminee";
            case ECHEC -> "etat-echec";
        };
    }
}

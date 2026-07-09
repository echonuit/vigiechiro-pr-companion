package fr.univ_amu.iut.commun.view;

import javafx.scene.control.TableView;

/// Configurateur des **tables de données** (#690) : applique une densité et un habillage **uniformes** à
/// toute `TableView` d'un écran de données (fiche site, qualification, audio, multisite, analyse), pour
/// qu'elles partagent la même hauteur de ligne et le même style de tableau.
///
/// Choix « composant plutôt que convention » : plutôt que de compter sur l'ajout manuel d'une classe CSS
/// sur chaque `<TableView>` (facile à oublier ou à faire diverger — c'est l'origine de la dette), un
/// **appel unique** dans le controller garantit l'habillage partagé. La classe `table-donnees` porte, dans
/// `design.css`, la densité (hauteur de ligne unique via `-fx-cell-size`) et le style de tableau (en-tête,
/// cellules).
///
/// Volontairement **sans** `setFixedCellSize` : cette optimisation rend `getTableRow().getItem()`
/// transitoirement nul pendant la virtualisation, ce qui fait vaciller les cellules qui en dépendent
/// (icônes conditionnelles, badges). `-fx-cell-size` en CSS donne la même hauteur uniforme sans ce piège.
public final class TableDonnees {

    private TableDonnees() {}

    /// Applique l'habillage partagé des tables de données (classe `table-donnees`) à `table`.
    /// Idempotent : la classe n'est pas ajoutée deux fois.
    public static void uniformiser(TableView<?> table) {
        if (!table.getStyleClass().contains("table-donnees")) {
            table.getStyleClass().add("table-donnees");
        }
    }
}

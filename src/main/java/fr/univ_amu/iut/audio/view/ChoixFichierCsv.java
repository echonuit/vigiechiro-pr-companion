package fr.univ_amu.iut.audio.view;

import javafx.stage.FileChooser;

/// Fabrique un sélecteur de fichier **CSV** (import/export de la vue audio), pour centraliser les libellés
/// de filtre (« CSV » / « *.csv ») et alléger le [SonsValidationController] (pur câblage, seuil de cohésion
/// PMD). L'appelant choisit ensuite `showOpenDialog` (import) ou `showSaveDialog` (export).
final class ChoixFichierCsv {

    private ChoixFichierCsv() {}

    /// `FileChooser` titré et filtré sur les `.csv`. `nomInitial` (facultatif, `null` à l'ouverture) pré-remplit
    /// le nom de fichier proposé à l'enregistrement.
    static FileChooser selecteur(String titre, String nomInitial) {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle(titre);
        if (nomInitial != null) {
            selecteur.setInitialFileName(nomInitial);
        }
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        return selecteur;
    }
}

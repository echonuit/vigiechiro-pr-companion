package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import java.nio.file.Path;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/// Déclenche l'import d'un CSV Tadarida sur la vue audio : import direct s'il n'y a pas encore de jeu de
/// résultats, sinon **demande confirmation** du remplacement (un seul jeu par passage) avant de
/// réimporter. Isolé du controller (même patron que [DepotFichier]) pour le garder léger.
final class ImportTadarida {

    private ImportTadarida() {}

    /// Lance l'import de `cheminCsv` via le `viewModel` : import direct si aucun résultat, sinon réimport
    /// (remplacement) après confirmation de l'utilisateur.
    static void lancer(AudioViewModel viewModel, Path cheminCsv) {
        if (!viewModel.resultatsDisponiblesProperty().get()) {
            viewModel.importer(cheminCsv, false);
        } else if (confirmerRemplacement()) {
            viewModel.importer(cheminCsv, true);
        }
    }

    private static boolean confirmerRemplacement() {
        Alert alerte = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Des résultats Tadarida existent déjà pour ce passage. Les remplacer par ce nouvel import ?"
                        + " Les validations en cours sur ce passage seront perdues.",
                ButtonType.OK,
                ButtonType.CANCEL);
        alerte.setHeaderText("Réimporter les résultats ?");
        return alerte.showAndWait().filter(bouton -> bouton == ButtonType.OK).isPresent();
    }
}

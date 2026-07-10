package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.BilanImport;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;

/// Câblage et déclenchement de l'**import des résultats VigieChiro** (axe 4.2) sur la vue audio. Isolé du
/// [SonsValidationController] (même patron que [ImportTadarida] / `DepotFichier`) pour le garder léger
/// (contrainte NCSS) : le controller ne conserve que ses champs `@FXML` et deux délégations.
final class ImportVigieChiroUI {

    private ImportVigieChiroUI() {}

    /// Câble l'item de menu (libellé Importer / Réimporter, désactivé pendant un import) et le libellé de
    /// restitution (avancement / bilan / erreur), sur les propriétés des deux ViewModel.
    static void cabler(
            MenuItem item, Label message, ImportVigieChiroViewModel importVigieChiro, AudioViewModel viewModel) {
        item.textProperty()
                .bind(Bindings.when(viewModel.resultatsDisponiblesProperty())
                        .then("🔁 Réimporter depuis VigieChiro…")
                        .otherwise("☁ Importer depuis VigieChiro…"));
        item.disableProperty().bind(importVigieChiro.enCoursProperty());
        message.textProperty().bind(importVigieChiro.messageProperty());
        message.visibleProperty().bind(importVigieChiro.messageProperty().isNotEmpty());
        message.managedProperty().bind(importVigieChiro.messageProperty().isNotEmpty());
    }

    /// Lance l'import des résultats VigieChiro du passage de `source`, **hors fil JavaFX** : confirmation FX
    /// si un jeu existe déjà, puis récupération réseau + import sur un fil virtuel, application du bilan et
    /// **rafraîchissement** de la liste via `Platform.runLater`. Sans passage (source non ciblée), ne fait
    /// rien.
    static void lancer(
            ImportVigieChiroViewModel importVigieChiro, AudioViewModel viewModel, SourceObservations source) {
        ContextePassage contexte = source.contexteDuPassage();
        if (contexte == null) {
            return;
        }
        Long idPassage = contexte.idPassage();
        boolean remplacer = viewModel.resultatsDisponiblesProperty().get();
        if (remplacer && !confirmerRemplacement()) {
            return;
        }
        importVigieChiro.marquerEnCours();
        Thread.ofVirtual().name("import-vigiechiro").start(() -> {
            try {
                BilanImport bilan = importVigieChiro.importer(idPassage, remplacer);
                Platform.runLater(() -> {
                    importVigieChiro.appliquerBilan(bilan);
                    viewModel.ouvrirSur(source); // recharge la liste avec les observations importées
                });
            } catch (RuntimeException echec) {
                Platform.runLater(() -> importVigieChiro.echec(echec.getMessage()));
            }
        });
    }

    /// Confirme le **remplacement** d'un jeu de résultats existant avant un réimport (un seul jeu par
    /// passage ; les validations en cours seraient perdues).
    private static boolean confirmerRemplacement() {
        Alert alerte = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Des résultats Tadarida existent déjà pour ce passage. Les remplacer par ceux de VigieChiro ?"
                        + " Les validations en cours sur ce passage seront perdues.",
                ButtonType.OK,
                ButtonType.CANCEL);
        alerte.setHeaderText("Réimporter depuis VigieChiro ?");
        return alerte.showAndWait().filter(bouton -> bouton == ButtonType.OK).isPresent();
    }
}

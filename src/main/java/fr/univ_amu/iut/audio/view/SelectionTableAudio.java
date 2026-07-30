package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;

/// Tient la **sélection de la table** et celle du ViewModel alignées, dans les deux sens, et met à jour
/// les items « Fiche de l'espèce » qui en dépendent.
///
/// Câblage délégué hors de [SonsValidationController], comme `MenuCertitude` et `PanneauDiscussion` avant
/// lui : ce contrôleur est au plafond de `NcssCount`, et cette synchronisation forme un tout, les deux
/// écouteurs n'ont de sens que l'un par rapport à l'autre, la garde d'égalité de l'un empêchant la boucle
/// avec l'autre.
final class SelectionTableAudio {

    private SelectionTableAudio() {}

    /// Installe les deux sens de la synchronisation.
    ///
    /// **Table → ViewModel** : la ligne surlignée devient la sélection du modèle, et les items de fiche
    /// se recalent sur elle (#847, #1795).
    ///
    /// **ViewModel → table** : après un Valider/Corriger, `charger()` reconstruit la liste avec de
    /// nouvelles instances de record (statut ou taxon changés), ce qui vide la surbrillance alors que le
    /// ViewModel, lui, a restauré la sélection par identifiant. On réaligne donc la ligne surlignée sur
    /// la sélection du modèle. La **garde d'égalité** empêche la boucle avec le sens inverse : une
    /// sélection déjà alignée ne redéclenche rien.
    static void installer(
            TableView<LigneObservationAudio> table,
            AudioViewModel viewModel,
            ActionsMenuAudio actionsMenu,
            MenuItem itemFicheEspece) {
        table.getSelectionModel().selectedItemProperty().addListener((obs, ancienne, nouvelle) -> {
            viewModel.selectionProperty().set(nouvelle);
            actionsMenu.configurerFiches(itemFicheEspece, nouvelle);
        });
        // État initial des items « Fiche de l'espèce » avant toute sélection (désactivés, libellé explicatif).
        actionsMenu.configurerFiches(itemFicheEspece, table.getSelectionModel().getSelectedItem());
        viewModel.selectionProperty().addListener((obs, ancienne, nouvelle) -> {
            var modele = table.getSelectionModel();
            if (nouvelle == null) {
                modele.clearSelection();
            } else if (!nouvelle.equals(modele.getSelectedItem())) {
                modele.select(nouvelle);
            }
        });
    }
}

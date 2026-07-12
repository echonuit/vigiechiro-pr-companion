package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.Taxon;
import java.util.List;
import java.util.Objects;
import javafx.scene.control.TableView;

/// Aiguille les actions de revue (valider / corriger / basculer référence) selon la **sélection de la
/// table** (#479) : **une seule** ligne → action **unitaire** du [AudioViewModel] (respecte le mode
/// Inventaire, écoute la ligne courante) ; **plusieurs** lignes → action **en lot** (mode Activité,
/// transaction atomique). Un seul point de décision, partagé par les **boutons** de la barre d'actions et
/// les **raccourcis clavier** ([RevueClavier]).
final class ActionsSelectionAudio {

    private final TableView<LigneObservationAudio> table;
    private final AudioViewModel viewModel;

    ActionsSelectionAudio(TableView<LigneObservationAudio> table, AudioViewModel viewModel) {
        this.table = table;
        this.viewModel = viewModel;
    }

    void valider() {
        List<LigneObservationAudio> selection = selection();
        if (selection.size() <= 1) {
            viewModel.valider();
        } else {
            viewModel.actions().validerLot(ids(selection));
        }
    }

    void corriger(Taxon taxon) {
        List<LigneObservationAudio> selection = selection();
        if (selection.size() <= 1) {
            viewModel.corriger(taxon);
        } else {
            viewModel.actions().corrigerLot(ids(selection), taxon);
        }
    }

    void basculerReference() {
        List<LigneObservationAudio> selection = selection();
        if (selection.size() <= 1) {
            viewModel.basculerReference();
            return;
        }
        // Bascule homogène pour un lot mixte : dès qu'une ligne n'est pas en référence, on **marque** tout
        // le lot ; sinon (toutes en référence) on **retire** tout — plus prévisible qu'un toggle par ligne.
        boolean marquer = selection.stream().anyMatch(ligne -> !ligne.reference());
        viewModel.actions().marquerReferenceLot(ids(selection), marquer);
    }

    /// Bascule le drapeau **douteux** (#160) : **une** ligne → bascule unitaire ; **plusieurs** → bascule en
    /// lot homogène (pendant de [#basculerReference()]).
    void basculerDouteux() {
        List<LigneObservationAudio> selection = selection();
        if (selection.size() <= 1) {
            viewModel.basculerDouteux();
            return;
        }
        // Bascule homogène pour un lot mixte : dès qu'une ligne n'est pas douteuse, on **marque** tout le lot ;
        // sinon (toutes douteuses) on **retire** tout — plus prévisible qu'un toggle par ligne.
        boolean marquer = selection.stream().anyMatch(ligne -> !ligne.douteux());
        viewModel.actions().marquerDouteuxLot(ids(selection), marquer);
    }

    private List<LigneObservationAudio> selection() {
        return table.getSelectionModel().getSelectedItems();
    }

    private static List<Long> ids(List<LigneObservationAudio> lignes) {
        // On écarte les séquences non identifiées (sans observation) : une action en lot ne porte que sur
        // les lignes réellement validables.
        return lignes.stream()
                .map(LigneObservationAudio::idObservation)
                .filter(Objects::nonNull)
                .toList();
    }
}

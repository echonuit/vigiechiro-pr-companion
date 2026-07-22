package fr.univ_amu.iut.audio.view;

import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/// Câblage du **panneau d'écoute** de la vue audio, regroupé pour alléger le [SonsValidationController]
/// (pur câblage, seuil de cohésion PMD). Assemble les collaborateurs de l'`AudioView` :
///
/// - [ConfigurationAudioView] : normalisations, expansion ×10, source liée à la sélection, `dispose` ;
/// - [RepereCriAudio] (#482) : surlignage + seek du cri sélectionné ;
/// - [MetriquesAcoustiquesAudio] (#500) : FME / fréquence terminale calculées à la sélection ;
/// - [LecteurAudio] (#483) : options de lecture (auto-lecture + boucle) dans le menu ☰.
final class PanneauEcouteAudio {

    private PanneauEcouteAudio() {}

    static void installer(
            AudioView audioView,
            AudioViewModel viewModel,
            TableView<LigneObservationAudio> table,
            TableColumn<LigneObservationAudio, String> colFme,
            TableColumn<LigneObservationAudio, String> colFreqTerminale,
            MenuButton menuActions,
            ReglagesReactifs reactifs) {
        ConfigurationAudioView.installer(audioView, viewModel.etatEcoute().cheminAudioCourantProperty(), reactifs);
        RepereCriAudio.installer(audioView, viewModel.selectionProperty());
        MetriquesAcoustiquesAudio.installer(audioView, viewModel.selectionProperty(), table, colFme, colFreqTerminale);
        LecteurAudio.installer(audioView, menuActions, reactifs);
    }
}

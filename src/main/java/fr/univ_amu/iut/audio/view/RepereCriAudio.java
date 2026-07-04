package fr.univ_amu.iut.audio.view;

import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import javafx.beans.value.ObservableValue;

/// Repérage du cri sélectionné sur l'[AudioView] (#482) : surligne la fenêtre `[début, fin]` du cri sur
/// l'onde ET le spectrogramme (emphase) et positionne la lecture à son début (seek).
///
/// Logique sortie du `SonsValidationController` (pur câblage) pour le garder sous les seuils de cohésion
/// PMD (NcssCount / GodClass), comme `CellulesAudio`.
///
/// **Convention de temps.** Les bornes `debutS`/`finS` (temps Tadarida) sont **déjà en secondes réelles**
/// au sein de la tranche de 5 s. Les API temporelles de l'AudioView (`seek`, `highlightWindow`) attendent
/// aussi des **secondes réelles** (l'axe est réel grâce à `setTimeExpansionFactor`). On passe donc les
/// bornes **telles quelles**, sans conversion. [#FACTEUR_EXPANSION_TEMPS] ne sert plus qu'à configurer
/// l'AudioView (le fichier joué est, lui, ralenti ×10).
final class RepereCriAudio {

    /// Facteur d'expansion temporelle ×10 du fichier d'écoute, passé à `AudioView.setTimeExpansionFactor`
    /// pour que ses axes affichent les grandeurs réelles. **Ne sert pas** à convertir les temps du cri
    /// (déjà réels).
    static final double FACTEUR_EXPANSION_TEMPS = 10;

    private RepereCriAudio() {}

    /// Câble le repérage : à chaque changement de sélection, et à chaque fois que l'AudioView devient
    /// « prêt » (nouveau clip décodé), on réapplique le surlignage + seek de la sélection courante. La
    /// double application est nécessaire car le seek n'agit qu'une fois le clip chargé.
    static void installer(AudioView audioView, ObservableValue<LigneObservationAudio> selection) {
        selection.addListener((obs, ancienne, nouvelle) -> appliquer(audioView, nouvelle));
        audioView.readyProperty().addListener((obs, avant, pret) -> {
            if (pret) {
                appliquer(audioView, selection.getValue());
            }
        });
    }

    /// Surligne la fenêtre du cri et y positionne la lecture. Sans observation ou sans bornes temporelles,
    /// efface le surlignage. Les bornes (secondes réelles) sont passées **telles quelles** à l'AudioView.
    /// Le seek n'est tenté que si le clip est chargé ([AudioView#isReady]) — sinon il serait borné à zéro ;
    /// il est rejoué au passage à « prêt ».
    static void appliquer(AudioView audioView, LigneObservationAudio observation) {
        if (observation == null || observation.debutS() == null || observation.finS() == null) {
            audioView.clearHighlight();
            return;
        }
        double debut = observation.debutS();
        double fin = observation.finS();
        audioView.highlightWindow(debut, fin);
        if (audioView.isReady()) {
            audioView.seek(debut);
        }
    }
}

package fr.univ_amu.iut.audio.view;

import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.viewmodel.OngletReglagesAudio;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import java.nio.file.Path;
import javafx.beans.value.ObservableValue;

/// Configuration de base de l'[AudioView] de la vue audio (E7.S3), sortie du [SonsValidationController]
/// (pur câblage, seuil de cohésion PMD) :
///
/// - **trois normalisations** complémentaires : le NIVEAU à la lecture (#109) pour égaliser le volume d'un
///   cri à l'autre, et les deux VISUELLES (audio-view 1.14) pour les cris faibles — l'onde du sonogramme
///   remplit la gouttière au lieu de rester plate, la fenêtre dB du spectrogramme se recale sur le pic ;
/// - **expansion temporelle ×10** du protocole Vigie-Chiro (les séquences transformées sont les originaux
///   ralentis ×10) : réglée pour que les axes affichent les grandeurs RÉELLES (fréquences × 10, temps ÷ 10) ;
/// - la **source** suit l'observation sélectionnée (`audioFileProperty` liée au chemin courant) ;
/// - le **clip est libéré** ([AudioView#dispose]) quand la vue quitte la scène.
final class ConfigurationAudioView {

    private ConfigurationAudioView() {}

    /// Applique la configuration ci-dessus à `audioView`, la source suivant `cheminAudio`. Le mode
    /// **daltonien** du spectrogramme suit le réglage persistant (#1006), donc l'onglet « Audio » de
    /// l'écran Réglages : effet immédiat.
    static void installer(AudioView audioView, ObservableValue<? extends Path> cheminAudio, ReglagesReactifs reactifs) {
        audioView.setNormalisation(true);
        audioView.setWaveNormalisation(true);
        audioView.setSpectrogramNormalisation(true);
        audioView.setTimeExpansionFactor(RepereCriAudio.FACTEUR_EXPANSION_TEMPS);
        audioView
                .colorblindFriendlyProperty()
                .bind(reactifs.proprieteBooleen(
                        OngletReglagesAudio.CLE_DALTONIEN, OngletReglagesAudio.DEFAUT_DALTONIEN));
        audioView.audioFileProperty().bind(cheminAudio);
        audioView.sceneProperty().addListener((obs, avant, scene) -> {
            if (scene == null) {
                audioView.dispose();
            }
        });
    }
}

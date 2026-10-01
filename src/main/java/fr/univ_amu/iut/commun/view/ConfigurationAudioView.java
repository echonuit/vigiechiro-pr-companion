package fr.univ_amu.iut.commun.view;

import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.commun.viewmodel.ReglageDaltonien;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import java.nio.file.Path;
import javafx.beans.value.ObservableValue;

/// Configuration de base de l'[AudioView], la même sur tout écran qui affiche un son (E7.S3) :
///
/// - **trois normalisations** complémentaires : le NIVEAU à la lecture (#109) pour égaliser le volume d'un
///   cri à l'autre, et les deux VISUELLES (audio-view 1.14) pour les cris faibles : l'onde du sonogramme
///   remplit la gouttière au lieu de rester plate, la fenêtre dB du spectrogramme se recale sur le pic ;
/// - **expansion temporelle ×10** du protocole Vigie-Chiro (les séquences transformées sont les originaux
///   ralentis ×10) : réglée pour que les axes affichent les grandeurs RÉELLES (fréquences × 10, temps ÷ 10) ;
/// - la **source** suit l'observation sélectionnée (`audioFileProperty` liée au chemin courant) ;
/// - le **clip est libéré** ([AudioView#dispose]) quand la vue quitte la scène.
///
/// Sortie de la fonctionnalité audio pour que la Qualification la reçoive aussi (#5603) : configurée à
/// la main, elle n'avait ni les normalisations visuelles ni le réglage daltonien, et son spectrogramme
/// était moins lisible que celui de Sons & validation sur le même son.
public final class ConfigurationAudioView {

    /// Facteur d'expansion temporelle ×10 du protocole Vigie-Chiro : les séquences transformées sont les
    /// originaux ralentis ×10, et les axes doivent afficher les grandeurs **réelles** (fréquences × 10).
    public static final double FACTEUR_EXPANSION_TEMPS = 10;

    private ConfigurationAudioView() {}

    /// Applique la configuration ci-dessus à `audioView`, la source suivant `cheminAudio`. Le mode
    /// **daltonien** du spectrogramme suit le réglage persistant (#1006), donc l'onglet « Audio » de
    /// l'écran Réglages : effet immédiat.
    public static void installer(
            AudioView audioView, ObservableValue<? extends Path> cheminAudio, ReglagesReactifs reactifs) {
        audioView.setNormalisation(true);
        audioView.setWaveNormalisation(true);
        audioView.setSpectrogramNormalisation(true);
        audioView.setTimeExpansionFactor(FACTEUR_EXPANSION_TEMPS);
        audioView
                .colorblindFriendlyProperty()
                .bind(reactifs.proprieteBooleen(ReglageDaltonien.CLE, ReglageDaltonien.DEFAUT));
        audioView.audioFileProperty().bind(cheminAudio);
        audioView.sceneProperty().addListener((obs, avant, scene) -> {
            if (scene == null) {
                audioView.dispose();
            }
        });
    }
}

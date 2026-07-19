package fr.univ_amu.iut.audio.view;

import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.viewmodel.OngletReglagesAudio;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import java.util.List;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.SeparatorMenuItem;
import org.kordamp.ikonli.javafx.FontIcon;

/// Options de **lecture** de la vue audio (#483), ajoutées en tête du menu « ☰ » et câblées sur l'[AudioView] :
///
/// - **Lecture automatique à la sélection** (activée par défaut, désactivable) : à chaque clip **prêt**
///   (`readyProperty` passe à vrai après le chargement de la séquence sélectionnée), la lecture démarre.
/// - **Lecture en boucle** : la boucle de l'`AudioView` suit la case.
///
/// Ces deux options sont désormais **persistées** et **centralisées** : liées à la même Property
/// réactive ([ReglagesReactifs]) que l'onglet « Audio » de l'écran Réglages
/// ([fr.univ_amu.iut.audio.viewmodel.OngletReglagesAudio], #1006), elles survivent d'une session à
/// l'autre et restent synchronisées entre le menu et l'onglet.
///
/// Créées **programmatiquement** (comme le « Colonnes… » du [GestionnaireColonnes]) plutôt qu'en `@FXML` :
/// concerne toutes les sources (pas de visibilité conditionnelle) et garde le controller (pur câblage) sous
/// son seuil de cohésion PMD. Sorti du controller comme [RepereCriAudio] / [MetriquesAcoustiquesAudio].
final class LecteurAudio {

    private LecteurAudio() {}

    /// Ajoute les deux options en **fin** du menu `menu`, après un séparateur (ce sont des **réglages** de
    /// lecture, regroupés en bas comme « Colonnes… », les actions d'import/export restant en tête), et les
    /// branche sur `audioView` : boucle liée à sa case ; auto-lecture déclenchée à chaque clip prêt si sa
    /// case est cochée.
    static void installer(AudioView audioView, MenuButton menu, ReglagesReactifs reactifs) {
        CheckMenuItem lectureAuto = new CheckMenuItem("Lecture automatique à la sélection");
        lectureAuto.setGraphic(new FontIcon("fas-volume-up"));
        lectureAuto
                .selectedProperty()
                .bindBidirectional(reactifs.proprieteBooleen(
                        OngletReglagesAudio.CLE_LECTURE_AUTO, OngletReglagesAudio.DEFAUT_LECTURE_AUTO));
        CheckMenuItem boucle = new CheckMenuItem("Lecture en boucle");
        boucle.setGraphic(new FontIcon("fas-redo"));
        boucle.selectedProperty()
                .bindBidirectional(
                        reactifs.proprieteBooleen(OngletReglagesAudio.CLE_BOUCLE, OngletReglagesAudio.DEFAUT_BOUCLE));

        audioView.loopProperty().bind(boucle.selectedProperty());
        audioView.readyProperty().addListener((obs, avant, pret) -> {
            if (Boolean.TRUE.equals(pret) && lectureAuto.isSelected()) {
                audioView.setPlaying(true);
            }
        });

        menu.getItems().addAll(List.of(new SeparatorMenuItem(), lectureAuto, boucle));
    }
}

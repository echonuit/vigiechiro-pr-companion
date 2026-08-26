package fr.univ_amu.iut.commun.view;

import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;

/// Les boutons des dialogues, **en français** (#4229).
///
/// ## Pourquoi ils ne peuvent pas rester standards
///
/// `ButtonType.OK` et `ButtonType.CANCEL` portent un libellé que **JavaFX traduit depuis la locale de
/// la machine**, pas depuis l'application. Tout le reste de cette interface est en français écrit en
/// dur : le produit était donc français partout, sauf sur les boutons de ses dialogues, qui suivaient
/// le poste. Un utilisateur français sur un système en anglais lisait « Se déconnecter effacera le
/// jeton… » et cliquait « Cancel ».
///
/// Vu sur un clip de recette, tourné sur un runner en anglais : le film montrait un produit que
/// personne n'utilise.
///
/// Le `ButtonData` est **conservé** : c'est lui, et non le libellé, qui dit à JavaFX lequel est le
/// bouton par défaut, lequel ferme la fenêtre sur Échap, et dans quel ordre les poser selon la
/// plateforme. Changer le texte sans garder la donnée casserait ces trois comportements en silence.
public final class BoutonsDeDialogue {

    /// Le geste que le dialogue propose d'accomplir.
    public static final ButtonType CONFIRMER = new ButtonType("Confirmer", ButtonData.OK_DONE);

    /// Y renoncer.
    public static final ButtonType ANNULER = new ButtonType("Annuler", ButtonData.CANCEL_CLOSE);

    /// Refermer un dialogue qui ne demande rien - une information, un compte rendu.
    public static final ButtonType FERMER = new ButtonType("Fermer", ButtonData.OK_DONE);

    private BoutonsDeDialogue() {}
}

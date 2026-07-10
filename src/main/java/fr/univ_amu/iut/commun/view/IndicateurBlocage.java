package fr.univ_amu.iut.commun.view;

import javafx.beans.value.ObservableStringValue;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;

/// Rend « parlant » un contrôle qui se désactive selon l'état. Un [javafx.scene.control.Button],
/// [javafx.scene.control.CheckBox] ou [javafx.scene.control.Hyperlink] **désactivé** n'affiche aucun
/// [Tooltip] : il ne reçoit plus les événements de survol. On installe donc le tooltip sur une
/// **enveloppe** non désactivée, typiquement un [StackPane] qui entoure le contrôle et, lui, reçoit le
/// survol. Le texte du tooltip explique alors *pourquoi* le contrôle est bloqué et *comment* le
/// réactiver (#789).
///
/// Ce composant généralise le patron `enveloppeSupprimer` d'origine de la fiche site : plutôt que de
/// recopier le trio « StackPane + Tooltip + install » sur chaque écran, on l'expose ici.
///
/// Deux façons de l'utiliser :
/// - l'enveloppe est déjà déclarée dans le FXML (un `StackPane` autour du contrôle) → [#expliquer] ;
/// - le contrôle est bâti en code (hors FXML) → [#enrober], qui crée l'enveloppe et la renvoie.
public final class IndicateurBlocage {

    private IndicateurBlocage() {}

    /// Installe sur `enveloppe` un tooltip dont le texte **suit** `texte`, typiquement un binding
    /// `Bindings.when(possible).then("ce que fait l'action").otherwise("cause du blocage + remède")`.
    /// `enveloppe` doit être un conteneur **non désactivé** entourant le contrôle qui, lui, se désactive.
    public static void expliquer(Node enveloppe, ObservableStringValue texte) {
        Tooltip info = new Tooltip();
        info.textProperty().bind(texte);
        Tooltip.install(enveloppe, info);
    }

    /// Variante à texte fixe : le motif ne dépend d'aucune propriété observable (ex. carte reconstruite
    /// à chaque rafraîchissement, où l'état de blocage est figé au moment de la construction).
    public static void expliquer(Node enveloppe, String texte) {
        Tooltip.install(enveloppe, new Tooltip(texte));
    }

    /// Enrobe `controle` dans un [StackPane] porteur du tooltip (cf. [#expliquer]) et renvoie cette
    /// enveloppe, à insérer dans la scène **à la place** de `controle`. Pour les contrôles bâtis en code.
    public static StackPane enrober(Node controle, ObservableStringValue texte) {
        StackPane enveloppe = new StackPane(controle);
        expliquer(enveloppe, texte);
        return enveloppe;
    }

    /// Variante à texte fixe de [#enrober].
    public static StackPane enrober(Node controle, String texte) {
        StackPane enveloppe = new StackPane(controle);
        expliquer(enveloppe, texte);
        return enveloppe;
    }
}

package fr.univ_amu.iut.sites.view;

import fr.univ_amu.iut.sites.viewmodel.StatutPlateforme;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

/// Fabrique du **badge de statut plateforme** (#734), unique pour les deux écrans qui l'affichent : la
/// carte de « Mes sites » et le détail de site.
///
/// Le badge ne décide de rien : il rend ce que l'état [StatutPlateforme] dit de lui-même (libellé, famille
/// de couleur, infobulle). C'est ce que demandait #734 : *« utiliser le badge de statut unifié de l'app
/// plutôt qu'un style ad hoc »*, et c'est ce qui garantit qu'un carré verrouillé se lit pareil des deux
/// côtés.
final class BadgeStatutPlateforme {

    private BadgeStatutPlateforme() {}

    /// Badge de `statut`, infobulle comprise : elle porte la règle (*un site doit être verrouillé pour
    /// qu'on puisse y déposer*), qu'aucun libellé ne suffit à faire deviner (#801).
    ///
    /// L'infobulle est posée par `setTooltip` et non par `Tooltip.install` : un [Label] est un `Control`,
    /// il a donc une **propriété** `tooltip`, que la seconde forme (réservée aux nœuds quelconques) laisse
    /// vide : l'infobulle s'affichait, mais restait invisible du code, donc intestable.
    static Label creer(StatutPlateforme statut) {
        Label badge = new Label(statut.libelle());
        badge.getStyleClass().addAll("badge", statut.classeBadge());
        badge.setTooltip(new Tooltip(statut.infobulle()));
        return badge;
    }
}

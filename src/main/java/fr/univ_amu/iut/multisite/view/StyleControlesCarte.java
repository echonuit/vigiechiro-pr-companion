package fr.univ_amu.iut.multisite.view;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Tooltip;

/// Stylage des **contrôles superposés à la carte** de M-Multisite : les boutons d'overlay (recadrer,
/// éditer/enregistrer les positions) et les **poignées** de repli des panneaux. Sorti du
/// [MultisiteController] (fonctions de présentation pures, sans état) pour le garder sous le seuil de
/// taille (PMD `NcssCount`).
final class StyleControlesCarte {

    private StyleControlesCarte() {}

    /// Applique la classe CSS `classe` à un bouton d'overlay et renseigne son libellé accessible + son
    /// infobulle avec `description`.
    static void overlay(ButtonBase bouton, String classe, String description) {
        bouton.getStyleClass().add(classe);
        bouton.setAccessibleText(description);
        bouton.setTooltip(new Tooltip(description));
    }

    /// Configure une poignée de repli : libellé visible, libellé accessible + infobulle, et état activé.
    static void poignee(Button poignee, String libelle, String description, boolean actif) {
        poignee.setText(libelle);
        poignee.setAccessibleText(description);
        poignee.setTooltip(new Tooltip(description));
        poignee.setDisable(!actif);
    }
}

package fr.univ_amu.iut.qualification.view;

import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import java.util.Locale;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

/// Rendu des trois « feux » du pré-check (R13) de M-Qualification. En plus de la couleur (classe CSS
/// `feu-vert` / `feu-orange` / `feu-rouge`), chaque feu porte un **pictogramme** et une **infobulle** :
/// l'état ne doit pas être encodé par la seule teinte (lisibilité daltoniens, #801). L'infobulle
/// **explique** désormais la mesure et l'écart (#1506) : son texte vient du ViewModel et est suivi en
/// direct, au lieu du « conforme / à surveiller / anomalie » générique d'avant. Extrait de
/// [QualificationController] pour garder le contrôleur sous le plafond de taille (PMD `NcssCount`).
final class Feux {

    private Feux() {}

    /// Lie un feu à sa couleur et à son explication observables : applique l'état courant puis à
    /// chaque changement. L'infobulle est liée au `detail` (mesure + écart, #1506) ; elle disparaît
    /// tant que le pré-check n'est pas calculé (couleur `null`).
    static void lier(
            Label feu, String libelle, ReadOnlyObjectProperty<PreCheckNuit.Feu> couleur, ObservableStringValue detail) {
        Tooltip infobulle = new Tooltip();
        infobulle.textProperty().bind(detail);
        appliquer(feu, libelle, couleur.get(), infobulle);
        couleur.addListener((obs, ancien, nouveau) -> appliquer(feu, libelle, nouveau, infobulle));
    }

    private static void appliquer(Label feu, String libelle, PreCheckNuit.Feu valeur, Tooltip infobulle) {
        feu.getStyleClass().removeAll("feu-vert", "feu-orange", "feu-rouge");
        if (valeur == null) {
            // Pré-check non calculé : ni couleur, ni pictogramme, ni infobulle (état inconnu).
            feu.setText(libelle);
            feu.setTooltip(null);
            return;
        }
        feu.setText(picto(valeur) + " " + libelle);
        feu.getStyleClass().add("feu-" + valeur.name().toLowerCase(Locale.ROOT));
        feu.setTooltip(infobulle);
    }

    private static String picto(PreCheckNuit.Feu valeur) {
        return switch (valeur) {
            case VERT -> "✓";
            case ORANGE -> "⚠";
            case ROUGE -> "✖";
        };
    }
}

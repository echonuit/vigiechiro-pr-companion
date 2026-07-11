package fr.univ_amu.iut.qualification.view;

import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import java.util.Locale;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

/// Rendu des trois « feux » du pré-check (R13) de M-Qualification. En plus de la couleur (classe CSS
/// `feu-vert` / `feu-orange` / `feu-rouge`), chaque feu porte un **pictogramme** et une **infobulle** :
/// l'état ne doit pas être encodé par la seule teinte (lisibilité daltoniens, #801). Extrait de
/// [QualificationController] pour garder le contrôleur sous le plafond de taille (PMD `NcssCount`).
final class Feux {

    private Feux() {}

    /// Lie un feu à sa couleur observable : applique l'état courant puis à chaque changement.
    static void lier(Label feu, String libelle, ReadOnlyObjectProperty<PreCheckNuit.Feu> couleur) {
        appliquer(feu, libelle, couleur.get());
        couleur.addListener((obs, ancien, nouveau) -> appliquer(feu, libelle, nouveau));
    }

    private static void appliquer(Label feu, String libelle, PreCheckNuit.Feu valeur) {
        feu.getStyleClass().removeAll("feu-vert", "feu-orange", "feu-rouge");
        if (valeur == null) {
            // Pré-check non calculé : ni couleur, ni pictogramme, ni infobulle (état inconnu).
            feu.setText(libelle);
            feu.setTooltip(null);
            return;
        }
        feu.setText(picto(valeur) + " " + libelle);
        feu.getStyleClass().add("feu-" + valeur.name().toLowerCase(Locale.ROOT));
        feu.setTooltip(new Tooltip(libelle + " : " + etat(valeur)));
    }

    private static String picto(PreCheckNuit.Feu valeur) {
        return switch (valeur) {
            case VERT -> "✓";
            case ORANGE -> "⚠";
            case ROUGE -> "✖";
        };
    }

    private static String etat(PreCheckNuit.Feu valeur) {
        return switch (valeur) {
            case VERT -> "conforme";
            case ORANGE -> "à surveiller (léger écart au protocole)";
            case ROUGE -> "anomalie détectée";
        };
    }
}

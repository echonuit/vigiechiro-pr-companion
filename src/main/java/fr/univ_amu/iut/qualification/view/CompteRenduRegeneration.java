package fr.univ_amu.iut.qualification.view;

import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.Notificateur;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;

/// Compte rendu de la **régénération de la sélection d'écoute** (#1509/#1404). Sans retour, on ne
/// savait pas si l'action avait eu lieu (sur une nuit courte, la sélection régénérée est
/// identique). Tenu hors du contrôleur, à la façon de [Feux].
final class CompteRenduRegeneration {

    private CompteRenduRegeneration() {}

    /// Rend compte de la régénération, **au succès seulement** : l'échec est déjà affiché par le
    /// libellé d'erreur de la sélection (lié à `messageProperty`, #795), on ne le redouble pas.
    static void rendreCompte(Notificateur notificateur, SelectionEcouteViewModel selection) {
        String erreur = selection.messageProperty().get();
        if (erreur != null && !erreur.isBlank()) {
            return;
        }
        int nombre = selection.lignes().size();
        String detail = nombre + (nombre > 1 ? " séquences " : " séquence ")
                + descriptionMethode(selection.methodeProperty().get()) + ".";
        notificateur.notifier(NiveauNotification.INFORMATION, "Sélection régénérée", detail);
    }

    /// Décrit la méthode de constitution sans mentir : « réparties sur la nuit » ne vaut que pour la
    /// répartition temporelle.
    private static String descriptionMethode(MethodeSelection methode) {
        return switch (methode) {
            case REPARTITION_TEMPORELLE -> "réparties sur la nuit";
            case ALEATOIRE -> "tirées au hasard";
            case MANUEL -> "choisies manuellement";
            case RECUE_D_UN_PAQUET -> "reçues d'un paquet, et figées";
        };
    }
}

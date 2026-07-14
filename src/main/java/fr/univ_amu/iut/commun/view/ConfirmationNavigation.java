package fr.univ_amu.iut.commun.view;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

/// Confirmation par défaut (vrai `Alert` modal), factorisant le patron
/// `Alert(CONFIRMATION, …, OK, CANCEL)` jusque-là dupliqué dans les controllers. Implémente
/// [Confirmateur] ; le [Navigateur] et [ConfirmateurModifiable] l'utilisent par défaut, les tests
/// injectent un stub. Un **titre** optionnel personnalise la barre du dialogue (ex. « Supprimer les
/// archives de dépôt ? ») quand le message seul manquerait de contexte.
public final class ConfirmationNavigation implements Confirmateur {

    private final String titre;

    /// Dialogue au titre système par défaut.
    public ConfirmationNavigation() {
        this(null);
    }

    /// Dialogue au **titre** personnalisé (`null` = titre système), sans en-tête.
    public ConfirmationNavigation(String titre) {
        this.titre = titre;
    }

    @Override
    public boolean confirmer(String message) {
        return dialogue(message)
                .showAndWait()
                .filter(bouton -> bouton == ButtonType.OK)
                .isPresent();
    }

    /// Le dialogue **tel qu'il sera montré**, sans le montrer (#1468).
    ///
    /// Il existe pour les **captures de documentation**. Celles-ci reconstruisaient jusqu'ici les
    /// confirmations **à la main** (`CaptureDialogues`), faute d'un moyen d'obtenir le vrai dialogue sans
    /// bloquer sur `showAndWait`. Et « à l'identique » n'engageait personne : la documentation a montré des
    /// dialogues qui **avaient dérivé** du produit - jusqu'à une confirmation entière qui manquait.
    ///
    /// Désormais la capture et la production passent par **ce même code** : ce que la doc montre est ce que
    /// l'utilisateur verra.
    public Alert dialogue(String message) {
        Alert alerte = new Alert(AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        if (titre != null) {
            alerte.setTitle(titre);
            alerte.setHeaderText(null);
        }
        return alerte;
    }
}

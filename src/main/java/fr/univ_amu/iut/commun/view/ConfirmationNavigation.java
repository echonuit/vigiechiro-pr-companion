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
        Alert alerte = new Alert(AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        if (titre != null) {
            alerte.setTitle(titre);
            alerte.setHeaderText(null);
        }
        return alerte.showAndWait().filter(bouton -> bouton == ButtonType.OK).isPresent();
    }
}

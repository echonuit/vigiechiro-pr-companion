package fr.univ_amu.iut.commun.view;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

/// Confirmation de navigation par défaut (vrai `Alert` modal), factorisant le patron
/// `Alert(CONFIRMATION, …, OK, CANCEL)` jusque-là dupliqué dans les controllers. Implémente
/// [ConfirmateurQuitter] ; le [Navigateur] l'utilise par défaut, les tests injectent un stub.
public final class ConfirmationNavigation implements ConfirmateurQuitter {

    @Override
    public boolean confirmer(String message) {
        Alert alerte = new Alert(AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        return alerte.showAndWait().filter(bouton -> bouton == ButtonType.OK).isPresent();
    }
}

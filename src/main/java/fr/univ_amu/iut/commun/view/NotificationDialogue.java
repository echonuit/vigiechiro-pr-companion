package fr.univ_amu.iut.commun.view;

import java.util.Objects;
import java.util.function.Supplier;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/// Implémentation **réelle** de [Notificateur] : la boîte de dialogue modale de JavaFX. Jumelle de
/// [ConfirmationNavigation], et seul endroit du socle qui construise un `Alert` de compte rendu -
/// chaque action en fabriquait le sien, avec un titre ici, un en-tête là.
public final class NotificationDialogue implements Notificateur {

    /// Fenêtre propriétaire du dialogue, évaluée **au moment de notifier** (l'écran peut ne pas encore
    /// être attaché à une fenêtre quand l'action est construite). Peut rendre `null` : le dialogue
    /// s'affiche alors sans propriétaire, ce que JavaFX accepte.
    private final Supplier<Window> fenetre;

    /// Dialogue sans propriétaire déclaré.
    public NotificationDialogue() {
        this(() -> null);
    }

    public NotificationDialogue(Supplier<Window> fenetre) {
        this.fenetre = Objects.requireNonNull(fenetre, "fenetre");
    }

    @Override
    public void notifier(NiveauNotification niveau, String entete, String message) {
        Alert alerte = new Alert(type(niveau), message, ButtonType.OK);
        alerte.setHeaderText(entete);
        alerte.initOwner(fenetre.get());
        alerte.showAndWait();
    }

    private static AlertType type(NiveauNotification niveau) {
        return switch (niveau) {
            case INFORMATION -> AlertType.INFORMATION;
            case AVERTISSEMENT -> AlertType.WARNING;
        };
    }
}

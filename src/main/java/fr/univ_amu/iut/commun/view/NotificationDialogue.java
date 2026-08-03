package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import java.util.List;
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
        Alert alerte = dialogue(niveau, entete, message);
        alerte.initOwner(fenetre.get());
        alerte.showAndWait();
    }

    /// Le dialogue **sans l'ouvrir** (#3148).
    ///
    /// Deux choses en découlent, et la seconde est la vraie raison. D'abord l'aperçu documentaire peut
    /// montrer le compte rendu de la **production** au lieu d'une reconstitution (ADR 0025). Ensuite,
    /// et surtout, le choix du **type** de dialogue devient observable : cet adaptateur décide qu'un
    /// avertissement se distingue d'une information, et rien ne le vérifiait, `showAndWait` figeant
    /// tout test headless qui aurait voulu regarder.
    public Alert dialogue(NiveauNotification niveau, String entete, String message) {
        Alert alerte = new Alert(type(niveau), message, ButtonType.OK);
        alerte.setHeaderText(entete);
        return alerte;
    }

    /// Rend la bande chiffrée **dans** le dialogue, à la place de son texte.
    ///
    /// Les feuilles de style sont posées à la main sur le `DialogPane`. Un `Alert` porte sa propre scène,
    /// vierge de celles de l'application : sans ce geste, les classes `cr-*` du panneau ne s'appliquent
    /// pas et la bande perd ses teintes de sévérité comme ses marges - elle s'affiche, en gris, alignée
    /// n'importe comment.
    @Override
    public void notifier(NiveauNotification niveau, String entete, CompteRenduChiffre compteRendu) {
        PanneauCompteRendu bande = new PanneauCompteRendu();
        bande.afficher(compteRendu);
        Alert alerte = new Alert(type(niveau), "", ButtonType.OK);
        alerte.setHeaderText(entete);
        alerte.getDialogPane().setContent(bande);
        for (String feuille : List.of("palette.css", "design.css")) {
            var url = PanneauCompteRendu.class.getResource(feuille);
            if (url != null) {
                alerte.getDialogPane().getStylesheets().add(url.toExternalForm());
            }
        }
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

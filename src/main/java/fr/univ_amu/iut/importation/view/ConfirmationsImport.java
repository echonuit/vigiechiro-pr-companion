package fr.univ_amu.iut.importation.view;

import java.util.Objects;
import java.util.function.Predicate;

/// Centralise les **confirmations** de l'assistant d'import (#214) : importer une nuit **déjà importée**
/// (#147) et l'**écrasement destructif** d'un passage existant (#279, double confirmation).
///
/// Extrait de [ImportationController] pour ne pas y concentrer la formulation des messages ni la logique
/// de décision (le contrôleur reste un aiguilleur d'événements). Le prédicat de confirmation est
/// **injectable** (`definirConfirmateur`) pour tester les parcours sans boîte de dialogue native, comme le
/// `ConfirmateurQuitter` du Navigateur.
final class ConfirmationsImport {

    private Predicate<String> confirmateur;

    ConfirmationsImport(Predicate<String> confirmateur) {
        this.confirmateur = Objects.requireNonNull(confirmateur, "confirmateur");
    }

    void definirConfirmateur(Predicate<String> confirmateur) {
        this.confirmateur = Objects.requireNonNull(confirmateur, "confirmateur");
    }

    /// `true` si l'import peut se poursuivre : soit la nuit n'a jamais été importée (`avertissement` vide),
    /// soit l'utilisateur confirme explicitement « importer quand même » comme nouveau passage (#147).
    boolean confirmerImportNuitDejaImportee(String avertissement) {
        return avertissement.isEmpty()
                || confirmateur.test(avertissement + "\n\nImporter quand même comme nouveau passage ?");
    }

    /// `true` si l'utilisateur confirme l'**écrasement** destructif d'un passage existant (#279) : **double
    /// confirmation** (le principe, puis le nombre exact de séquences définitivement supprimées).
    boolean confirmerEcrasement(int sequencesSupprimees) {
        return confirmateur.test("Le n° de passage choisi est déjà utilisé. Écraser le passage existant et le"
                        + " remplacer par cette nuit ?")
                && confirmateur.test("⚠ Suppression DÉFINITIVE du passage existant et de ses " + sequencesSupprimees
                        + " séquence(s). Action irréversible. Confirmer l'écrasement ?");
    }
}

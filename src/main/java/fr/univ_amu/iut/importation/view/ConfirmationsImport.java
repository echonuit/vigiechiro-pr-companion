package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.importation.model.ApercuEcrasement;
import java.util.Objects;

/// Centralise les **confirmations** de l'assistant d'import (#214) : importer une nuit **déjà importée**
/// (#147) et l'**écrasement destructif** d'un passage existant (#279, double confirmation).
///
/// Extrait de [ImportationController] pour ne pas y concentrer la formulation des messages ni la logique
/// de décision (le contrôleur reste un aiguilleur d'événements). Le [Confirmateur] reçu est le porteur
/// partagé du contrôleur ([ConfirmateurModifiable], #1013) : les tests le stubent à la source.
public final class ConfirmationsImport {

    private final Confirmateur confirmateur;

    public ConfirmationsImport(Confirmateur confirmateur) {
        this.confirmateur = Objects.requireNonNull(confirmateur, "confirmateur");
    }

    /// `true` si l'import peut se poursuivre : soit la nuit n'a jamais été importée (`avertissement` vide),
    /// soit l'utilisateur confirme explicitement « importer quand même » comme nouveau passage (#147).
    public boolean confirmerImportNuitDejaImportee(String avertissement) {
        return avertissement.isEmpty()
                || confirmateur.confirmer(avertissement + "\n\nImporter quand même comme nouveau passage ?");
    }

    /// `true` si l'utilisateur confirme l'**écrasement** destructif d'un passage existant (#279) : **double
    /// confirmation** (le principe, puis le détail de ce qui est définitivement supprimé). Si le passage
    /// écrasé porte des **validations observateur** (`apercu.validations() > 0`), elles seront elles aussi
    /// définitivement perdues (aucune préservation possible, contrairement à une ré-importation de CSV) : la
    /// seconde confirmation le mentionne alors explicitement.
    public boolean confirmerEcrasement(ApercuEcrasement apercu) {
        String alerteValidations = apercu.validations() == 0
                ? ""
                : " Dont " + apercu.validations()
                        + " validation(s) Tadarida (correction, référence, commentaire) définitivement perdue(s).";
        return confirmateur.confirmer("Le n° de passage choisi est déjà utilisé. Écraser le passage existant et le"
                        + " remplacer par cette nuit ?")
                && confirmateur.confirmer("⚠ Suppression DÉFINITIVE du passage existant et de ses " + apercu.sequences()
                        + " séquence(s)." + alerteValidations + " Action irréversible. Confirmer l'écrasement ?");
    }
}

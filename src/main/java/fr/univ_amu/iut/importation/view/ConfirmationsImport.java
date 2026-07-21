package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.importation.model.ApercuEcrasement;
import java.util.List;
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

    /// `true` si l'import peut se poursuivre : soit la nuit n'a jamais été importée (compte rendu vide),
    /// soit l'utilisateur confirme explicitement « importer quand même » comme nouveau passage (#147).
    public boolean confirmerImportNuitDejaImportee(CompteRendu question) {
        // La question est rédigée par le ViewModel, qui détient les passages concernés : la vue ne
        // recompose plus une phrase (#2050). C'est un compte rendu structuré, pas une chaîne à puces qui se
        // briserait au retour à la ligne (#2060) : la modale l'aligne par VueCompteRendu.
        return question.estVide() || confirmateur.confirmer(question);
    }

    /// `true` si l'utilisateur confirme l'**écrasement** destructif d'un passage existant (#279) : **double
    /// confirmation** (le principe, puis le détail de ce qui est définitivement supprimé). Si le passage
    /// écrasé porte des **validations observateur** (`apercu.validations() > 0`), elles seront elles aussi
    /// définitivement perdues (aucune préservation possible, contrairement à une ré-importation de CSV) : la
    /// seconde confirmation le mentionne alors explicitement.
    public boolean confirmerEcrasement(ApercuEcrasement apercu) {
        return confirmateur.confirmer("Le n° de passage choisi est déjà utilisé. Écraser le passage existant et le"
                        + " remplacer par cette nuit ?")
                && confirmateur.confirmer(ecrasementDefinitif(apercu));
    }

    /// La seconde confirmation d'écrasement, en **compte rendu** (#2223) : un constat **erreur** (la
    /// suppression définitive et son nombre de séquences), suivi le cas échéant du détail des validations
    /// perdues, et la question en conclusion.
    ///
    /// C'était une chaîne ouvrant par « ⚠ ». La modale rend désormais la sévérité en icône
    /// ([ConfirmationNavigation] via [CompteRendu]), le port `Confirmateur` transportant la structure
    /// depuis #2060 et le porteur injectable la déléguant depuis #2223.
    private static CompteRendu ecrasementDefinitif(ApercuEcrasement apercu) {
        List<Detail> details = apercu.validations() == 0
                ? List.of()
                : List.of(Detail.de("Dont " + apercu.validations()
                        + " validation(s) Tadarida (correction, référence, commentaire) définitivement perdue(s)."));
        Constat perte = new Constat(
                "Suppression DÉFINITIVE du passage existant et de ses " + apercu.sequences() + " séquence(s).",
                Severite.ERREUR,
                details);
        return new CompteRendu("", "", List.of(perte), "Action irréversible. Confirmer l'écrasement ?");
    }
}

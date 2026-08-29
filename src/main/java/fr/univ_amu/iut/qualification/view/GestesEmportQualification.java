package fr.univ_amu.iut.qualification.view;

import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.commun.view.Notificateur;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.qualification.model.ServiceEmport;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.stage.Window;

/// Les quatre gestes de l'emport, tenus **hors du contrôleur** (#4744).
///
/// Sortis de [QualificationController] parce qu'ils l'avaient fait franchir le seuil `GodClass` du
/// portail : quatre méthodes, trois champs et une identité de connexion pour un écran qui en portait
/// déjà beaucoup. Un cliquet qui monte est une décision, et celle-ci se prend en déplaçant plutôt
/// qu'en relevant.
///
/// C'est le patron déjà en service de [VerdictParFichier] et de [Feux] : le contrôleur relie, il
/// n'accumule pas.
final class GestesEmportQualification {

    private final ActionsEmport actions;
    private final StockageConnexion connexion;

    /// La nuit ouverte, que les gestes visent. `null` tant qu'aucune ne l'est.
    private Long idPassage;

    /// @param service le parcours d'emport
    /// @param connexion l'identité qui signe un avis renvoyé
    /// @param fenetre la fenêtre où poser les sélecteurs natifs
    GestesEmportQualification(ServiceEmport service, StockageConnexion connexion, Supplier<Window> fenetre) {
        this.actions = new ActionsEmport(Objects.requireNonNull(service, "service"), fenetre);
        this.connexion = Objects.requireNonNull(connexion, "connexion");
    }

    /// Branche les dialogues du parent : une action qui demande, confirme et rend compte n'est
    /// testable que si les trois passent par ses porteurs.
    ///
    /// @param notificateur le compte rendu du parent
    /// @param confirmateur le oui/non du parent
    void relierAux(Notificateur notificateur, Confirmateur confirmateur) {
        actions.notificateur().definir(notificateur);
        actions.confirmateur().definir(confirmateur);
    }

    /// La nuit sur laquelle les gestes portent désormais.
    ///
    /// @param idPassage la nuit ouverte
    void surNuit(Long idPassage) {
        this.idPassage = idPassage;
    }

    /// Les quatre gestes, tels que la sous-vue les attend.
    SelectionEcouteController.GestesDEmport gestes() {
        return new SelectionEcouteController.GestesDEmport(
                this::emporter, this::ouvrirPaquetRecu, this::renvoyerAvis, this::reprendreAvis);
    }

    private void emporter() {
        if (idPassage != null) {
            actions.emporter(idPassage);
        }
    }

    private void ouvrirPaquetRecu() {
        actions.ouvrirPaquetRecu(connexion.profil());
    }

    /// Renvoie l'avis, signé de qui est connecté ici : sans connexion, il n'y a personne à nommer.
    private void renvoyerAvis() {
        if (idPassage != null) {
            connexion.profil().ifPresent(profil -> actions.renvoyerAvis(idPassage, profil.pseudo()));
        }
    }

    private void reprendreAvis() {
        actions.importerAvis();
    }
}

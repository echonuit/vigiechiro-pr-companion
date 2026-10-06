package fr.univ_amu.iut.qualification.view;

import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.Notificateur;
import fr.univ_amu.iut.commun.view.SelecteurFichierModifiable;
import fr.univ_amu.iut.commun.view.Selecteurs;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.qualification.model.ServiceEmport;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import java.util.Objects;
import java.util.Optional;
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

    /// Ce qui relit la sélection affichée après un geste qui l'a changée en base. Rien tant que
    /// l'écran ne l'a pas branché.
    private Runnable rechargement = () -> {};

    /// @param service le parcours d'emport
    /// @param connexion l'identité qui signe un avis renvoyé
    /// @param fenetre la fenêtre où poser les sélecteurs natifs
    GestesEmportQualification(
            ServiceEmport service, StockageConnexion connexion, Supplier<Window> fenetre, Selecteurs selecteurs) {
        this.actions = new ActionsEmport(Objects.requireNonNull(service, "service"), fenetre, selecteurs);
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

    /// Branche le rechargement de la sélection affichée, **hors du fil JavaFX** et sous l'indicateur
    /// d'occupation, comme l'ouverture de l'écran : relire la base sur le fil de l'interface la
    /// figerait le temps de la lecture.
    ///
    /// @param occupation l'indicateur de l'écran, qui porte le travail hors du fil
    /// @param selectionVm la sélection affichée
    void rechargerPar(IndicateurOccupation occupation, SelectionEcouteViewModel selectionVm) {
        this.rechargement = () -> {
            Long nuit = idPassage;
            if (nuit != null) {
                occupation.occuper(
                        "Relecture de la sélection…",
                        () -> selectionVm.charger(nuit),
                        donnees -> selectionVm.appliquer(nuit, donnees),
                        erreur -> selectionVm.signalerErreur(nuit, erreur));
            }
        };
    }

    /// Le porteur du sélecteur, que la recette substitue (#4728).
    SelecteurFichierModifiable selecteur() {
        return actions.selecteur();
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
        if (actions.ouvrirPaquetRecu(connexion.profil())) {
            rechargement.run();
        }
    }

    /// Renvoie l'avis, signé de qui est connecté ici. Sans connexion il n'y a personne à nommer, et
    /// le geste le dit dans les mots de la commande jumelle plutôt que de se taire.
    private void renvoyerAvis() {
        if (idPassage == null) {
            return;
        }
        Optional<ProfilVigieChiro> profil = connexion.profil();
        if (profil.isEmpty()) {
            actions.notificateur()
                    .notifier(
                            NiveauNotification.AVERTISSEMENT,
                            "Avis non renvoyé",
                            "Aucune identité : reconnectez-vous, sinon l'avis reviendrait anonyme.");
            return;
        }
        actions.renvoyerAvis(idPassage, profil.get().pseudo());
    }

    private void reprendreAvis() {
        if (actions.importerAvis()) {
            rechargement.run();
        }
    }
}

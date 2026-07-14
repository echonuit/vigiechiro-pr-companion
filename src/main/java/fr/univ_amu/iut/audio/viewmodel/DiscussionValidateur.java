package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.validation.model.MessageObservation;
import fr.univ_amu.iut.validation.model.PublicationMessage;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// La **discussion avec le validateur**, vue de l'écran « Sons & validation » : lire le fil (#1417) et y
/// répondre (#1418).
///
/// Un seul collaborateur, et non trois de plus dans [AudioViewModel] : celui-ci est au plafond de la
/// liste de paramètres (PMD), et surtout ces trois besoins — *le fil*, *qui je suis*, *écrire* — forment
/// une seule notion. Les séparer aurait éparpillé une conversation en trois morceaux.
///
/// L'envoi est **optionnel** : `discuter-validateur` est une feature désactivable, et les injecteurs
/// partiels (outils de capture) n'ont pas de client VigieChiro. Quand elle est absente, la lecture du fil
/// continue de fonctionner — c'est tout l'intérêt de les avoir séparées.
public class DiscussionValidateur {

    private final ServiceValidation service;
    private final StockageConnexion connexion;
    private final Optional<PublicationMessage> publication;

    public DiscussionValidateur(
            ServiceValidation service, StockageConnexion connexion, Optional<PublicationMessage> publication) {
        this.service = Objects.requireNonNull(service, "service");
        this.connexion = Objects.requireNonNull(connexion, "connexion");
        this.publication = Objects.requireNonNull(publication, "publication");
    }

    /// Le fil d'une observation, dans l'ordre du serveur. Vide si personne n'a écrit, ou si la ligne n'est
    /// pas une observation (une séquence non identifiée n'existe pas côté plateforme).
    public List<MessageObservation> fil(Long idObservation) {
        return idObservation == null ? List.of() : service.filDeLObservation(idObservation);
    }

    /// Identifiant plateforme du profil connecté, ou `null` hors connexion. Le serveur ne donne que des
    /// objectid comme auteurs : le comparer à celui-ci permet d'écrire « Vous » sans appel réseau.
    public String idProfilConnecte() {
        return connexion.profil().map(profil -> profil.id()).orElse(null);
    }

    /// **Pourquoi** l'utilisateur ne peut-il pas écrire sur cette observation ? Vide s'il le peut.
    ///
    /// Sans réseau : c'est ce qui permet de **désactiver la saisie en disant pourquoi** (affordance #789),
    /// plutôt que de laisser quelqu'un rédiger un message qui échouerait à l'envoi. Trois raisons
    /// possibles : la fonctionnalité est coupée, aucune ligne n'est sélectionnée, ou la détection n'existe
    /// pas sur VigieChiro (import CSV, saisie manuelle) — il n'y a alors personne à qui parler.
    public Optional<String> pourquoiPasEcrire(Long idObservation) {
        if (publication.isEmpty()) {
            return Optional.of("L'envoi de messages au validateur est désactivé.");
        }
        if (idObservation == null) {
            return Optional.of("Sélectionnez une détection pour écrire au validateur.");
        }
        return publication.get().pourquoiImpossible(idObservation);
    }

    /// Poste un message. **Définitif** en cas de succès : le serveur ajoute par `$push` et n'offre aucune
    /// route de suppression. À n'appeler qu'après confirmation explicite, et **hors du fil JavaFX**.
    ///
    /// @throws IllegalStateException si l'envoi est indisponible (la vue doit l'avoir empêché en amont,
    ///     via [#pourquoiPasEcrire] : un bouton qui ne ferait rien au clic est un bug d'affordance)
    public ReponseApi<String> poster(Long idObservation, String texte) {
        return publication
                .orElseThrow(() -> new IllegalStateException("Envoi de messages indisponible"))
                .poster(idObservation, texte);
    }
}

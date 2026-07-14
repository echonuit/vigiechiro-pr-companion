package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.validation.model.dao.MessageObservationDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// **Poster un message** dans le fil de discussion d'une observation (#1418, axe 4.4) : le seul geste de
/// ce chantier qui **écrit** chez VigieChiro sur des données partagées avec un validateur du MNHN.
///
/// ⚠️ **Ce qui part ne se retire pas.** Le serveur ajoute par `$push`, et **aucune route ne permet de
/// supprimer ni de modifier un message** (spike de #724). Ce n'est pas un détail d'implémentation : c'est
/// la propriété qui gouverne tout le reste. L'appelant IHM doit faire **confirmer explicitement**, et le
/// dire — un message envoyé par erreur restera visible pour l'expert, indéfiniment.
///
/// **Le serveur d'abord, la base ensuite.** Le message n'est écrit localement **qu'après** que le serveur
/// l'a accepté. L'inverse — écrire en base puis pousser — laisserait, au moindre refus, un message que
/// l'observateur croirait envoyé et que le validateur ne verrait jamais. C'est exactement la perte
/// silencieuse que l'EPIC #1154 traque, à l'envers.
///
/// Orchestration réseau **sans IHM** (règle ArchUnit `model_sans_javafx`), **bloquante** : à appeler hors
/// du fil JavaFX. Injectée de façon **optionnelle**, comme [PublicationCorrections] : les injecteurs sans
/// `connexion` (outils de capture) ne la chargent pas.
public class PublicationMessage {

    private final ClientVigieChiro client;
    private final ObservationDao observations;
    private final MessageObservationDao messages;
    private final UniteDeTravail uniteDeTravail;

    public PublicationMessage(
            ClientVigieChiro client,
            ObservationDao observations,
            MessageObservationDao messages,
            UniteDeTravail uniteDeTravail) {
        this.client = Objects.requireNonNull(client, "client");
        this.observations = Objects.requireNonNull(observations, "observations");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.uniteDeTravail = Objects.requireNonNull(uniteDeTravail, "uniteDeTravail");
    }

    /// Cette observation **peut-elle** recevoir un message ? Non si elle n'a pas d'**ancrage plateforme**
    /// (`_id` de la donnée + indice brut, #1139) : une observation issue d'un CSV Tadarida, ou saisie à la
    /// main, n'existe pas côté serveur — il n'y a personne à qui parler.
    ///
    /// Sans réseau : l'IHM s'en sert pour **désactiver** la saisie et en **dire la raison** (affordance
    /// #789), plutôt que de laisser l'utilisateur écrire un message qui échouerait à l'envoi.
    public Optional<String> pourquoiImpossible(Long idObservation) {
        Objects.requireNonNull(idObservation, "idObservation");
        Optional<Observation> observation = observations.findById(idObservation);
        if (observation.isEmpty()) {
            return Optional.of("Observation introuvable.");
        }
        if (observation.get().idDonneeVigieChiro() == null) {
            return Optional.of("Cette détection n'existe pas sur VigieChiro (import CSV ou saisie manuelle) :"
                    + " il n'y a pas de fil de discussion à alimenter.");
        }
        return Optional.empty();
    }

    /// Poste `texte` dans le fil de l'observation. **Définitif** en cas de succès.
    ///
    /// En cas de succès, le message est ajouté au fil local **en queue** (l'ordre du serveur est celui du
    /// `$push`) : le fil affiche immédiatement ce qui vient d'être dit, sans attendre le prochain import.
    ///
    /// Ni **auteur**, ni **date** ne sont inventés : c'est le serveur qui les pose, et le prochain import
    /// réécrira le fil avec sa version, qui fait foi. Fabriquer une date locale (« maintenant », en heure
    /// de la machine, alors que le serveur date en UTC à sa propre horloge) reviendrait à afficher un
    /// horodatage que personne d'autre ne verra jamais.
    ///
    /// @param idObservation observation commentée
    /// @param texte corps du message (vide ou blanc → refusé sans réseau : on n'envoie pas du vide)
    /// @return l'issue **triée** (#1284). Rien n'est écrit en base sur un échec
    public ReponseApi<String> poster(Long idObservation, String texte) {
        Objects.requireNonNull(idObservation, "idObservation");
        if (texte == null || texte.isBlank()) {
            return ReponseApi.refuse(0, "Message vide : il n'y a rien à envoyer.");
        }
        Observation observation = observations
                .findById(idObservation)
                .orElseThrow(() -> new IllegalArgumentException("Observation introuvable : " + idObservation));
        if (observation.idDonneeVigieChiro() == null || observation.indiceVigieChiro() == null) {
            return ReponseApi.refuse(
                    0,
                    "Cette détection n'a pas d'ancrage VigieChiro : elle n'existe pas côté serveur,"
                            + " aucun message ne peut y être rattaché.");
        }

        String propre = texte.strip();
        ReponseApi<String> reponse =
                client.posterMessage(observation.idDonneeVigieChiro(), observation.indiceVigieChiro(), propre);

        // Le serveur d'abord. Écrire en base sur un refus laisserait un message que l'observateur croirait
        // envoyé, et que le validateur ne verrait jamais.
        if (reponse instanceof ReponseApi.Succes) {
            enregistrerLocalement(idObservation, propre);
        }
        return reponse;
    }

    /// Ajoute le message en **queue** du fil local : le rang suit le dernier message connu, comme le
    /// `$push` du serveur. L'auteur et la date restent vides — le serveur les posera (cf. [#poster]).
    private void enregistrerLocalement(Long idObservation, String texte) {
        List<MessageObservation> fil = messages.filDeLObservation(idObservation);
        int rang = fil.isEmpty() ? 0 : fil.getLast().rang() + 1;
        List<MessageObservation> complet = new ArrayList<>(fil);
        complet.add(new MessageObservation(null, idObservation, rang, null, texte, null));
        uniteDeTravail.executer(connexion -> messages.remplacerFil(connexion, idObservation, complet));
    }
}

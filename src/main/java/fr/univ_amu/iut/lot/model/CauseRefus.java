package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.api.Provenance;
import fr.univ_amu.iut.commun.api.ReponseApi;

/// Pourquoi un refus de dépôt est **définitif**, et donc **ce qui pourrait le lever** (#3689).
///
/// ## Pourquoi cette distinction existe
///
/// Depuis #3687, l'écran cesse de proposer une reprise sur une unité refusée définitivement : le bouton
/// ne promet plus ce qu'il ne peut pas tenir. Mais rien ne **réarme** ces unités quand la cause
/// extérieure est levée, et une nuit dont toutes les archives ont été refusées reste alors coincée.
///
/// Réarmer suppose de savoir de quoi il retourne, et ces refus ne sont pas de même nature : une
/// reconnexion réussie répare des droits, elle ne répare pas un contenu refusé.
///
/// ## La cause vient du statut et de la provenance, jamais du texte
///
/// Le dépôt s'interdit de relire `message_erreur` pour en déduire quoi que ce soit - « la même panne
/// s'y écrit de trop de façons pour qu'on la redevine ». La cause est donc décidée **à l'émission**,
/// depuis le statut HTTP et la [Provenance] de la requête qui a échoué, puis persistée.
///
/// Le statut seul ne suffisait pas (#5598) : un `403` de l'API et un `403` du stockage S3 étaient
/// rangés ensemble, et l'écran conseillait une reconnexion qui ne peut rien pour une URL pré-signée.
public enum CauseRefus {
    /// 401 / 403 **de l'API** : jeton mort, droits manquants. **Une reconnexion réussie peut lever cette
    /// cause** : ces unités se réarment.
    ///
    /// C'est ici, et **seulement** ici, que la règle est écrite. Une méthode `leveeParUneReconnexion()`
    /// la disait aussi, et personne ne l'appelait : `RearmementDepotUnites` passe la constante en dur au
    /// DAO. Deux expressions d'une même règle, dont l'inerte **se lisait** comme l'autorité - un lecteur
    /// qui l'aurait modifiée aurait cru changer le comportement (#3961).
    AUTHENTIFICATION,

    /// 400 / 422 et les autres 4xx : le contenu lui-même est refusé. **Aucun événement extérieur ne le
    /// répare** ; seule une régénération de l'archive changerait quelque chose, et c'est un autre geste.
    CONTENU,

    /// 401 / 403 **du stockage S3** : URL pré-signée refusée, expirée, ou dont la signature ne couvre pas
    /// la requête (#5598). **Une reconnexion n'y peut rien** : le jeton de l'API n'intervient pas dans
    /// une URL pré-signée. Une relance redemande des URL neuves ; si le refus persiste, reste le dépôt
    /// manuel.
    STOCKAGE;

    /// La cause d'un refus, ou `null` si la réponse n'est pas un refus définitif.
    ///
    /// Un `429` ou un `5xx` **n'arrive pas ici** : `ReponseApi.estReessayable()` les juge rejouables,
    /// donc l'unité n'est jamais marquée définitive et n'a pas de cause à porter.
    public static CauseRefus de(ReponseApi<?> reponse, Provenance provenance) {
        if (!(reponse instanceof ReponseApi.Refuse<?> refus) || reponse.estReessayable()) {
            return null;
        }
        if (refus.statut() != 401 && refus.statut() != 403) {
            return CONTENU;
        }
        return provenance == Provenance.STOCKAGE ? STOCKAGE : AUTHENTIFICATION;
    }
}

package fr.univ_amu.iut.lot.model;

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
/// ## La cause vient du statut, jamais du texte
///
/// Le dépôt s'interdit de relire `message_erreur` pour en déduire quoi que ce soit - « la même panne
/// s'y écrit de trop de façons pour qu'on la redevine ». La cause est donc décidée **à l'émission**,
/// depuis le statut HTTP, puis persistée.
public enum CauseRefus {
    /// 401 / 403 : jeton mort, droits S3 manquants, URL signée expirée. **Une reconnexion réussie peut
    /// lever cette cause** : ces unités se réarment.
    AUTHENTIFICATION,

    /// 400 / 422 et les autres 4xx : le contenu lui-même est refusé. **Aucun événement extérieur ne le
    /// répare** ; seule une régénération de l'archive changerait quelque chose, et c'est un autre geste.
    CONTENU;

    /// La cause d'un refus, ou `null` si la réponse n'est pas un refus définitif.
    ///
    /// ⚠️ Un `429` ou un `5xx` **n'arrive pas ici** : `ReponseApi.estReessayable()` les juge rejouables,
    /// donc l'unité n'est jamais marquée définitive et n'a pas de cause à porter.
    public static CauseRefus de(ReponseApi<?> reponse) {
        if (!(reponse instanceof ReponseApi.Refuse<?> refus) || reponse.estReessayable()) {
            return null;
        }
        return refus.statut() == 401 || refus.statut() == 403 ? AUTHENTIFICATION : CONTENU;
    }

    /// Vrai si une **reconnexion réussie** peut avoir levé cette cause.
    public boolean leveeParUneReconnexion() {
        return this == AUTHENTIFICATION;
    }
}

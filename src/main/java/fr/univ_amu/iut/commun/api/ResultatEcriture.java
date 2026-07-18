package fr.univ_amu.iut.commun.api;

import java.util.Optional;

/// Issue d'une **écriture** sur la plateforme VigieChiro : soit elle a abouti (en produisant
/// éventuellement un identifiant), soit elle a été refusée, et le **détail** du refus est conservé
/// (statut HTTP + corps de la réponse, ou cause réseau).
///
/// Une écriture refusée doit être **expliquée** à l'utilisateur - message exploitable, jamais un booléen
/// opaque. C'est la même exigence que l'[ADR
/// 0008](https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/dev/decisions/0008-aucun-echec-silencieux-severite-a-l-emission/)
/// (aucun échec silencieux) appliquée au sens montant.
///
/// **Le succès se lit sur [#echec], pas sur [#id].** Ce type remplace les deux records qui disaient la
/// même chose de deux façons : l'un portait l'identifiant créé (participation), l'autre rien du tout
/// (correction d'observation). La confusion était visible dans `modifierParticipation`, qui **recopiait
/// son propre paramètre** dans le champ `id` uniquement pour que le test de succès `id().isPresent()`
/// réponde vrai - l'identifiant y servait de drapeau, pas de charge utile.
///
/// L'identifiant redevient donc ce qu'il est : une **charge utile**, présente quand l'écriture a créé
/// quelque chose (une participation), absente quand elle en a modifié une (un `PATCH`, une correction
/// d'observation).
///
/// @param id identifiant produit par l'écriture, quand elle en produit un
/// @param echec détail du refus (statut + réponse, ou cause réseau), `null` en cas de succès
public record ResultatEcriture(Optional<String> id, String echec) {

    /// Écriture aboutie **sans identifiant à rendre** (mise à jour, correction).
    public static ResultatEcriture reussie() {
        return new ResultatEcriture(Optional.empty(), null);
    }

    /// Écriture aboutie ayant **créé** l'entité `id`.
    public static ResultatEcriture reussie(String id) {
        return new ResultatEcriture(Optional.of(id), null);
    }

    /// Écriture **refusée**, avec de quoi le dire à l'utilisateur.
    public static ResultatEcriture echouee(String echec) {
        return new ResultatEcriture(Optional.empty(), echec);
    }

    /// `true` si la plateforme a accepté l'écriture.
    public boolean estReussie() {
        return echec == null;
    }
}

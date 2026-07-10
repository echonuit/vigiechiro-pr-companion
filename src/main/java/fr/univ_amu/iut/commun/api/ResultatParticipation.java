package fr.univ_amu.iut.commun.api;

import java.util.Optional;

/// Résultat d'une **création de participation** ([ClientVigieChiro#creerParticipation]) : soit l'`_id`
/// créé, soit le **détail de l'échec** (statut HTTP + corps de la réponse VigieChiro, ou cause réseau). La
/// création est une écriture : un refus doit être **expliqué** à l'utilisateur (message exploitable), pas
/// réduit à un `Optional.empty()` opaque.
///
/// Exactement l'un des deux est renseigné : [#id] présent en cas de succès, [#echec] non `null` sinon.
///
/// @param id identifiant de la participation créée (présent en cas de succès)
/// @param echec détail de l'échec (statut + réponse, ou cause réseau), ou `null` en cas de succès
public record ResultatParticipation(Optional<String> id, String echec) {

    public static ResultatParticipation reussie(String id) {
        return new ResultatParticipation(Optional.of(id), null);
    }

    public static ResultatParticipation echouee(String echec) {
        return new ResultatParticipation(Optional.empty(), echec);
    }
}

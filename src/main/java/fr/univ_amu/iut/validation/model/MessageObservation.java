package fr.univ_amu.iut.validation.model;

import java.time.Instant;

/// Un message du **fil de discussion** d'une observation (table `observation_message`, V26, #1417) :
/// le canal par lequel l'observateur et le validateur du MNHN se parlent à propos d'une détection.
///
/// **Reflet du serveur**, jamais une saisie locale : le fil vient frais de
/// `GET /participations/{id}/donnees` à chaque import, et disparaît avec l'observation qu'il commente
/// (cascade). Ce que l'utilisateur poste, lui, part au serveur d'abord (#1418).
///
/// L'auteur est un **identifiant plateforme** (objectid de la ressource `utilisateurs`), **pas** un
/// nom : le serveur ne donne rien d'autre dans cette charge utile. Plutôt qu'un appel réseau par
/// auteur, l'application le compare à l'identifiant de son propre profil — déjà stocké localement à la
/// connexion — pour distinguer « vous » d'« un validateur » ([#deMoi(String)]).
///
/// @param id clé technique, `null` avant insertion
/// @param idObservation observation commentée (FK → `observation.id`, obligatoire)
/// @param rang position dans le fil : l'ordre du serveur, qui **est** l'ordre chronologique (l'ajout
///     se fait par `$push`). Figé pour ne pas dépendre de l'ordre d'insertion
/// @param auteur identifiant plateforme de l'auteur, ou `null` si le serveur ne l'a pas renseigné
/// @param texte corps du message (obligatoire)
/// @param date date de publication, ou `null` si le serveur ne l'a pas donnée, ou si son format était
///     illisible : un fil daté à moitié reste un fil lisible
public record MessageObservation(Long id, Long idObservation, int rang, String auteur, String texte, Instant date) {

    /// Ce message est-il **le nôtre** ? Vrai si son auteur est l'utilisateur dont l'identifiant
    /// plateforme est `idProfil` (celui du profil connecté). Faux si l'un des deux est inconnu : sans
    /// certitude sur l'identité, on n'attribue rien — mieux vaut un message d'auteur indéterminé
    /// qu'un message faussement signé.
    public boolean deMoi(String idProfil) {
        return auteur != null && idProfil != null && auteur.equals(idProfil);
    }
}

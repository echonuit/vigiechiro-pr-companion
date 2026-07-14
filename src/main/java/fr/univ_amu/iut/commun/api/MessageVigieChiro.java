package fr.univ_amu.iut.commun.api;

import java.time.Instant;

/// Un message du **fil de discussion** d'une observation VigieChiro (#1417, axe 4.4) : le canal par
/// lequel l'observateur et le validateur du MNHN se parlent à propos d'une détection.
///
/// C'est un sous-document de l'observation dans le schéma serveur (`messages`, cf. le spike de #724) :
/// il arrive donc **déjà** dans `GET /participations/#id/donnees`, sans appel supplémentaire.
///
/// L'`auteur` est un **identifiant plateforme** (objectid de la ressource `utilisateurs`), **pas** un
/// nom : le serveur ne donne rien d'autre ici. Le résoudre en nom demanderait un appel par auteur ;
/// l'application se contente de le comparer à l'identifiant de son propre profil (déjà stocké
/// localement à la connexion) pour distinguer « vous » d'« un validateur ».
///
/// @param auteur identifiant plateforme de l'auteur, ou `null` si le serveur ne l'a pas renseigné
/// @param texte corps du message (jamais `null` : un message sans texte n'est pas retenu au parsing)
/// @param date date de publication, ou `null` si absente ou illisible (tolérance : le format de date
///     du serveur peut évoluer sans nous prévenir, et un fil sans date reste lisible)
public record MessageVigieChiro(String auteur, String texte, Instant date) {}

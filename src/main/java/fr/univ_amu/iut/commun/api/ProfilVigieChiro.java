package fr.univ_amu.iut.commun.api;

/// Profil de l'utilisateur connecté à VigieChiro (`GET /moi`), réduit à ce dont l'application a besoin.
/// Objet de données pur (aucune dépendance JavaFX ni JDBC).
///
/// @param id identifiant plateforme (objectid Mongo) ; jamais `null` sur un profil valide
/// @param pseudo pseudo affiché, ou `null`
/// @param role rôle VigieChiro (`Observateur`, `Validateur`, `Administrateur`), ou `null`
public record ProfilVigieChiro(String id, String pseudo, String role) {}

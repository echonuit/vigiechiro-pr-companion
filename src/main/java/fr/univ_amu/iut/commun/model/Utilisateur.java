package fr.univ_amu.iut.commun.model;

/// Unique utilisateur de l'application (C1, table `user`).
///
/// L'application est mono-utilisateur et hors-ligne : pas de compte ni de mot de passe. En
/// pratique, la table `user` ne contient qu'une seule ligne. L'entité est **transverse**
/// (cross-cutting) : elle vit dans `commun.model` car plusieurs features y font référence (un
/// site appartient à l'utilisateur courant, R2.01).
///
/// @param localId identifiant local (UUID en `TEXT`), généré à l'installation, jamais affiché
/// : clé primaire naturelle
/// @param displayName nom affiché (optionnel, repris dans la barre de titre)
public record Utilisateur(String localId, String displayName) {}

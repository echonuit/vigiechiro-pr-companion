package fr.univ_amu.iut.audit.model;

/// Un écart de cohérence relevé par l'[ServiceAuditCoherence].
///
/// @param severite gravité du constat
/// @param categorie nature de l'incohérence
/// @param idPassage passage concerné, ou `null` pour un dossier orphelin sans passage rattaché
/// @param cible élément visé (chemin de fichier, nom logique, dossier)
/// @param detail explication lisible destinée à l'utilisateur
public record ConstatAudit(
        SeveriteConstat severite, CategorieConstat categorie, Long idPassage, String cible, String detail) {}

package fr.univ_amu.iut.audit.model;

import fr.univ_amu.iut.commun.model.Severite;

/// Un écart de cohérence relevé par l'[ServiceAuditCoherence].
///
/// @param severite gravité du constat
/// @param categorie nature de l'incohérence
/// @param idPassage passage concerné, ou `null` pour un dossier orphelin sans passage rattaché
/// @param cible élément visé (chemin de fichier, nom logique, dossier)
/// @param detail explication lisible destinée à l'utilisateur
///
/// La [Severite] du socle compte **quatre** niveaux ; l'audit n'en produit que trois. `SUCCES` est
/// donc atteignable par le type sans qu'aucun constat ne l'émette : un audit ne félicite pas, il relève
/// ce qui cloche. Écrit ici pour que le prochain lecteur ne cherche pas où ce niveau est produit (#2159).
public record ConstatAudit(
        Severite severite, CategorieConstat categorie, Long idPassage, String cible, String detail) {}

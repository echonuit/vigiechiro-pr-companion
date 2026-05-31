package fr.univ_amu.iut.validation.model;

/// Taxon : code 6 lettres de la nomenclature Tadarida (C14, table `taxon`).
///
/// Contrairement aux entités à clé technique, le taxon a une **clé naturelle** : son `code` (3
/// lettres du genre + 3 de l'espèce, ex. `Pippip`, `Nyclei`). Les pseudo-taxons `noise` (bruit)
/// et `piaf` (oiseau) suivent la même table. L'insertion ne récupère donc aucune clé générée (cf.
/// `UtilisateurDao`) : [fr.univ_amu.iut.validation.model.dao.TaxonDao#insert(Taxon)] renvoie
/// l'entité telle quelle.
///
/// Le nom latin et le nom vernaculaire sont optionnels (`null`) : les pseudo-taxons n'ont pas de
/// nom latin.
///
/// @param code clé naturelle, code 6 lettres (ou pseudo-taxon `noise` / `piaf`)
/// @param nomLatin nom latin (optionnel, ex. `"Pipistrellus pipistrellus"`)
/// @param nomVernaculaireFr nom vernaculaire français (optionnel, ex. `"Pipistrelle commune"`)
/// @param idGroupe identifiant du groupe taxonomique parent (FK → `taxonomic_group.id`)
public record Taxon(String code, String nomLatin, String nomVernaculaireFr, Long idGroupe) {}

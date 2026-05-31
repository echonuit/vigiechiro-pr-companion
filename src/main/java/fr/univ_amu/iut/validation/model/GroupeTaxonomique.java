package fr.univ_amu.iut.validation.model;

/// Groupe taxonomique : niveau hiérarchique au-dessus du taxon (C15, table `taxonomic_group`).
///
/// Sert de filtre groupé dans l'IHM (« tous les murins », « toutes les pipistrelles »). Chaque
/// groupe regroupe `1..*` [Taxon] via `taxon.group_id`.
///
/// L'`id` (clé technique auto-incrémentée) vaut `null` tant que le groupe n'a pas été inséré :
/// [fr.univ_amu.iut.validation.model.dao.GroupeTaxonomiqueDao#insert(GroupeTaxonomique)] renvoie
/// une copie avec l'id généré par SQLite.
///
/// Le `niveau` (« Genre » / « Famille » / « Ordre ») est conceptuellement un énum mais reste
/// stocké en `TEXT` libre : aucun énum n'est fourni par `commun.model` pour ce point de variation
/// (cf. note d'intégration), on le modélise donc en [String] comme `Site#numeroCarre()` pour
/// rester fidèle au patron de la feature de référence.
///
/// @param id clé technique, `null` avant insertion
/// @param niveau niveau hiérarchique (ex. `"Genre"`, `"Famille"`, `"Ordre"`)
/// @param nom nom du groupe (ex. `"Pipistrellus"`, `"Vespertilionidae"`)
public record GroupeTaxonomique(Long id, String niveau, String nom) {}

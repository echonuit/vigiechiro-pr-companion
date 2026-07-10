package fr.univ_amu.iut.commun.model;

/// Identité minimale d'une espèce, telle qu'une vue « espèces » la connaît, pour construire un lien
/// vers sa **fiche d'information** externe (cf. [ConstructeurLienEspece]).
///
/// Value object hébergé dans `commun` pour être partagé par les features qui affichent des espèces
/// (`validation`/audio, `analyse`) **sans les coupler** : chacune bâtit une `EspeceIdentifiee` depuis
/// son propre modèle de ligne, et ne connaît que ce contrat (même principe d'inversion que
/// [CoordonneesPoint] pour le GPS).
///
/// Les trois champs sont **optionnels** (`null` admis) : les pseudo-taxons `noise`/`piaf` n'ont ni nom
/// latin ni nom vernaculaire, et un taxon hors référentiel peut n'avoir que son code. Le nom
/// vernaculaire n'entre pas dans la construction de l'URL ; il est porté ici pour l'affichage de
/// l'action IHM (libellé « Fiche de l'espèce (Pipistrelle commune) »).
///
/// @param codeTadarida code 6 lettres de la nomenclature Tadarida (ex. `Pippip`), ou pseudo-taxon
///     (`noise`/`piaf`) ; sert de clé pour la fiche spécialisée chiroptères
/// @param nomLatin nom latin (ex. `Pipistrellus pipistrellus`), ou `null` ; sert de clé pour la source
///     universelle (par nom scientifique)
/// @param nomVernaculaireFr nom vernaculaire français (ex. `Pipistrelle commune`), ou `null` ; libellé
///     d'affichage uniquement
public record EspeceIdentifiee(String codeTadarida, String nomLatin, String nomVernaculaireFr) {}

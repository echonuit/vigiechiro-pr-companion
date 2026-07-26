package fr.univ_amu.iut.passage.model;

/// Campagne de suivi (#2355, table `campagne`) : un regroupement **facultatif** de passages relevant
/// du même suivi (une saison sur un ensemble de points). L'objet n'est qu'un intitulé ; le lien avec
/// les passages est porté par la colonne nullable `passage.campaign_id` (lot 1 PR 2).
///
/// Vit dans `passage.model` parce que c'est le passage qui la référence ; assemblée par
/// [fr.univ_amu.iut.passage.di.CampagneModule].
///
/// @param id clé technique, `null` avant insertion
/// @param nom nom de la campagne (ex. « Suivi ENS 2026 »)
/// @param annee année de la campagne
/// @param commentaire commentaire libre (optionnel, `null` accepté)
public record Campagne(Long id, String nom, int annee, String commentaire) {}

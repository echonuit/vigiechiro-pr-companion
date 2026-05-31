package fr.univ_amu.iut.validation.model;

/// Résultats d'identification : fichier CSV produit par Tadarida et importé pour la validation
/// taxonomique (C12, table `identification_results`).
///
/// Rattaché à **un seul passage** (`passage_id` unique, cardinalité 0:1 côté passage) : un
/// passage est annoté par au plus un jeu de résultats. Agrège `1..*` [Observation] via
/// `observation.results_id`.
///
/// L'`id` (clé technique auto-incrémentée) vaut `null` tant que les résultats n'ont pas été
/// insérés. Le `formatDetecte` (« Brut » / « Vu ») est un énum stocké en `TEXT` libre (aucun énum
/// `commun.model` fourni pour ce point de variation, cf. note d'intégration).
///
/// @param id clé technique, `null` avant insertion
/// @param cheminFichier chemin du CSV sur disque (sous-dossier `transformes/`, R23)
/// @param formatDetecte format détecté (ex. `"Brut"` avec guillemets, `"Vu"` réinjectable)
/// @param dateImport date/heure d'import (ISO-8601)
/// @param idPassage identifiant du passage annoté (FK → `passage.id`, unique)
public record ResultatsIdentification(
    Long id, String cheminFichier, String formatDetecte, String dateImport, Long idPassage) {}

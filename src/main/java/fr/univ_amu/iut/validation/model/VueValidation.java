package fr.univ_amu.iut.validation.model;

import java.util.List;

/// Projection de lecture pour l'écran **M-Vision-Tadarida** : les observations d'un passage (avec
/// leur statut de revue) et l'identifiant du jeu de résultats qui les porte (pour l'export et les
/// actions valider/corriger).
///
/// `idResultats` est `null` et `observations` vide tant qu'aucun CSV Tadarida n'a été importé pour
/// le passage : l'écran affiche alors un état vide plutôt que de lever.
///
/// @param idResultats identifiant du jeu de résultats (`identification_results`), ou `null`
/// @param observations observations + statut, dans l'ordre d'import
public record VueValidation(Long idResultats, List<ObservationStatut> observations) {}

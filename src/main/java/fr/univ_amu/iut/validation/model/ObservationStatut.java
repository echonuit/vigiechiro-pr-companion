package fr.univ_amu.iut.validation.model;

/// Une observation Tadarida accompagnée de son [StatutObservation] dérivé (non touchée / validée /
/// corrigée), pour l'affichage de la liste de M-Vision-Tadarida. Projection de lecture immuable.
///
/// @param observation l'observation (taxon Tadarida, probabilité, taxon observateur éventuel…)
/// @param statut statut de revue dérivé par [ServiceValidation#statut]
public record ObservationStatut(Observation observation, StatutObservation statut) {}

package fr.univ_amu.iut.passage.viewmodel;

/// État d'une étape du stepper de workflow de M-Passage, relatif au statut courant du passage.
public enum EtatEtape {
  /// Étape déjà franchie (statut antérieur au statut courant).
  FRANCHIE,
  /// Étape courante (statut actuel du passage, action éventuellement requise).
  COURANTE,
  /// Étape encore à venir.
  A_VENIR
}

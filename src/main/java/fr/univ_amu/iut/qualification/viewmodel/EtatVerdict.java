package fr.univ_amu.iut.qualification.viewmodel;

/// État du verdict de qualification (M-Qualification), piloté par
/// [QualificationViewModel#enregistrer()].
public enum EtatVerdict {
  /// Verdict choisi mais pas encore persisté : la vue affiche les boutons et le bouton Enregistrer.
  BROUILLON,
  /// Verdict enregistré : le passage est passé au statut `VERIFIE`.
  ENREGISTRE
}

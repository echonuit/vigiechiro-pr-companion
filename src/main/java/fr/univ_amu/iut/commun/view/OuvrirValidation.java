package fr.univ_amu.iut.commun.view;

/// Contrat socle d'**ouverture de la validation Tadarida** (M-Vision-Tadarida) d'un passage.
///
/// Même esprit que [OuvrirVerification] / [OuvrirDiagnostic] : le socle inverse la dépendance.
/// La feature `validation` en fournit l'implémentation (`NavigationValidation`) et la lie dans son
/// module ; la feature `passage` (M-Passage, onglet « Validation Tadarida ») l'injecte pour ouvrir
/// l'écran **sans dépendre** de `validation.view` (le graphe de slices reste acyclique).
public interface OuvrirValidation {

  /// Ouvre l'écran de validation taxonomique des résultats Tadarida du passage `idPassage`.
  void ouvrir(Long idPassage);
}

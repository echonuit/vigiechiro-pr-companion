package fr.univ_amu.iut.importation.viewmodel;

/// État de l'exécution de l'import dans l'assistant M-Import, piloté par
/// [ImportationViewModel#importer()]. La vue bascule entre l'assistant, la barre de progression, le
/// résumé de résultat et l'affichage d'erreur selon cet état.
public enum EtatImport {
  /// Avant tout import : l'assistant (dossier / inspection / rattachement) est affiché.
  PRET,
  /// Import en cours (copie protégée + renommage + transformation).
  EN_COURS,
  /// Import terminé avec succès : le résultat est disponible.
  TERMINE,
  /// Import en échec : un message d'erreur est disponible.
  ECHEC
}

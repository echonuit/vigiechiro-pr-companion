package fr.univ_amu.iut.passage.viewmodel;

/// Prochaine action recommandée du workflow d'un passage, dérivée de son statut (progression
/// linéaire Importé → Transformé → Vérifié → Prêt à déposer → Déposé).
///
/// Pilote la **mise en avant visuelle d'une seule carte d'action** dans M-Passage : la carte
/// correspondant à l'étape suivante porte le liseré « recommandée ». La mise en avant se déplace donc
/// au fil de l'avancement (Vérifier → Préparer le dépôt → Validation Tadarida), au lieu de rester
/// figée sur la première action.
public enum ActionRecommandee {
    /// Aucune action mise en avant (nuit pas encore transformée : on attend la transformation).
    AUCUNE,
    /// Vérifier l'enregistrement (nuit transformée, pas encore vérifiée).
    VERIFIER,
    /// Préparer le dépôt (passage vérifié ou prêt à déposer).
    DEPOSER,
    /// Valider les résultats Tadarida (passage déposé).
    VALIDER
}

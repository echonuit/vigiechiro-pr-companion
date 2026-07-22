package fr.univ_amu.iut.passage.model;

/// Que devient un fichier **vérifié** lors d'une réactivation (#2255, ADR 0048) : l'application se
/// l'approprie, ou elle le laisse où il est.
///
/// Le choix se **déduit du chemin** désigné - un dossier hors de l'espace de travail appelle la
/// référence - mais il reste **offert dans tous les cas** : l'utilisateur peut vouloir rapatrier
/// depuis une carte SD qu'il va rendre, comme il peut vouloir garder son audio sur son NAS.
public enum ModeRebranchement {

    /// Le fichier est **copié** à l'emplacement que la base attend. L'audio devient *possédé* par
    /// l'espace de travail : il y vivra tant que l'utilisateur ne l'efface pas lui-même. C'est le
    /// geste historique, et celui qui convient quand la source est temporaire (carte SD à rendre).
    COPIE,

    /// **Rien n'est déplacé** : c'est la base qui pointe désormais le fichier là où il vit. L'audio
    /// reste *à l'utilisateur* - sur son NAS, son disque externe, son dossier de travail habituel.
    ///
    /// Conséquence à dire, pas à taire : la nuit devient muette si ce support n'est plus joignable,
    /// et redevient écoutable quand il revient (l'identité est alors revérifiée, #2254). Rien n'est
    /// perdu, mais la disponibilité ne dépend plus de nous.
    REFERENCE
}

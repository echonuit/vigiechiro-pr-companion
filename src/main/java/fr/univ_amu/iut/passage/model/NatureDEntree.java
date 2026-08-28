package fr.univ_amu.iut.passage.model;

/// Ce que pèse chaque **nature** de contenu dans un paquet d'emport (#4625).
///
/// Ventiler l'estimation plutôt que d'en rendre un total unique est ce qui rend le choix informé :
/// l'utilisateur voit ce que coûtent les séquences séparément du reste, et sait donc ce qu'il
/// gagnerait à ne pas les emporter.
public enum NatureDEntree {
    /// Le manifeste et les métadonnées de la nuit : quelques kilo-octets.
    METADONNEES,
    /// Les séquences transformées, qui font le volume.
    SEQUENCE
}

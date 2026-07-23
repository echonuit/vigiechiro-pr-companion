package fr.univ_amu.iut.validation.model;

/// Statut **dérivé** d'une [Observation] vis-à-vis de la validation taxonomique (R15, R16, R17).
/// Ce n'est pas une colonne stockée : il se déduit de la comparaison entre `taxonObservateur` et
/// `taxonTadarida` (cf. `ServiceValidation#statut`).
///
/// - [#NON_TOUCHEE] (R17) : aucun taxon observateur saisi → la ligne conserve ses colonnes
///   `tadarida_*` telles quelles à l'export `_Vu`.
/// - [#VALIDEE] (R15) : taxon observateur = taxon Tadarida. La **probabilité observateur n'entre
///   pas** dans le statut (cf. `ServiceValidation#statut`) : la confiance de l'observateur se
///   déclare par la [fr.univ_amu.iut.commun.model.Certitude], distincte du statut.
/// - [#CORRIGEE] (R16) : taxon observateur ≠ taxon Tadarida.
public enum StatutObservation {
    NON_TOUCHEE,
    VALIDEE,
    CORRIGEE
}

package fr.univ_amu.iut.validation.model;

/// Projection **inventaire par espèce** (#analyse) : une espèce ([Taxon]) observée par l'utilisateur,
/// agrégée sur **toutes** ses observations (selon le filtre de statut). Sert à la vue transverse
/// « Espèces & observations » — répondre à « quelles espèces, où, quand, combien ».
///
/// L'espèce est le taxon **validé** s'il existe, sinon la proposition Tadarida
/// (`COALESCE(taxon_observer, taxon_tadarida)`) ; les pseudo-taxons `noise`/`piaf` sont exclus.
///
/// @param code code 6 lettres du taxon
/// @param nomLatin nom latin (optionnel)
/// @param nomVernaculaireFr nom vernaculaire français (optionnel)
/// @param groupe nom du groupe taxonomique (ex. « Pipistrellus »), optionnel
/// @param nbObservations nombre de détections
/// @param nbPassages nombre de passages distincts où l'espèce apparaît
/// @param nbCarres nombre de carrés distincts
/// @param nbPoints nombre de points d'écoute distincts
/// @param anneeMin première année d'observation
/// @param anneeMax dernière année d'observation
public record EspeceAgregee(
        String code,
        String nomLatin,
        String nomVernaculaireFr,
        String groupe,
        int nbObservations,
        int nbPassages,
        int nbCarres,
        int nbPoints,
        int anneeMin,
        int anneeMax) {}

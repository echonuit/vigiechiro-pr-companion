package fr.univ_amu.iut.validation.model;

/// Une observation **enrichie** de l'inventaire « Espèces & observations » (#analyse) : une ligne par
/// observation de l'utilisateur (pseudo-taxons bruit/oiseau exclus), portant l'**espèce retenue**
/// (`COALESCE(observateur, tadarida)`), son **statut** de revue dérivé, l'**info espèce** (latin,
/// vernaculaire, groupe/taxon parent) et le **contexte** du relevé (passage, année, carré, site, point).
///
/// C'est la **matière brute** du filtrage et de l'agrégation **côté client** (#537 étape 4) : le socle
/// filtre ces lignes homogènes, puis [fr.univ_amu.iut.analyse.model.AgregationAnalyse] les regroupe par
/// espèce ([EspeceAgregee]) ou par carré ([CarreEspeces]). Remplace l'agrégation SQL par des
/// `GROUP BY` en mémoire, à échelle modeste (~4000 observations).
///
/// @param taxonRetenu code du taxon retenu (`COALESCE(observateur, tadarida)`)
/// @param nomLatin nom latin de l'espèce, ou `null`
/// @param nomVernaculaireFr nom vernaculaire français, ou `null`
/// @param groupe groupe taxonomique (taxon parent, ex. « Chiroptères »), ou `null`
/// @param statut statut de revue dérivé de l'observation
/// @param idPassage identifiant du passage (pour compter les passages distincts)
/// @param annee année du passage (min/max de l'inventaire)
/// @param numeroCarre numéro de carré du site
/// @param nomSite nom convivial du site
/// @param idPoint identifiant du point d'écoute (pour compter les points distincts)
public record ObservationAnalyse(
        String taxonRetenu,
        String nomLatin,
        String nomVernaculaireFr,
        String groupe,
        StatutObservation statut,
        long idPassage,
        int annee,
        String numeroCarre,
        String nomSite,
        long idPoint) {}

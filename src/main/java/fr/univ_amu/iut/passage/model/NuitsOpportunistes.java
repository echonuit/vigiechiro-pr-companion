package fr.univ_amu.iut.passage.model;

import java.util.Set;

/// Port de **lecture** du marquage opportuniste (#2614) : une feature qui exploite les nuits sans être
/// `passage` (au premier chef `analyse`) sait lesquelles sont des participations opportunistes (carré
/// d'un tiers, exemptées de R3/R4, #2525) **sans dépendre de tout [ServicePassage]** ni du DAO.
///
/// Pendant en lecture de [MarquageOpportuniste], qui n'expose que l'écriture : les deux restent séparés
/// pour qu'`importation`, qui ne fait que marquer, ne se voie pas offrir une lecture dont elle n'a que
/// faire, et réciproquement.
///
/// Lecture **groupée et rare** : le seul consommateur est une puce de filtre, qui en prend un instantané
/// à son ouverture plutôt qu'une requête par ligne filtrée.
@FunctionalInterface
public interface NuitsOpportunistes {

    /// Identifiants de tous les passages marqués opportunistes. Ensemble éventuellement **vide** : c'est
    /// le cas courant d'une saison menée entièrement sur ses propres carrés.
    Set<Long> identifiants();
}

package fr.univ_amu.iut.validation.model;

import java.util.Set;

/// Port de lecture des **espèces à enjeu de conservation** (#2353) : les espèces dites *prioritaires*
/// du Plan National d'Actions Chiroptères 2016-2025, telles que la migration V36 les a marquées.
///
/// Interface étroite, à l'image des autres ponts du produit ([fr.univ_amu.iut.passage.model.NuitsOpportunistes],
/// `CoordonneesPoint`) : les écrans qui repèrent, filtrent et comptent ces espèces — `audio`, `analyse` —
/// prennent **l'ensemble des codes**, et rien de plus. Aucun d'eux n'a de raison de toucher au
/// référentiel, qui vient du plan national et non de l'application.
///
/// Lecture **groupée et rare** : les consommateurs en gardent un instantané le temps d'un chargement,
/// plutôt qu'une requête par ligne affichée.
@FunctionalInterface
public interface EspecesPrioritaires {

    /// Codes des taxons prioritaires présents dans le référentiel embarqué. Ensemble **jamais vide** en
    /// pratique (17 codes après V36), mais rien n'en dépend : une base dont la migration n'aurait rien
    /// marqué se lit comme une base sans espèce à enjeu, pas comme une anomalie.
    Set<String> codes();
}

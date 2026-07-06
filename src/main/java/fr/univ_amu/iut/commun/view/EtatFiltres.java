package fr.univ_amu.iut.commun.view;

import java.util.List;

/// État mémorisable d'une **barre de filtres** : la recherche texte courante et les puces actives (chacune
/// décrite par un [EtatCritere]), dans l'ordre d'ajout. Photographié par [GestionnaireFiltres#capturer()]
/// et restitué par [GestionnaireFiltres#restaurer(EtatFiltres)] (mémorisation de session, #484).
///
/// Deux `EtatFiltres` sont égaux si texte et puces coïncident (record : égalité de valeur), ce qui rend la
/// mémorisation testable simplement. Type-valeur **indépendant du type de ligne filtrée** (socle #537).
///
/// @param texte contenu de la recherche texte permanente (jamais `null`, vide si aucune)
/// @param criteres puces actives, dans l'ordre d'ajout
public record EtatFiltres(String texte, List<EtatCritere> criteres) {}
